package services

import javax.inject.{Inject, Singleton}

import akka.stream.{Materializer, ThrottleMode}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalDateTime

import akka.actor.ActorSystem
import cn.playscala.mongo.Mongo
import com.mongodb.client.model.changestream.OperationType
import models._
import play.api._
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.WSClient
import utils.{HashUtil, VersionComparator}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * 执行系统初始化任务
  */
@Singleton
class InitializeService @Inject()(mongo: Mongo, application: Application, actorSystem: ActorSystem, env: Environment, config: Configuration, ws: WSClient, elasticService: ElasticService, appLifecycle: ApplicationLifecycle, ipHelper: IPHelper, commonService: CommonService)(implicit ec: ExecutionContext, mat: Materializer) {
  val settingCol = mongo.collection("common-setting")
  val esEnabled = config.getOptional[Boolean]("es.enabled").getOrElse(false)

  app.Global.esEnabled = esEnabled

  // 系统初始化
  for {
    versionOpt <- settingCol.findById("version")
    siteSettingOpt <- settingCol.findById("siteSetting")
  } yield {
    versionOpt match {
      case Some(js) =>
        val ver = js("value").as[String]
        if (VersionComparator.compareVersion(ver, App.version) > 0) {
          Logger.warn("You are using a lower version than before.")
        }

      case None =>
        settingCol.updateOne(Json.obj("_id" -> "version"), Json.obj("$set" -> Json.obj("value" -> App.version)), upsert = true)
    }
    siteSettingOpt.map(_.as[SiteSetting]).foreach{ siteSetting =>
      App.siteSetting = siteSetting
      if (env.mode == Mode.Dev) {
        App.siteSetting = App.siteSetting.copy(logo = "https://www.playscala.cn/assets/images/logo.png", name = "PlayScala社区")
      }
    }
  }

  // 初始化管理员账户
  mongo.count[User](Json.obj("role" -> Role.ADMIN)).map{ count =>
    if (count <= 0) {
      commonService.getNextSequence("user-sequence").map{ uid =>
        mongo.insertOne[User](User(uid.toString, Role.ADMIN, "admin@playscala.cn", HashUtil.sha256("123456"), UserSetting("管理员", "", "", "/assets/images/head.png", ""), UserStat.DEFAULT, 0, true, "register", "127.0.0.1", None, Nil, None))
      }
    }
  }

  // 检查ES索引是否存在
  if (esEnabled) {
    elasticService.existsIndex.map { esIndexExists =>
      if (!esIndexExists) {
        Logger.info("Creating ElasticSearch Index ...")
        elasticService.createIndex.map { success =>
          if (success) {
            app.Global.isElasticReady = true
            Logger.info("Created ElasticSearch Index.")
          } else {
            Logger.error("Create ElasticSearch Index Failed.")
          }
        }
      } else {
        app.Global.isElasticReady = true
      }
    }
  }

  /**
    * 同步更新至ES
    */
  if (esEnabled) {
    mongo
      .collection("common-resource")
      .watch()
      .fullDocument
      .toSource
      .groupedWithin(10, 1000.millis)
      .throttle(elements = 1, per = 1.second, maximumBurst = 1, ThrottleMode.shaping)
      .watchTermination() { (_, done) =>
        done.onComplete {
          case Success(_) => Logger.error("common-resource's change stream completed..")
          case Failure(error) => Logger.error(s"common-resource's change stream failed with error ${error.getMessage}")
        }
      }
      .runForeach { seq =>
        try {
          Logger.info(seq.toString())

          val inserts = seq.filter(c => c.getOperationType == OperationType.INSERT).map(_.getFullDocument.as[Resource]).toList
          val updates = seq.filter(c => c.getOperationType == OperationType.UPDATE || c.getOperationType == OperationType.REPLACE).map(_.getFullDocument.as[Resource]).toList
          val deletes = seq.filter(c => c.getOperationType == OperationType.DELETE).map(_.getDocumentKey.getString("_id").getValue).toList

          inserts.foreach { r =>
            elasticService.insert(IndexedDocument(r._id, r.resType, r.title, r.content, r.author.name, r.author._id, r.createTime.toEpochMilli, None, None, None))
          }

          updates.foreach { r =>
            val obj = Json.obj(
              "title" -> r.title,
              "content" -> r.content
            )
            elasticService.update(r._id, obj)
          }

          deletes.foreach { _id =>
            elasticService.remove(_id)
          }
        } catch {
          case t: Throwable =>
            Logger.error("watch common-resource error" + t.getMessage, t)
        }
      }
  }

  /**
    * 更新IP地理位置
    */
  actorSystem.scheduler.schedule(2 minutes, 2 minutes){
    for{
      jsOpt <- mongo.collection("common-user").find(Json.obj("ipLocation" -> Json.obj("$exists" -> false)), Json.obj("ip" -> 1)).first
    } {
      jsOpt.foreach{ js =>
        val ip = js("ip").as[String]
        ipHelper.getLocation(ip).foreach{ locationOpt =>
          locationOpt.foreach{ location =>
            mongo.updateMany[User](Json.obj("ip" -> ip), Json.obj("$set" -> Json.obj("ipLocation" -> location)))
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
