organization in ThisBuild := "me.lessis"

version in ThisBuild := "0.1.0"

name := "unisockets"

libraryDependencies ++= Seq(
  "com.github.jnr" % "jnr-unixsocket" % "0.3",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test")

lazy val unisockets = project.in(file("."))

lazy val `unisockets-netty` = project.dependsOn(unisockets)
