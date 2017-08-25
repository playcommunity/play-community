package services

import java.io.{File, FileInputStream}
import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.{NamedDatabase, ReactiveMongoApi}
import reactivemongo.api.QueryOpts
import reactivemongo.bson.{BSONDocument, BSONTimestamp}
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{Await, ExecutionContext, Future}
import akka.stream.scaladsl.Source
import pl.allegro.tech.embeddedelasticsearch.{EmbeddedElastic, IndexSettings, PopularProperties}
import java.lang.ClassLoader._
import java.net.URL
import java.nio.file.{Files, Paths}
import java.time.{Clock, LocalDateTime, OffsetDateTime}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import akka.actor.ActorSystem
import controllers.admin.routes
import models._
import play.api.{Application, Configuration, Environment, Logger}
import models.JsonFormats._
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.WSClient
import utils.{DateTimeUtil, HashUtil, VersionComparator}

import scala.concurrent.duration._

/**
  * 执行系统初始化任务
  */
@Singleton
class InitializeService @Inject()(app: Application, actorSystem: ActorSystem, env: Environment, config: Configuration, ws: WSClient, reactiveMongoApi: ReactiveMongoApi, elasticService: ElasticService, appLifecycle: ApplicationLifecycle, ipHelper: IPHelper, counter: CounterService)(implicit ec: ExecutionContext, mat: Materializer) {
  def oplogColFuture = reactiveMongoApi.connection.database("local").map(_.collection[JSONCollection]("oplog.rs"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def settingColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-setting"))

  val useExternalES = config.getOptional[Boolean]("es.useExternalES").getOrElse(false)
  val externalESServer = config.getOptional[String]("es.externalESServer").getOrElse("127.0.0.1:9200")

  // 系统初始化
  for {
    settingCol <- settingColFuture
    versionOpt <- settingCol.find(Json.obj("_id" -> "version")).one[JsObject]
    siteSettingOpt <- settingCol.find(Json.obj("_id" -> "siteSetting")).one[SiteSetting]
  } yield {
    versionOpt match {
      case Some(js) =>
        val ver = js("value").as[String]
        if (VersionComparator.compareVersion(ver, App.version) > 0) {
          Logger.warn("You are using a lower version than before.")
        }

      case None =>
        settingCol.update(Json.obj("_id" -> "version"), Json.obj("$set" -> Json.obj("value" -> App.version)), upsert = true)
    }
    siteSettingOpt.foreach{ siteSetting =>
      App.siteSetting = siteSetting
    }
  }

  // 初始化管理员账户
  userColFuture.flatMap(_.count(Some(Json.obj("role" -> Role.ADMIN)))).map{ count =>
    if (count <= 0) {
      counter.getNextSequence("user-sequence").map{ uid =>
        userColFuture.map(_.insert(User(uid.toString, Role.ADMIN, "admin@playscala.cn", HashUtil.sha256("123456"), UserSetting("管理员", "", "", "/assets/images/head.png", ""), UserStat(0, 0, 0, 0, 0, 0, 0, 0, 0, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now()), 0, true, "register", "127.0.0.1", None, None)))
      }
    }
  }

  if (!useExternalES) {
    Logger.info("Starting Embedded ElasticSearch ...")
    val es = EmbeddedElastic.builder()
      .withDownloadUrl(new URL(s"file:///${env.rootPath}${File.separator}embed${File.separator}elasticsearch-5.5.0.zip"))
      //.withElasticVersion("5.5.0")
      .withSetting(PopularProperties.TRANSPORT_TCP_PORT, 9350)
      .withSetting(PopularProperties.CLUSTER_NAME, "cluster-0")
      .withInstallationDirectory(new File(s"${env.rootPath}${File.separator}embed"))
      .withCleanInstallationDirectoryOnStop(false)
      .withStartTimeout(15, TimeUnit.MINUTES)
      .build().start()

    Logger.info("Embedded ElasticSearch started!")
  } else {
    Logger.info("Use external ElasticSearch " + externalESServer)
  }

  // 检查ES索引是否存在
  val esIndexExists = Await.result(elasticService.existsIndex, 60 seconds)
  if (!esIndexExists) {
    Logger.info("Creating ElasticSearch Index ...")
    val success = Await.result(elasticService.createIndex, 60 seconds)
    if (!success) {
      Logger.error("Create ElasticSearch Index Failed.")
      Await.result(appLifecycle.stop(), 24 hours)
      System.exit(0)
    }
  }

  /**
    * Tail oplog.
    *
    * 保存的check point时间使用ts原始格式。
    */
  var tailCount = new AtomicLong(0L)
  for{
    oplogCol <- oplogColFuture
    db <- reactiveMongoApi.database.map(_.name)
    lastTS <- settingColFuture.flatMap(_.find(Json.obj("_id" -> "oplog-last-ts")).one[JsObject]).map(_.map(obj => obj("value").as[BSONDocument]).getOrElse(BSONTimestamp(System.currentTimeMillis()/1000, 1)))
  } {
    println("lastTS: " + lastTS)
    val tailingCursor =
      oplogCol
        .find(Json.obj("ns" -> Json.obj("$in" -> Set(s"${db}.common-doc", s"${db}.common-article", s"${db}.common-qa")), "ts" -> Json.obj("$gte" -> lastTS)))
        .options(QueryOpts().tailable.oplogReplay.awaitData.noCursorTimeout)
        .cursor[BSONDocument]()

    Logger.info("start tailing oplog ...")
    tailingCursor.fold(()){ (_, doc) =>
      try {
        tailCount.addAndGet(1L)
        val jsObj = doc.as[JsObject]
        println(tailCount.get() + " - oplog: " + jsObj("ts").toString())
        val ns = jsObj("ns").as[String]
        val resType = ns.split("-").last
        jsObj("op").as[String] match {
          case "i" =>
            val r = jsObj("o").as[JsObject]
            elasticService.insert(IndexedDocument(r("_id").as[String], resType, r("title").as[String], r("content").as[String], r("author")("name").as[String], r("author")("_id").as[String], r("author")("headImg").as[String], OffsetDateTime.parse(r("timeStat")("createTime").as[String]).toEpochSecond * 1000, r("viewStat")("count").as[Int], r("replyStat")("count").as[Int], r("voteStat")("count").as[Int], None))
            println("insert " + r("title").as[String])
          case "u" =>
            val _id = jsObj("o2")("_id").as[String]
            val modifier: JsObject = jsObj("o")("$set").as[JsObject]
            elasticService.update(_id, modifier)
            println("update " + _id)
          case "d" =>
            val _id = jsObj("o")("_id").as[String]
            elasticService.remove(_id)
            println("remove " + _id)
        }

        if (tailCount.get() % 10 == 0) {
          settingColFuture.map(_.update(Json.obj("_id" -> "oplog-last-ts"), Json.obj("$set" -> Json.obj("value" -> jsObj("ts"))), upsert = true))
          Logger.info("record last ts time for tailing oplog.")
        }
      } catch {
        case t: Throwable =>
          Logger.error("Tail oplog Error: " + t.getMessage, t)
      }
    }
  }

  /**
    * 更新IP地理位置
    */
  actorSystem.scheduler.schedule(2 minutes, 2 minutes){
    for{
      userCol <- userColFuture
      jsOpt <- userCol.find(Json.obj("ipLocation" -> Json.obj("$exists" -> false)), Json.obj("ip" -> 1)).one[JsObject]
    } {
      jsOpt.foreach{ js =>
        val ip = js("ip").as[String]
        ipHelper.getLocation(ip).foreach{ locationOpt =>
          locationOpt.foreach{ location =>
            userCol.update(Json.obj("ip" -> ip), Json.obj("$set" -> Json.obj("ipLocation" -> location)), multi = true)
          }
        }
      }
    }
  }


  appLifecycle.addStopHook { () =>
    Logger.info(s"Stopping application at ${LocalDateTime.now()}")
    Future.successful(())
  }

}
