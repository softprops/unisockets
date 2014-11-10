name := "unisockets-netty"

libraryDependencies ++= Seq(
  "io.netty" % "netty" % "3.9.2.Final",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2" % "test",
  "org.slf4j" % "slf4j-log4j12" % "1.6.2")
