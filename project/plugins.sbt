resolvers += Resolver.url("HMRC-open-artefacts-ivy2", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("uk.gov.hmrc"          % "sbt-auto-build"        % "3.24.0")
addSbtPlugin("uk.gov.hmrc"          % "sbt-distributables"    % "2.6.0")
addSbtPlugin("org.playframework"    % "sbt-plugin"            % "3.0.10")
addSbtPlugin("org.scoverage"        % "sbt-scoverage"         % "2.4.4")
addSbtPlugin("org.scalameta"        % "sbt-scalafmt"          % "2.6.1")
addSbtPlugin("com.timushev.sbt"     % "sbt-updates"           % "0.6.4")

//fix for scoverage compile errors for scala 2.13.10
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
addDependencyTreePlugin