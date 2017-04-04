/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package engine

import cats.data.WriterT
import connector.InternalUserIdentifier
import model.Locations.PersonalTaxAccount
import model._
import play.api.Play.current
import play.api.mvc.{AnyContent, Request}
import play.api.{Configuration, Play}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

trait Throttling {

  def random: Random

  def configuration: Configuration

  val throttlingEnabled = Play.configuration.getBoolean("throttling.enabled").getOrElse(false)

  val hundredPercent = 100

  def throttle(currentResult: EngineResult, ruleContext: RuleContext): EngineResult = {

    import cats.instances.all._
    import cats.syntax.flatMap._
    import cats.syntax.functor._

    def configurationForLocation(location: Location, request: Request[AnyContent]): Configuration = {

      def getLocationSuffix(location: Location, request: Request[AnyContent]): String = {
        location match {
          case PersonalTaxAccount =>
            if (request.session.data.contains("token")) "-gg"
            else "-verify"
          case _ => ""
        }
      }

      val suffix = getLocationSuffix(location, request)
      configuration.getConfig(s"throttling.locations.${location.name}$suffix").getOrElse(Configuration.empty)
    }

    def throttlePercentage: (Configuration) => Int = configurationForLocation => {
      configurationForLocation.getString("percentageBeToThrottled").map(_.toInt).getOrElse(0)
    }

    def findFallbackFor(location: Location): (Configuration) => Location = configurationForLocation => {
      val fallback = for {
        fallbackName <- configurationForLocation.getString("fallback")
        fallback <- Locations.find(fallbackName)
      } yield fallback
      fallback.getOrElse(location)
    }

    def throttle(auditInfo: AuditInfo, location: Location, userId: InternalUserIdentifier): (Configuration) => (AuditInfo, Location) =
      for {
        percentage <- throttlePercentage
        throttleDestination <- findFallbackFor(location)
        shouldThrottle = Throttler.shouldThrottle(userId, hundredPercent)
        throttlingInfo = ThrottlingInfo(percentage = Some(percentage), location != throttleDestination, location, throttlingEnabled)
      } yield {
        if (shouldThrottle) {
          (auditInfo.copy(throttlingInfo = Some(throttlingInfo)), throttleDestination)
        } else {
          (auditInfo, location)
        }
      }

    currentResult flatMap { location =>

      val result: Future[(AuditInfo, Location)] = for {
        userIdentifier <- ruleContext.internalUserIdentifier
        auditInfo <- currentResult.written
      } yield userIdentifier match {
        case None => (auditInfo, location)
        case Some(userId) if throttlingEnabled =>
          val locationConfiguration = configurationForLocation(location, ruleContext.request_)
          throttle(auditInfo, location, userId)(locationConfiguration)
        case Some(_) =>
          val throttlingInfo = ThrottlingInfo(percentage = None, throttled = false, location, throttlingEnabled)
          (auditInfo.copy(throttlingInfo = Some(throttlingInfo)), location)
      }

      WriterT(result)
    }
  }
}

object Throttling extends Throttling {

  override lazy val random: Random = Random

  override val configuration: Configuration = Play.configuration
}
