name := """play-community"""

version := "1.0.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.2"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.0.0" % Test,
  "org.reactivemongo" % "play2-reactivemongo_2.12" % "0.12.5-play26",
  "com.hankcs" % "hanlp" % "portable-1.3.4",
  "org.roaringbitmap" % "RoaringBitmap" % "0.6.44"
)
