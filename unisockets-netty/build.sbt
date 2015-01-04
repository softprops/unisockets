description := "Provides netty bindings for an nio unix domain socket factory"

libraryDependencies ++= Seq(
  "io.netty" % "netty" % "3.9.6.Final",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2" % "test",
  "org.slf4j" % "slf4j-log4j12" % "1.6.2" % "test")

bintraySettings

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("domain-sockets", "unix", "sockets", "netty", "nio")
