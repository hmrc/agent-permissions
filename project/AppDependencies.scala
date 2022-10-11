import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % "7.7.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"         % "0.73.0",
    "uk.gov.hmrc"             %% "agent-mtd-identifiers"      % "0.47.0-play-28",
    "uk.gov.hmrc"             %% "agent-kenshoo-monitoring"   % "4.8.0-play-28",
    "uk.gov.hmrc"             %% "crypto"                     % "7.2.0",
    "uk.gov.hmrc"             %% "crypto-json-play-28"        % "7.2.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % "7.7.0"             % "test, it",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % "0.73.0"            % "test, it",
    "org.scalamock"           %% "scalamock"                  % "5.2.0"             % "test, it"
  )
}
