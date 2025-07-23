import sbt.*

object AppDependencies {

  private val mongoVer: String = "2.6.0"
  private val bootstrapVer: String = "9.16.0"
  private val bootstrapBackend = "bootstrap-backend-play-30"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"          %% bootstrapBackend             % bootstrapVer,
    "uk.gov.hmrc.mongo"    %% "hmrc-mongo-play-30"         % mongoVer,
    "uk.gov.hmrc"          %% "agent-mtd-identifiers"      % "2.2.0",
    "uk.gov.hmrc"          %% "crypto-json-play-30"        % "8.2.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVer  % Test,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % mongoVer      % Test,
    "org.scalamock"           %% "scalamock"                  % "7.4.0"       % Test
  )
}
