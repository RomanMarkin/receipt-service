ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.7"

lazy val root = (project in file("."))
  .settings(
    name := "receipt-service",
    assembly / assemblyJarName := "app.jar",
    assembly / assemblyMergeStrategy := { // Handle Merge Conflicts (Critical for ZIO + Logback)
      case PathList("META-INF", xs @ _*) =>
        xs map {_.toLowerCase} match {
          case "services" :: xs => MergeStrategy.filterDistinctLines
          case "manifest.mf" :: Nil | "index.list" :: Nil | "dependencies" :: Nil =>
            MergeStrategy.discard
          case ps @ x :: xs if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
            MergeStrategy.discard
          case "plexus" :: xs => MergeStrategy.discard
          case "spring.tooling" :: xs => MergeStrategy.discard
          case "io.netty.versions.properties" :: Nil => MergeStrategy.discard
          case _ => MergeStrategy.discard
        }
      case "reference.conf" => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
  )

libraryDependencies ++= Seq(
  "dev.zio"       %% "zio"                 % "2.1.24",
  "dev.zio"       %% "zio-http"            % "3.8.0",
  "dev.zio"       %% "zio-json"            % "0.7.45",
  "dev.zio"       %% "zio-logging"         % "2.5.3",
  "dev.zio"       %% "zio-logging-slf4j"   % "2.5.3",
  "dev.zio"       %% "zio-config"          % "4.0.6",
  "dev.zio"       %% "zio-config-typesafe" % "4.0.6",
  "dev.zio"       %% "zio-config-magnolia" % "4.0.6",     // automatic config derivation
  "org.mongodb.scala" %% "mongo-scala-driver" % "4.11.0" cross CrossVersion.for3Use2_13,
  "org.scala-lang.modules" %% "scala-xml"  % "2.4.0",
  "ch.qos.logback" % "logback-classic"     % "1.5.25",
  "com.github.f4b6a3" % "uuid-creator"     % "6.1.1",

  // Test Dependencies
  "dev.zio"       %% "zio-test"                       % "2.1.24" % Test,
  "dev.zio"       %% "zio-test-sbt"                   % "2.1.24" % Test,
  "dev.zio"       %% "zio-test-magnolia"              % "2.1.24" % Test,
  "dev.zio"       %% "zio-http-testkit"               % "3.8.0" % Test,
  "com.dimafeng"  %% "testcontainers-scala-mongodb"   % "0.44.1" % Test,
  "org.testcontainers" % "mongodb"                    % "1.21.4" % Test
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

// 'true' = Forked JVM exits, signaling Ryuk to terminate itself and cleanup resources.
// 'false' = JVM stays alive (Ryuk hangs/waits), but required for IntelliJ Breakpoints.
Test / fork := true