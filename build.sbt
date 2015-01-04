organization in ThisBuild := "me.lessis"

version in ThisBuild := "0.1.0-SNAPSHOT"

crossScalaVersions in ThisBuild := Seq("2.10.4", "2.11.4")

scalaVersion in ThisBuild := crossScalaVersions.value.last

publishArtifact := false

publish := {}

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation) ++
  Seq("-Ywarn-unused-import", "-Ywarn-unused", "-Xlint", "-feature").filter(
    Function.const(scalaVersion.value.startsWith("2.11")))

lazy val unisockets = project.in(file(".")).aggregate(`unisockets-core`, `unisockets-netty`)

lazy val `unisockets-core` = project

lazy val `unisockets-netty` = project.dependsOn(`unisockets-core`)
