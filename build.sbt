name := """play-community"""

version := "1.0.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  cache,
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % Test,
  "org.reactivemongo" %% "play2-reactivemongo" % "0.12.4" excludeAll(ExclusionRule("com.typesafe.play", "play-iteratees_2.11")),
  "com.hankcs" % "hanlp" % "portable-1.3.4",
  "org.roaringbitmap" % "RoaringBitmap" % "0.6.44"
)
