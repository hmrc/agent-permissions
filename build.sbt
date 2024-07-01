import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

val appName = "agent-permissions"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    PlayKeys.playDefaultPort         := 9447,
    routesImport                     ++= Seq(
      "uk.gov.hmrc.agentpermissions.binders.Binders._",
      "uk.gov.hmrc.agentmtdidentifiers.model.Arn",
      "uk.gov.hmrc.agentpermissions.models.GroupId"
    ),
    majorVersion                     := 0,
    scalaVersion                     := "2.13.10",
    Compile / scalafmtOnCompile      := true,
    Test / scalafmtOnCompile         := true,
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test,
    //fix for scoverage compile errors for scala 2.13.10
    libraryDependencySchemes ++= Seq("org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always),
    scalacOptions ++= Seq(
      "-Werror",
      "-Wdead-code",
      "-feature",
      "-language:implicitConversions",
      "-Xlint",
      "-Wconf:src=target/.*:s", // silence warnings from compiled files
      "-Wconf:src=*html:w", // silence html warnings as they are wrong
      "-Wconf:cat=deprecation:s",
      "-Wconf:cat=unused-privates:s",
      "-Wconf:msg=match may not be exhaustive:is", // summarize warnings about non-exhaustive pattern matching
      "-Wconf:src=Routes/.*:s" // silence warnings from routes files
    ),
  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
