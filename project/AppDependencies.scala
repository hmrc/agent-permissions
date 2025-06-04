import sbt._

object AppDependencies {

  private val mongoVer: String = "1.9.0"
  private val bootstrapVer: String = "8.6.0"
  private val bootstrapBackend = "bootstrap-backend-play-30"

  val compile = Seq(
    "uk.gov.hmrc"          %% bootstrapBackend             % bootstrapVer,
    "uk.gov.hmrc.mongo"    %% "hmrc-mongo-play-30"         % mongoVer,
    "uk.gov.hmrc"         %% "agent-mtd-identifiers"      % "2.0.0",
    "uk.gov.hmrc"          %% "crypto-json-play-30"        % "8.0.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVer  % Test,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % mongoVer      % Test,
    "org.scalamock"           %% "scalamock"                  % "6.0.0"       % Test
  )
}
