name := """play-java"""
version := "1.0-SNAPSHOT"
lazy val root = (project in file(".")).enablePlugins(PlayJava)
scalaVersion := "2.11.8"
libraryDependencies ++= Seq(
  guice,
  ws,
  "com.mortennobel" % "java-image-scaling" % "0.8.5",
  "com.github.depsypher" % "pngtastic" % "1.2",
  "org.webjars" % "jquery" % "2.2.2"
)

