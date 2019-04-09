name := "db_streaming"

val akkaVersion = "2.5.21"
val akkaHttpVersion = "10.1.8"

libraryDependencies += "com.lightbend.akka" %% "akka-stream-alpakka-slick" % "1.0-RC1"

libraryDependencies ++= Seq(
  "com.typesafe.akka"        %% "akka-stream"              % akkaVersion,
  "com.typesafe.akka"        %% "akka-stream-testkit"      % akkaVersion % Test,
  "com.typesafe.akka"        %% "akka-stream-typed"        % akkaVersion,
  "com.typesafe.akka" %% "akka-http" %  akkaHttpVersion,

  // to be used slightly in followers example
  "com.typesafe.akka"        %% "akka-actor-typed"         % akkaVersion,
  "com.h2database"  %  "h2"                 % "1.+",
  "com.typesafe.akka"        %% "akka-actor-testkit-typed" % akkaVersion % Test,

  "org.scalacheck"           %% "scalacheck"               % "1.13.5"    % Test,
  "junit"                    % "junit"                     % "4.10"      % Test,
  "ch.qos.logback"  %  "logback-classic"    % "1.+",
)

val circeVersion = "0.10.0"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies += "io.prometheus" % "parent" % "0.6.0"
libraryDependencies += "io.prometheus" % "simpleclient_pushgateway" % "0.6.0"
libraryDependencies += "org.postgresql" % "postgresql" % "42.2.5"

version := "0.1"

scalaVersion := "2.12.8"