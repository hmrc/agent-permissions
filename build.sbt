import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

val appName = "agent-permissions"

val silencerVersion = "1.7.8"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    PlayKeys.playDefaultPort         := 9447,
    routesImport                     ++= Seq("uk.gov.hmrc.agentpermissions.binders.Binders._", "uk.gov.hmrc.agentmtdidentifiers.model.Arn", "uk.gov.hmrc.agentpermissions.models.GroupId"),
    majorVersion                     := 0,
    scalaVersion                     := "2.12.15",
    Compile / scalafmtOnCompile      := true,
    Test / scalafmtOnCompile         := true,
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test,
    // ***************
    // Use the silencer plugin to suppress warnings
    scalacOptions += "-P:silencer:pathFilters=routes",
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    )
    // ***************
  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
