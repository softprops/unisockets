licenses in ThisBuild := Seq(("MIT", url(s"https://github.com/softprops/zoey/blob/${version.value}/LICENSE")))

pomExtra in ThisBuild := (
  <scm>
    <url>git@github.com:softprops/{name.value}.git</url>
    <connection>scm:git:git@github.com:softprops/{name.value}.git</connection>
  </scm>
  <developers>
    <developer>
      <id>softprops</id>
      <name>Doug Tangren</name>
      <url>https://github.com/softprops</url>
    </developer>
    </developers>
    <url>https://github.com/softprops/{name.value}</url>)
