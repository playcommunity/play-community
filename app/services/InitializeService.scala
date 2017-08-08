package services

import java.io.{File, FileInputStream}
import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.{NamedDatabase, ReactiveMongoApi}
import reactivemongo.akkastream.State
import reactivemongo.api.QueryOpts
import reactivemongo.bson.{BSONDocument, BSONTimestamp}
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{Await, ExecutionContext, Future}
import akka.stream.scaladsl.Source
import pl.allegro.tech.embeddedelasticsearch.{EmbeddedElastic, IndexSettings, PopularProperties}
import reactivemongo.akkastream.{State, cursorProducer}
import java.lang.ClassLoader._
import java.net.URL
import java.nio.file.{Files, Paths}
import java.time.{Clock, LocalDateTime, OffsetDateTime}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import akka.actor.ActorSystem
import controllers.admin.routes
import models.{App, Article, IndexedDocument, SiteSetting}
import play.api.{Application, Configuration, Environment, Logger}
import models.JsonFormats.articleFormat
import models.JsonFormats.ipLocationFormat
import models.JsonFormats.siteSettingFormat
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.WSClient

import scala.concurrent.duration._

/**
  * 执行系统初始化任务
  */
@Singleton
class InitializeService @Inject()(app: Application, actorSystem: ActorSystem, env: Environment, config: Configuration, ws: WSClient, val reactiveMongoApi: ReactiveMongoApi, elasticService: ElasticService, appLifecycle: ApplicationLifecycle, ipHelper: IPHelper)(implicit ec: ExecutionContext, mat: Materializer) {
  def oplogColFuture = reactiveMongoApi.connection.database("local").map(_.collection[JSONCollection]("oplog.rs"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def settingColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-setting"))

  val useExternalES = config.getOptional[Boolean]("es.useExternalES").getOrElse(false)
  val externalESServer = config.getOptional[String]("es.externalESServer").getOrElse("127.0.0.1:9200")

  // 载入网站设置
  for {
    settingCol <- settingColFuture
    opt <- settingCol.find(Json.obj("_id" -> "siteSetting")).one[SiteSetting]
  } yield {
    opt.foreach{ siteSetting =>
      App.siteSetting = siteSetting
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
    */
  var tailCount = new AtomicLong(0L)
  for{
    db <- reactiveMongoApi.database.map(_.name)
    lastHeartTime <- settingColFuture.flatMap(_.find(Json.obj("_id" -> "oplog-heart-time")).one[JsObject]).map(_.map(obj => obj("value").as[Long]).getOrElse(System.currentTimeMillis()))
    oplogCol <- oplogColFuture
  } {
    //val source: Source[BSONDocument, Future[State]] = oplogCol.find(Json.obj("ts" -> Json.obj("$gte" -> BSONTimestamp(lastHeartTime/1000, 1)))).options(QueryOpts().tailable.awaitData.noCursorTimeout).cursor[BSONDocument]().documentSource()
    val source: Source[BSONDocument, Future[State]] = oplogCol.find(Json.obj("ns" -> Json.obj("$in" -> Set(s"${db}.common-doc", s"${db}.common-article", s"${db}.common-qa")), "ts" -> Json.obj("$gte" -> BSONTimestamp(lastHeartTime/1000, 1)))).options(QueryOpts().tailable.awaitData.noCursorTimeout).cursor[BSONDocument]().documentSource()
    //val source: Source[BSONDocument, Future[State]] = oplogCol.find(Json.obj()).options(QueryOpts().tailable.awaitData.noCursorTimeout).cursor[BSONDocument]().documentSource()
    Logger.info("start tailing oplog ...")
    source.runForeach{ doc =>
      try {
        tailCount.addAndGet(1L)
        //println(tailCount.get() + " - oplog: " + BSONDocument.pretty(doc))
        val jsObj = doc.as[JsObject]
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

        if (tailCount.get() % 100 == 0) {
          settingColFuture.map(_.update(Json.obj("_id" -> "oplog-heart-time"), Json.obj("$set" -> Json.obj("value" -> System.currentTimeMillis()))))
          Logger.info("record heart beat time for tailing oplog.")
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
        ipHelper.getLocation(js("ip").as[String]).foreach{ locationOpt =>
          locationOpt.foreach{ location =>
            userCol.update(Json.obj("_id" -> js("_id").as[String]), Json.obj("$set" -> Json.obj("ipLocation" -> location)))
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
