package app

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import javax.inject.{Inject, Singleton}
import models._
import play.api._
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import services.{CommonService, ElasticService, IPHelper, LeaderService, WatchService}
import utils.{HanLPUtil, HashUtil, VersionComparator}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * 执行系统初始化任务
  */
@Singleton
class Initializer @Inject()(mongo: Mongo, application: Application, actorSystem: ActorSystem, env: Environment, config: Configuration, ws: WSClient, elasticService: ElasticService, appLifecycle: ApplicationLifecycle, ipHelper: IPHelper, commonService: CommonService, watchService: WatchService, leaderService: LeaderService)(implicit ec: ExecutionContext, mat: Materializer) {
  val settingCol = mongo.collection("common-setting")
  val esEnabled = config.getOptional[Boolean]("es.enabled").getOrElse(false)
  val homeUrl = config.getOptional[String]("homeUrl").getOrElse("https://www.playscala.cn")

  app.Global.esEnabled = esEnabled
  app.Global.homeUrl = homeUrl

  // 初始化领域服务
  DomainRegistry.setInjector(application.injector)

  // 系统初始化
  for {
    versionOpt <- settingCol.findById("version")
    siteSettingOpt <- settingCol.findById("siteSetting")
  } yield {
    versionOpt match {
      case Some(js) =>
        val ver = js("value").as[String]
        if (VersionComparator.compareVersion(ver, app.Global.version) > 0) {
          Logger.warn("You are using a lower version than before.")
        }

      case None =>
        settingCol.updateOne(Json.obj("_id" -> "version"), Json.obj("$set" -> Json.obj("value" -> app.Global.version)), upsert = true)
    }
    siteSettingOpt.map(_.as[SiteSetting]).foreach{ siteSetting =>
      app.Global.siteSetting = siteSetting
      if (env.mode == Mode.Dev) {
        app.Global.siteSetting = app.Global.siteSetting.copy(logo = "https://www.playscala.cn/assets/images/logo.png", name = "PlayScala社区")
      }
    }
  }

  // 初始化管理员账户
  mongo.count[User](Json.obj("role" -> Role.ADMIN)).map{ count =>
    if (count <= 0) {
      commonService.getNextSequence("user-sequence").map{ uid =>
        mongo.insertOne[User](User(uid.toString, Role.ADMIN, "admin@playscala.cn", HashUtil.sha256("123456"), UserSetting("管理员", HanLPUtil.convertToPinyin("管理员"), "", "", "/assets/images/head.png", ""), UserStat.DEFAULT, 0, true, "register", "127.0.0.1", None, Nil, None))
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

  //如果开启搜索功能，则监听common-resource变化
  if (esEnabled) {
    watchService.watchResources()
  }

  //监听系统配置变化
  watchService.watchSettings()

  //更新IP地理位置
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

  //扫描博客更新时间
  actorSystem.scheduler.schedule(60 minutes, 60 minutes){
    leaderService.crawlLeaders()
  }

  appLifecycle.addStopHook { () =>
    Logger.info(s"Stopping application at ${LocalDateTime.now()}")
    Future.successful(())
  }

}
