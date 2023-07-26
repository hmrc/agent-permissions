import sbt._

object AppDependencies {

  private val mongoVer: String = "1.3.0"
  private val bootstrapVer: String = "7.19.0"
  private val bootstrapBackend = "bootstrap-backend-play-28"

  val compile = Seq(
    "uk.gov.hmrc"          %% bootstrapBackend             % bootstrapVer,
    "uk.gov.hmrc.mongo"    %% "hmrc-mongo-play-28"         % mongoVer,
    "uk.gov.hmrc"          %% "agent-mtd-identifiers"      % "1.13.0",
    "uk.gov.hmrc"          %% "agent-kenshoo-monitoring"   % "5.5.0" exclude("uk.gov.hmrc", bootstrapBackend),
    "uk.gov.hmrc"          %% "crypto-json-play-28"        % "7.3.0"
  )

  val test = Seq(
    "uk.gov.hmrc"          %% "bootstrap-test-play-28"     % bootstrapVer   % "test, it",
    "uk.gov.hmrc.mongo"    %% "hmrc-mongo-test-play-28"    % mongoVer       % "test, it",
    "org.scalamock"        %% "scalamock"                  % "5.2.0"        % "test, it"
  )
}
