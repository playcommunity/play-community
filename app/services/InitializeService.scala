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

import scala.concurrent.{ExecutionContext, Future}
import akka.stream.scaladsl.Source
import pl.allegro.tech.embeddedelasticsearch.{EmbeddedElastic, IndexSettings, PopularProperties}
import reactivemongo.akkastream.{State, cursorProducer}
import java.lang.ClassLoader._
import java.net.URL
import java.nio.file.{Files, Paths}
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import akka.actor.ActorSystem
import models.{Article, IndexedDocument}
import play.api.{Environment, Logger}
import models.JsonFormats.articleFormat
import models.JsonFormats.ipLocationFormat
import play.api.inject.ApplicationLifecycle

import scala.concurrent.duration._

@Singleton
class InitializeService @Inject()(clock: Clock, actorSystem: ActorSystem, env: Environment, val reactiveMongoApi: ReactiveMongoApi, elasticService: ElasticService, appLifecycle: ApplicationLifecycle, ipHelper: IPHelper)(implicit ec: ExecutionContext, mat: Materializer) {
  def oplogColFuture = reactiveMongoApi.connection.database("local").map(_.collection[JSONCollection]("oplog.rs"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def settingColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-setting"))

  println("Starting ElasticSearch ...")
  val elasticSearch = EmbeddedElastic.builder()
    .withDownloadUrl(new URL(s"file:///${env.rootPath}${File.separator}embed${File.separator}elasticsearch-5.5.0.zip"))
    //.withElasticVersion("5.5.0")
    .withSetting(PopularProperties.TRANSPORT_TCP_PORT, 9350)
    .withSetting(PopularProperties.CLUSTER_NAME, "cluster-0")
    //.withIndex("community", IndexSettings.builder().withType("document", new FileInputStream(s"${env.rootPath}${File.separator}conf${File.separator}document-mapping.json")).build())
    .withInstallationDirectory(new File(s"${env.rootPath}${File.separator}embed"))
    .withCleanInstallationDirectoryOnStop(false)
    .withStartTimeout(5, TimeUnit.MINUTES)
    .build()
    .start()

    println("ElasticSearch start on " + elasticSearch.getHttpPort)

  /**
    * Tail oplog.
    */
  var tailCount = new AtomicLong(0L)
  for{
    db <- reactiveMongoApi.database.map(_.name)
    lastHeartTime <- settingColFuture.flatMap(_.find(Json.obj("_id" -> "oplog-heart-time")).one[JsObject]).map(_.map(obj => obj("value").as[Long]).getOrElse(System.currentTimeMillis()))
    oplogCol <- oplogColFuture
  } {
    val source: Source[BSONDocument, Future[State]] = oplogCol.find(Json.obj("ns" -> Json.obj("$in" -> Set(s"${db}.common-article")), "ts" -> Json.obj("$gte" -> BSONTimestamp(lastHeartTime/1000, 1)))).options(QueryOpts().tailable.awaitData.noCursorTimeout).cursor[BSONDocument]().documentSource()
    //val source: Source[BSONDocument, Future[State]] = oplogCol.find(Json.obj()).options(QueryOpts().tailable.awaitData.noCursorTimeout).cursor[BSONDocument]().documentSource()
    println("tailing ...")
    source.runForeach{ doc =>
      tailCount.addAndGet(1L)
      println(tailCount.get() + " - oplog: " + BSONDocument.pretty(doc))
      val jsObj = doc.as[JsObject]
      jsObj("ns").as[String] match {
        case ns if ns.endsWith(".common-article") =>
          jsObj("op").as[String] match {
            case "i" =>
              val a = jsObj("o").as[Article]
              elasticService.insert("localhost", 9200, IndexedDocument(a._id, "article", a.title, a.content, a.author.name, a.author._id, a.author.headImg, a.timeStat.createTime.toEpochSecond * 1000, a.viewStat.count, a.replyStat.count, a.voteStat.count, None))
              println("insert " + a)
            case "u" =>
              val _id = jsObj("o2")("_id").as[String]
              val modifier: JsObject = jsObj("o")("$set").as[JsObject]
              elasticService.update("localhost", 9200, _id, modifier)
              println("update " + _id)
            case "d" =>
              val _id = jsObj("o")("_id").as[String]
              elasticService.remove("localhost", 9200, _id)
              println("remove " + _id)
          }
        case _ =>
      }
      if (tailCount.get() % 100 == 0) {
        settingColFuture.map(_.update(Json.obj("_id" -> "oplog-heart-time"), Json.obj("$set" -> Json.obj("value" -> System.currentTimeMillis()))))
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
    Logger.info(s"Stopping application at ${clock.instant}")
    Future.successful(())
  }

}
