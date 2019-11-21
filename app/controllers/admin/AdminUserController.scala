package controllers.admin

import akka.stream.Materializer
import controllers._
import infrastructure.repository.mongo.{MongoEventRepository, MongoMessageRepository, MongoResourceRepository, MongoUserRepository}
import javax.inject._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.mvc._
import security.PasswordEncoder
import utils.{AppUtil, HashUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}

/**
 * 用户管理 
 */
@Singleton
class AdminUserController @Inject()(cc: ControllerComponents, userAction: UserAction,
  passwordEncoder: PasswordEncoder, resourceRepo: MongoResourceRepository, userRepo: MongoUserRepository,
  messageRepo: MongoMessageRepository,
  mongoEventRepo: MongoEventRepository)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {

  private val PAGE_SIZE = 15

  def findUserList(page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = AppUtil.parsePage(page)
    userRepo.findList(obj(), obj("stat.createTime" -> -1), (cPage - 1) * PAGE_SIZE, PAGE_SIZE) map { list =>
      val json = list map { u =>
        obj(
          "key" -> u._id.toInt,
          "name" -> u.setting.name,
          "active" -> u.activeCode.isEmpty,
          "enabled" -> u.enabled,
          "city" -> u.ipLocation.map(_.city).getOrElse[String]("-"),
          "channel" -> u.channels.headOption.map(_.name).getOrElse[String](""),
          "createTime" -> u.stat.createTime.toEpochMilli,
          "status" -> 0
        )
      }
      Ok(Json.toJson(json))
    }
  }

}
