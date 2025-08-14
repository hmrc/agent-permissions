import sbt.*

object AppDependencies {

  private val mongoVer: String = "2.7.0"
  private val bootstrapVer: String = "9.19.0"
  private val bootstrapBackend = "bootstrap-backend-play-30"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% bootstrapBackend      % bootstrapVer,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"  % mongoVer,
    "uk.gov.hmrc"       %% "crypto-json-play-30" % "8.3.0",
    "uk.gov.hmrc"       %% "domain-play-30"      % "11.0.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVer % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % mongoVer     % Test,
    "org.scalamock"     %% "scalamock"               % "7.4.1"      % Test,
    "org.scalacheck"    %% "scalacheck"              % "1.18.1"     % Test
  )
}
