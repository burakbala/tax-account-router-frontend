package router

import com.github.tomakehurst.wiremock.client.WireMock._
import connector.AffinityGroupValue
import controllers.internal.{AccountType, AccountTypeResponse}
import support.page._
import support.stubs.{CommonStubs, SessionUser, StubbedFeatureSpec}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, PayeAccount, SaAccount}

class AccountTypeForCredIdFeature extends StubbedFeatureSpec with CommonStubs {

  val credId = "cred-id-for-the-user"
  val saUtr = "12345"
  val saAccounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))

  import scala.concurrent.duration._
  import scala.concurrent.{Await, Future}

  implicit val defaultTimeout = 5 seconds

  def await[A](future: Future[A])(implicit timeout: Duration) = Await.result(future, timeout)

  private val individualAccountType = AccountTypeResponse(AccountType.Individual)
  private val organisationAccountType = AccountTypeResponse(AccountType.Organisation)

  feature("Principal for credId") {

    scenario("a user with any business account should get Organisation as account type") {

      Given("a user with cred id exists")
      SessionUser(isRegisteredFor2SV = false, internalUserIdentifier = Some(credId)).stubLoggedOut()

      And("the user has business related enrolments")
      stubBusinessEnrolments()

      When("the destination for cred id is fetched")
      val response = await(Navigation.goToPath(s"/internal/$credId/account-type")(app).get())
      response.status shouldBe 200

      Then("the account type should be Organisation")
      response.json.as[AccountTypeResponse] shouldBe organisationAccountType

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo(s"/auth/gg/$credId")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }

    scenario("a user logged in through GG with self assessment enrolments and no previous returns should get Organisation as account type") {

      Given("a user logged in through Government Gateway")
      SessionUser(accounts = saAccounts, internalUserIdentifier = Some(credId)).stubLoggedOut()

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user has no previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      When("the destination for cred id is fetched")
      val response = await(Navigation.goToPath(s"/internal/$credId/account-type")(app).get())
      response.status shouldBe 200

      Then("the account type should be Organisation")
      response.json.as[AccountTypeResponse] shouldBe organisationAccountType

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo(s"/auth/gg/$credId")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a user logged in through GG and sa returning 500 should get Organisation as account type") {

      Given("a user logged in through Government Gateway")
      SessionUser(accounts = saAccounts, internalUserIdentifier = Some(credId)).stubLoggedOut()

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the sa is returning 500")
      stubSaReturnToReturn500(saUtr)

      createStubs(BtaHomeStubPage)

      When("the destination for cred id is fetched")
      val response = await(Navigation.goToPath(s"/internal/$credId/account-type")(app).get())
      response.status shouldBe 200

      Then("the account type should be Organisation")
      response.json.as[AccountTypeResponse] shouldBe organisationAccountType

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo(s"/auth/gg/$credId")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a user logged in through GG and Auth returning 500 on GET enrolments should get Organisation as account type") {

      Given("a user logged in through Government Gateway")
      SessionUser(accounts = saAccounts, internalUserIdentifier = Some(credId)).stubLoggedOut()

      And("gg is returning 500")
      stubEnrolmentsToReturn500()

      When("the destination for cred id is fetched")
      val response = await(Navigation.goToPath(s"/internal/$credId/account-type")(app).get())
      response.status shouldBe 200

      Then("the account type should be Organisation")
      response.json.as[AccountTypeResponse] shouldBe organisationAccountType

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo(s"/auth/gg/$credId")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }

    scenario("a user logged in through GG with self assessment enrolments and in a partnership should get Organisation as account type") {

      Given("a user logged in through Government Gateway")
      SessionUser(accounts = saAccounts, internalUserIdentifier = Some(credId)).stubLoggedOut()

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user is in a partnership")
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("partnership"))

      When("the destination for cred id is fetched")
      val response = await(Navigation.goToPath(s"/internal/$credId/account-type")(app).get())
      response.status shouldBe 200

      Then("the account type should be Organisation")
      response.json.as[AccountTypeResponse] shouldBe organisationAccountType

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo(s"/auth/gg/$credId")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a user logged in through GG with self assessment enrolments and self employed should get Organisation as account type") {
      Given("a user logged in through Government Gateway")
      SessionUser(accounts = saAccounts, internalUserIdentifier = Some(credId)).stubLoggedOut()

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user is self employed")
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("self_employment"))

      When("the destination for cred id is fetched")
      val response = await(Navigation.goToPath(s"/internal/$credId/account-type")(app).get())
      response.status shouldBe 200

      Then("the account type should be Organisation")
      response.json.as[AccountTypeResponse] shouldBe organisationAccountType

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo(s"/auth/gg/$credId")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with no NINO should get Organisation as account type") {

      Given("a user logged in through Government Gateway")
      SessionUser(accounts = saAccounts.copy(paye = None), internalUserIdentifier = Some(credId)).stubLoggedOut()

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user has previous returns and is not in a partnership and is not self employed and has no NINO")
      stubSaReturn(saUtr, previousReturns = true)

      When("the destination for cred id is fetched")
      val response = await(Navigation.goToPath(s"/internal/$credId/account-type")(app).get())
      response.status shouldBe 200

      Then("the account type should be Organisation")
      response.json.as[AccountTypeResponse] shouldBe organisationAccountType

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo(s"/auth/gg/$credId")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with NINO should get Individual as account type") {

      Given("a user logged in through Government Gateway")
      SessionUser(internalUserIdentifier = Some(credId), accounts = saAccounts.copy(paye = Some(PayeAccount("link", Nino("CS100700A"))))).stubLoggedOut()

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user has previous returns and is not in a partnership and is not self employed and has NINO")
      stubSaReturn(saUtr, previousReturns = true)

      When("the destination for cred id is fetched")
      val response = await(Navigation.goToPath(s"/internal/$credId/account-type")(app).get())
      response.status shouldBe 200

      Then("the account type should be Organisation")
      response.json.as[AccountTypeResponse] shouldBe individualAccountType

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo(s"/auth/gg/$credId")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a user logged in through GG and has no sa and no business enrolment with individual affinity group and inactive enrolments should get Organisation as account type") {

      Given("a user logged in through Government Gateway")
      SessionUser(internalUserIdentifier = Some(credId), affinityGroup = AffinityGroupValue.INDIVIDUAL).stubLoggedOut()

      And("the user has an inactive enrolment and individual affinity group")
      stubInactiveEnrolments()
      stubUserDetails(affinityGroup = Some(AffinityGroupValue.INDIVIDUAL))

      When("the destination for cred id is fetched")
      val response = await(Navigation.goToPath(s"/internal/$credId/account-type")(app).get())
      response.status shouldBe 200

      Then("the account type should be Organisation")
      response.json.as[AccountTypeResponse] shouldBe organisationAccountType

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo(s"/auth/gg/$credId")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }

    scenario("a user logged in through GG and has no sa and no business enrolment with individual affinity group and no inactive enrolments should get Individual as account type") {

      Given("a user logged in through Government Gateway")
      SessionUser(internalUserIdentifier = Some(credId), affinityGroup = AffinityGroupValue.INDIVIDUAL).stubLoggedOut()

      And("the user has no inactive enrolments and individual affinity group")
      stubNoEnrolments()
      stubUserDetails(affinityGroup = Some(AffinityGroupValue.INDIVIDUAL))

      When("the destination for cred id is fetched")
      val response = await(Navigation.goToPath(s"/internal/$credId/account-type")(app).get())
      response.status shouldBe 200

      Then("the account type should be Individual")
      response.json.as[AccountTypeResponse] shouldBe individualAccountType

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo(s"/auth/gg/$credId")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should be fetched from User Details")
      verify(getRequestedFor(urlEqualTo("/user-details-uri")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }

    scenario("a user logged in through GG and has no sa and no business enrolment and no inactive enrolments and affinity group not available should get Organisation as account type") {

      Given("a user logged in through Government Gateway")
      SessionUser(internalUserIdentifier = Some(credId), affinityGroup = AffinityGroupValue.INDIVIDUAL).stubLoggedOut()

      And("the user has no inactive enrolments and affinity group is not available")
      stubNoEnrolments()
      stubUserDetailsToReturn500()

      When("the destination for cred id is fetched")
      val response = await(Navigation.goToPath(s"/internal/$credId/account-type")(app).get())
      response.status shouldBe 200

      Then("the account type should be Organisation")
      response.json.as[AccountTypeResponse] shouldBe organisationAccountType

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo(s"/auth/gg/$credId")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should be fetched from User Details")
      verify(getRequestedFor(urlEqualTo("/user-details-uri")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }
  }
}