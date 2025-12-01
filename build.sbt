ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.7"

lazy val root = (project in file("."))
  .settings(
    name := "receipt-service"
  )

libraryDependencies ++= Seq(
  "dev.zio"       %% "zio"                 % "2.1.23",
  "dev.zio"       %% "zio-http"            % "3.7.0",
  "dev.zio"       %% "zio-json"            % "0.7.45",
  "dev.zio"       %% "zio-logging"         % "2.5.2",
  "dev.zio"       %% "zio-logging-slf4j"   % "2.5.2",
  "dev.zio"       %% "zio-config"          % "4.0.6",
  "dev.zio"       %% "zio-config-typesafe" % "4.0.6",
  "dev.zio"       %% "zio-config-magnolia" % "4.0.6",     // automatic config derivation
  "org.mongodb.scala" %% "mongo-scala-driver" % "4.11.0" cross CrossVersion.for3Use2_13,
  "ch.qos.logback" % "logback-classic"     % "1.5.21"
)
