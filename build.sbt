import CodeCoverageSettings.scoverageSettings
import uk.gov.hmrc.DefaultBuildSettings

val appName = "agent-permissions"

ThisBuild / majorVersion := 1
ThisBuild / scalaVersion := "3.7.4"

val scalaCOptions = Seq(
  "-Werror",
  "-unchecked",
  "-deprecation",
  "-feature",
  "-language:implicitConversions",
  "-Wconf:src=target/.*:s", // silence warnings from compiled files
  "-Wconf:cat=deprecation:s",
  "-Wconf:msg=Flag.*repeatedly:s",
  "-Wconf:msg=unused privates:s"
)

lazy val root = (project in file("."))
  .settings(
    name := appName,
    organization := "uk.gov.hmrc",
    PlayKeys.playDefaultPort         := 9447,
    routesImport                     ++= Seq(
      "uk.gov.hmrc.agentpermissions.binders.Binders.{given, *}",
      "uk.gov.hmrc.agentpermissions.model.Arn",
      "uk.gov.hmrc.agentpermissions.models.GroupId"
    ),
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    resolvers ++= Seq(Resolver.typesafeRepo("releases")),
    scalacOptions ++= scalaCOptions,
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    commands ++= SbtCommands.commands
  )
  .settings(
    Test / parallelExecution := false,
    scoverageSettings
  )
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)


lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(root % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.test)
  .settings(
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true
  )
