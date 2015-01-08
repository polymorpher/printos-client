name := "printos"

version := "1.0"

scalaVersion := "2.10.4"

assemblyJarName in assembly := "printos.jar"

mainClass in assembly := Some("potatos.client.rpi.printing.Client")

libraryDependencies ++= Seq(
  "com.google.code.gson" % "gson" % "2.2.4",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "com.ning" % "async-http-client" % "1.7.21",
  "joda-time" % "joda-time" % "2.4",
  "com.fatboyindustrial.gson-jodatime-serialisers" % "gson-jodatime-serialisers" % "1.1.0"
)