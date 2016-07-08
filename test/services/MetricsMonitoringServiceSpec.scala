/*
 * Copyright 2016 HM Revenue & Customs
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

package services

import com.codahale.metrics.{Meter, MetricRegistry}
import model.{AuditContext, Location, TAuditContext}
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.test.FakeRequest
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.mutable.{Map => mutableMap}

class MetricsMonitoringServiceSpec extends UnitSpec with MockitoSugar with Eventually {

  "MetricsMonitoringService" should {

    val scenarios = Table(
      ("scenario", "destinationNameBeforeThrottling", "destinationNameAfterThrottling"),
      ("destination not throttled", Some("some-destination"), "some-destination"),
      ("destination throttled", Some("some-destination"), "some-other-destination"),
      ("destination before throttling not present", None, "some-other-destination")
    )

    forAll(scenarios) { (scenario: String, destinationNameBeforeThrottling: Option[String], destinationNameAfterThrottling: String) =>

      s"send monitoring events - scenario: $scenario" in new Setup {

        val mockAuditContext = mock[TAuditContext]

        val mockThrottledLocation = mock[Location]
        when(mockThrottledLocation.name).thenReturn(destinationNameAfterThrottling)

        val routingReasons = mutableMap(
          "c1" -> "true",
          "c2" -> "true",
          "c3" -> "false",
          "c4" -> "false"
        )
        when(mockAuditContext.getReasons).thenReturn(routingReasons)

        val ruleApplied = "condition-applied"
        when(mockAuditContext.ruleApplied).thenReturn(ruleApplied)
        val mockRoutedToMeter = mock[Meter]

        if (destinationNameBeforeThrottling.isDefined && destinationNameBeforeThrottling.get != destinationNameAfterThrottling) {
          when(mockMetricRegistry.meter(eqTo(s"routed.to-$destinationNameAfterThrottling.because-$ruleApplied.throttled-from-${destinationNameBeforeThrottling.get}"))).thenReturn(mockRoutedToMeter)
        } else {
          when(mockMetricRegistry.meter(eqTo(s"routed.to-$destinationNameAfterThrottling.because-$ruleApplied.not-throttled"))).thenReturn(mockRoutedToMeter)
        }

        val c1Meter = mock[Meter]
        val c2Meter = mock[Meter]
        val c3Meter = mock[Meter]
        val c4Meter = mock[Meter]
        when(mockMetricRegistry.meter(eqTo("c1"))).thenReturn(c1Meter)
        when(mockMetricRegistry.meter(eqTo("c2"))).thenReturn(c2Meter)
        when(mockMetricRegistry.meter(eqTo("not-c3"))).thenReturn(c3Meter)
        when(mockMetricRegistry.meter(eqTo("not-c4"))).thenReturn(c4Meter)

        val entry = destinationNameBeforeThrottling.fold("" -> "") { destination => "destination-name-before-throttling" -> destination }
        val throttlingDetails = mutableMap() += entry
        when(mockAuditContext.getThrottlingDetails).thenReturn(throttlingDetails)

        // when
        metricsMonitoringService.sendMonitoringEvents(mockAuditContext, mockThrottledLocation)

        eventually {

          verify(mockRoutedToMeter).mark()

          verify(c1Meter).mark()
          verify(c2Meter).mark()
          verify(c3Meter).mark()
          verify(c4Meter).mark()

          verifyNoMoreInteractions(
            mockRoutedToMeter,
            c1Meter,
            c2Meter,
            c3Meter,
            c4Meter
          )
        }
      }
    }
  }

  it should {

    val scenarios = Table(
      ("scenario", "twoStepVerificationMandatory"),
      ("2sv registration is mandatory", true),
      ("2sv registration is optional", false)
    )

    forAll(scenarios) { (scenario: String, twoStepVerificationMandatory: Boolean) =>

      s"send passed-through-2SV metric when flag is true - $scenario" in new Setup {

        val auditContext = AuditContext()
        if (twoStepVerificationMandatory)
          auditContext.setSentToMandatory2SVRegister()
        else
          auditContext.setSentToOptional2SVRegister()

        val mockThrottledLocation = mock[Location]
        when(mockThrottledLocation.name).thenReturn(throttledLocation)

        // when
        metricsMonitoringService.sendMonitoringEvents(auditContext, mockThrottledLocation)

        eventually {
          verify(mockMeter2SV).mark()
          verify(mockMeter2SVMandatory, if (twoStepVerificationMandatory) times(1) else never()).mark()
          verify(mockMeter2SVOptional, if (!twoStepVerificationMandatory) times(1) else never()).mark()
          verifyNoMoreInteractions(mockMeter2SV, mockMeter2SVMandatory, mockMeter2SVOptional)
        }
      }
    }

    "don't send passed-through-2SV metric when flag is false" in new Setup {

      val auditContext = AuditContext()

      val mockThrottledLocation = mock[Location]
      when(mockThrottledLocation.name).thenReturn(throttledLocation)

      // when
      metricsMonitoringService.sendMonitoringEvents(auditContext, mockThrottledLocation)

      eventually {
        verifyZeroInteractions(mockMeter2SV, mockMeter2SVMandatory, mockMeter2SVOptional)
      }
    }
  }

  sealed trait Setup {
    implicit val fakeRequest = FakeRequest()
    implicit val hc = HeaderCarrier()
    implicit val authContext = mock[AuthContext]
    val mockMetricRegistry = mock[MetricRegistry]
    val metricsMonitoringService = new MetricsMonitoringService {
      override val metricsRegistry = mockMetricRegistry
    }

    val throttledLocation = "some-destination"
    val mockMeter2SV = mock[Meter]
    val mockMeter2SVMandatory = mock[Meter]
    val mockMeter2SVOptional = mock[Meter]
    when(mockMetricRegistry.meter(any[String])).thenReturn(mock[Meter])
    when(mockMetricRegistry.meter(eqTo(s"passed-through-2SV.to-$throttledLocation"))).thenReturn(mockMeter2SV)
    when(mockMetricRegistry.meter(eqTo(s"passed-through-2SV.mandatory.to-$throttledLocation"))).thenReturn(mockMeter2SVMandatory)
    when(mockMetricRegistry.meter(eqTo(s"passed-through-2SV.optional.to-$throttledLocation"))).thenReturn(mockMeter2SVOptional)
  }

}
