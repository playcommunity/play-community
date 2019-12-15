import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._


/**
 * 拆分配置、版本、依赖
 *
 * @author 梦境迷离
 * @version 1.0, 2019-06-16
 */
object Dependencies {

  object Versions {

    val scala = "2.12.4"
    val scalatestplus = "3.0.0"
    val mongo = "0.3.1"
    val hanlp = "portable-1.3.4"
    val roaringbitmap = "0.6.44"
    val elasticsearch = "2.2.0"
    val mailer = "6.0.0"
    val socket = "1.0.0-beta-2"
    val pdfbox = "2.0.7"
    val jsoup = "1.10.3"
    val scalameta = "3.7.3"
    val contrib = "3.7.3"
    val config = "1.3.4"
    val argon2 = "2.5"

  }

  object Compiles {

    lazy val config: ModuleID = "com.typesafe" % "config" % Versions.config

    //测试依赖少，直接放这了
    lazy val scalatestplus: ModuleID = "org.scalatestplus.play" %% "scalatestplus-play" % Versions.scalatestplus % Test

    lazy val play = Seq(
      "cn.playscala" % "play-mongo_2.12" % Versions.mongo,
      "com.lightbend.play" %% "play-socket-io" % Versions.socket,
      "com.typesafe.play" %% "play-mailer" % Versions.mailer,
      "com.typesafe.play" %% "play-mailer-guice" % Versions.mailer
    )

    lazy val hanlp: ModuleID = "com.hankcs" % "hanlp" % Versions.hanlp

    lazy val roaringBitmap: ModuleID = "org.roaringbitmap" % "RoaringBitmap" % Versions.roaringbitmap

    lazy val elasticsearch: ModuleID = "pl.allegro.tech" % "embedded-elasticsearch" % Versions.elasticsearch

    lazy val pdf: ModuleID = "org.apache.pdfbox" % "pdfbox" % Versions.pdfbox

    lazy val jsoup: ModuleID = "org.jsoup" % "jsoup" % Versions.jsoup

    lazy val scalameta = Seq(
      "org.scalameta" %% "scalameta" % Versions.scalameta,
      "org.scalameta" %% "contrib" % Versions.contrib
    )

    lazy val argon2 = "de.mkammerer" % "argon2-jvm" % Versions.argon2
  }

  import Compiles._

  //项目依赖
  lazy val depend: Setting[Seq[ModuleID]] = libraryDependencies ++= Seq(
    config,
    scalatestplus,
    hanlp,
    roaringBitmap,
    elasticsearch,
    pdf,
    argon2,
    jsoup,
    guice,
    ws,
    ehcache
  ) ++ scalameta ++ play
}
