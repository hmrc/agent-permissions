import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  private val mongoVer: String = "1.1.0"
  private val bootstrapVer: String = "7.13.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % bootstrapVer,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"         % mongoVer,
    "uk.gov.hmrc"             %% "agent-mtd-identifiers"      % "1.2.0",
    "uk.gov.hmrc"             %% "agent-kenshoo-monitoring"   % "5.3.0",
    "uk.gov.hmrc"             %% "crypto-json-play-28"        % "7.3.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % "7.13.0"            % "test, it",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % "0.74.0"            % "test, it",
    "org.scalamock"           %% "scalamock"                  % "5.2.0"             % "test, it"
  )
}
