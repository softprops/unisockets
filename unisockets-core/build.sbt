description := "Defines adapters for unix domain sockets for the jvm"

libraryDependencies ++= Seq(
  "com.github.jnr" % "jnr-unixsocket" % "0.3",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test")

bintraySettings

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("domain-sockets", "unix", "sockets", "nio")
