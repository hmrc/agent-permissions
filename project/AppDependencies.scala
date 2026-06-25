import sbt.*

object AppDependencies {

  private val mongoVer: String = "2.12.0"
  private val bootstrapVer: String = "10.7.0"
  private val playVer: String = "play-30"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-backend-$playVer"      % bootstrapVer,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVer"  % mongoVer,
    "uk.gov.hmrc"       %% s"crypto-json-$playVer" % "8.4.0",
    "uk.gov.hmrc"       %% s"domain-$playVer"      % "11.0.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-test-$playVer"  % bootstrapVer,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test-$playVer" % mongoVer,
    "org.scalamock"     %% "scalamock"               % "7.5.5",
    "org.scalacheck"    %% "scalacheck"              % "1.18.1"
  ).map(_ % Test)
}
