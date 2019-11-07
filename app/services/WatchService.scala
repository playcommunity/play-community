package services

import java.util

import javax.inject.{Inject, Singleton}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.{RestartSource, Source}
import akka.stream.{Materializer, ThrottleMode}
import cn.playscala.mongo.codecs.Macros._
import cn.playscala.mongo.{Mongo, MongoClient, MongoDatabase}
import com.mongodb.async.client.MongoClients
import com.mongodb.client.model.changestream.{ChangeStreamDocument, OperationType}
import com.typesafe.config.{ConfigFactory, ConfigList}
import models._
import org.bson.BsonDocument
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import play.api._
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import play.api.libs.json.Json._

import scala.collection.JavaConverters._
/**
  * 实时监听数据库变化。
  */
@Singleton
class WatchService @Inject()(val mongo: Mongo, actorSystem: ActorSystem, env: Environment, config: Configuration, ws: WSClient, appLifecycle: ApplicationLifecycle, categoryService: CategoryService, elasticService: ElasticService)(implicit ec: ExecutionContext, mat: Materializer) {

  def watchResources() = {
    val colName = "common-resource"
    restartSource(colName)
      .groupedWithin(10, 1000.millis)
      .throttle(elements = 1, per = 1.second, maximumBurst = 1, ThrottleMode.shaping)
      .runForeach { seq =>
        try {
          //Logger.info(seq.toString())

          val inserts = seq.filter(c => c.getOperationType == OperationType.INSERT).map(_.getFullDocument.as[Resource]).toList
          val updates = seq.filter(c => c.getOperationType == OperationType.UPDATE || c.getOperationType == OperationType.REPLACE).map(_.getFullDocument.as[Resource]).toList
          val deletes = seq.filter(c => c.getOperationType == OperationType.DELETE).map(_.getDocumentKey.getString("_id").getValue).toList

          inserts.foreach { r =>
            elasticService.insert(IndexedDocument(r._id, r.resType, r.title, r.content.getOrElse("-"), r.author.name, r.author._id, r.createTime.toEpochMilli, None, None, None))
          }

          updates.foreach { r =>
            val obj = Json.obj(
              "title" -> r.title,
              "content" -> r.content.getOrElse[String]("-")
            )
            elasticService.update(r._id, obj)
          }

          deletes.foreach { _id =>
            elasticService.remove(_id)
          }
        } catch { case t: Throwable =>
          Logger.error(s"Watch ${colName} error: ${t.getMessage}", t)
        }
      }
  }

  def watchSettings() = {
    val colName = "common-setting"
    restartSource(colName)
      .groupedWithin(10, 1000.millis)
      .throttle(elements = 1, per = 1.second, maximumBurst = 1, ThrottleMode.shaping)
      .runForeach { seq =>
        try {
          //Logger.info(seq.toString())
          val updates = seq.filter(c => c.getOperationType == OperationType.UPDATE || c.getOperationType == OperationType.REPLACE).map(_.getFullDocument.as[JsObject]).toList

          updates.foreach { obj =>
            val _id = (obj \ "_id").as[String]
            _id match {
              case "siteSetting" => app.Global.siteSetting = obj.as[SiteSetting]
              case "version" => app.Global.version = (obj \ "value").as[String]
              case _ =>
            }
          }

        } catch { case t: Throwable =>
          Logger.error(s"Watch ${colName} error: ${t.getMessage}", t)
        }
      }
  }

  /**
    * Use RestartSource to try recovering from error.
    */
  private def restartSource(colName: String): Source[ChangeStreamDocument[JsObject], _] = {
    RestartSource.withBackoff(
      minBackoff = 3.seconds,
      maxBackoff = 10.seconds,
      randomFactor = 0.2, // adds 20% "noise" to vary the intervals slightly
      maxRestarts = 1000000 // limits the amount of restarts
    ) { () ⇒
      Logger.warn(s"Creating source for watching ${colName}.")
      mongo.collection(colName).watch().fullDocument.toSource
    }
  }

  /**
    * Use RestartSource to try recovering from error.
    */
  private def restartSource(colName: String, pipeline: Seq[JsObject]): Source[ChangeStreamDocument[JsObject], _] = {
    RestartSource.withBackoff(
      minBackoff = 3.seconds,
      maxBackoff = 10.seconds,
      randomFactor = 0.2, // adds 20% "noise" to vary the intervals slightly
      maxRestarts = 1000000 // limits the amount of restarts
    ) { () ⇒
      Logger.warn(s"Creating source for watching ${colName}.")
      mongo.collection(colName).watch(pipeline).fullDocument.toSource
    }
  }

}