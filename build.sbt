ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.7"

lazy val root = (project in file("."))
  .settings(
    name := "receipt-service"
  )

libraryDependencies ++= Seq(
  "dev.zio"       %% "zio"                 % "2.1.23",
  "dev.zio"       %% "zio-http"            % "3.7.4",
  "dev.zio"       %% "zio-json"            % "0.7.45",
  "dev.zio"       %% "zio-logging"         % "2.5.2",
  "dev.zio"       %% "zio-logging-slf4j"   % "2.5.2",
  "dev.zio"       %% "zio-config"          % "4.0.6",
  "dev.zio"       %% "zio-config-typesafe" % "4.0.6",
  "dev.zio"       %% "zio-config-magnolia" % "4.0.6",     // automatic config derivation
  "org.mongodb.scala" %% "mongo-scala-driver" % "4.11.0" cross CrossVersion.for3Use2_13,
  "org.scala-lang.modules" %% "scala-xml"  % "2.4.0",
  "ch.qos.logback" % "logback-classic"     % "1.5.23",
  "com.github.f4b6a3" % "uuid-creator"     % "6.1.1",

  // Test Dependencies
  "dev.zio"       %% "zio-test"                       % "2.1.23" % Test,
  "dev.zio"       %% "zio-test-sbt"                   % "2.1.23" % Test,
  "dev.zio"       %% "zio-test-magnolia"              % "2.1.23" % Test,
  "com.dimafeng"  %% "testcontainers-scala-mongodb"   % "0.44.1" % Test,
  "org.testcontainers" % "mongodb"                    % "1.21.4" % Test
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

// 'true' = Forked JVM exits, signaling Ryuk to terminate itself and cleanup resources.
// 'false' = JVM stays alive (Ryuk hangs/waits), but required for IntelliJ Breakpoints.
Test / fork := true