import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % "6.3.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"         % "0.67.0",
    "uk.gov.hmrc"             %% "agent-mtd-identifiers"      % "0.43.0-play-28",
    "uk.gov.hmrc"             %% "agent-kenshoo-monitoring"   % "4.8.0-play-28"
  )

  val test = Seq(
    "org.scalatestplus"       %% "mockito-3-12"               % "3.2.10.0"          % "test, it",
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % "6.3.0"             % "test, it",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % "0.67.0"            % "test, it",
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.62.2"            % "test, it",
    "org.scalamock"           %% "scalamock"                  % "5.2.0"             % "test, it"
  )
}
