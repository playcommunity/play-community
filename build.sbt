name := """play-community"""

version := "1.2.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.4"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  guice, ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.0.0" % Test,
  "cn.playscala" % "play-mongo_2.12" % "0.1.0",
  "com.hankcs" % "hanlp" % "portable-1.3.4",
  "org.roaringbitmap" % "RoaringBitmap" % "0.6.44",
  "pl.allegro.tech" % "embedded-elasticsearch" % "2.2.0",
  "com.typesafe.play" %% "play-mailer" % "6.0.0",
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.0",
  "com.lightbend.play" %% "play-socket-io" % "1.0.0-beta-2",
  "org.apache.pdfbox" % "pdfbox" % "2.0.7",
  "org.jsoup" % "jsoup" % "1.10.3",
  "org.scalameta" %% "scalameta" % "3.7.3",
  "org.scalameta" %% "contrib" % "3.7.3"
)
