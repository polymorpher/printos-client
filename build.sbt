name := "printos"

version := "1.0"

scalaVersion := "2.11.6"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.google.code.gson" % "gson" % "2.2.4",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "com.ning" % "async-http-client" % "1.7.21",
  "joda-time" % "joda-time" % "2.4",
  "com.fatboyindustrial.gson-jodatime-serialisers" % "gson-jodatime-serialisers" % "1.1.0",
  "com.typesafe.akka" %% "akka-actor" % "2.3.8",
  "org.usb4java" % "usb4java" % "1.2.0"
)

assemblyJarName in assembly := "printos.jar"

mainClass in assembly := Some("printos.client.Client")

assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false, includeDependency = false)