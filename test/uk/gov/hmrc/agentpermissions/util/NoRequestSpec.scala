package uk.gov.hmrc.agentpermissions.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.typedmap.TypedMap

/** Scoverage insists that `NoRequest` needs an independent test, even though it has no discernible behaviour.
  */
class NoRequestSpec extends AnyWordSpecLike with Matchers:
  "NoRequest" should:
    "be an empty request" in:
      NoRequest.body shouldBe ""
      NoRequest.method shouldBe ""
      NoRequest.version shouldBe ""
      NoRequest.connection.remoteAddressString shouldBe ""
      NoRequest.connection.secure shouldBe false
      NoRequest.connection.clientCertificateChain should not be defined
      NoRequest.target.uriString shouldBe ""
      NoRequest.target.path shouldBe ""
      NoRequest.target.queryMap shouldBe empty
      NoRequest.attrs shouldBe TypedMap.empty
      NoRequest.headers.headers shouldBe empty
      NoRequest.target.uri.toASCIIString shouldBe ""
