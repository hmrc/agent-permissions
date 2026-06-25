import sbt.*

object AppDependencies {

  private val mongoVer: String = "2.12.0"
  private val bootstrapVer: String = "10.7.0"
  private val bootstrapBackend = "bootstrap-backend-play-30"
  private val playVer: String = "play-30"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% bootstrapBackend      % bootstrapVer,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVer"  % mongoVer,
    "uk.gov.hmrc"       %% s"crypto-json-$playVer" % "8.4.0",
    "uk.gov.hmrc"       %% s"domain-$playVer"      % "11.0.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-test-$playVer"  % bootstrapVer % Test,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test-$playVer" % mongoVer     % Test,
    "org.scalamock"     %% "scalamock"               % "7.5.5"      % Test,
    "org.scalacheck"    %% "scalacheck"              % "1.18.1"     % Test
  )
}
