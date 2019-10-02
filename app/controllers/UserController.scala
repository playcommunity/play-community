package controllers

import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import javax.inject._
import models._
import play.api.data.Form
import play.api.data.Forms.{ tuple, _ }
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.mvc._
import security.PasswordEncoder
import services.{ CommonService, EventService }
import utils.{ HashUtil, RequestHelper }

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class UserController @Inject()(cc: ControllerComponents, mongo: Mongo, resourceController: GridFSController,
                               userAction: UserAction, eventService: EventService,
                               commonService: CommonService, passwordEncoder: PasswordEncoder)
                              (implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {

  def index() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      articles <- mongo.find[Resource](obj("resType" -> Resource.Article, "author._id" -> request.session("uid"))).sort(obj("timeStat.updateTime" -> -1)).limit(15).list
      articlesCount <- mongo.count[Resource](obj("resType" -> Resource.Article, "author._id" -> request.session("uid")))
      qas <- mongo.find[Resource](obj("resType" -> Resource.QA, "author._id" -> request.session("uid"))).sort(obj("timeStat.updateTime" -> -1)).limit(15).list
      qaCount <- mongo.count[Resource](obj("resType" -> Resource.QA, "author._id" -> request.session("uid")))
      collectRes <- mongo.find[StatCollect](obj("uid" -> request.session("uid"))).sort(obj("collectTime" -> -1)).limit(15).list
      collectResCount <- mongo.count[StatCollect](obj("uid" -> request.session("uid")))
    } yield {
      Ok(views.html.user.index(articles, articlesCount.toInt, qas, qaCount.toInt, collectRes, collectResCount.toInt))
    }
  }

  def home(uidOpt: Option[String]) = Action.async { implicit request: Request[AnyContent] =>
    (uidOpt orElse RequestHelper.getUidOpt) match {
      case Some(uid) =>
        for {
          createEvents <- mongo.find[Event](obj("actor._id" -> uid, "action" -> "create")).sort(obj("createTime" -> -1)).limit(30).list
          events <- mongo.find[Event](obj("actor._id" -> uid)).sort(obj("createTime" -> -1)).limit(30).list
          userOpt <- mongo.find[User](obj("_id" -> uid)).first
        } yield {
          Ok(views.html.user.home(uidOpt, userOpt, createEvents, events))
        }
      case None =>
        Future.successful(Ok(views.html.message("系统提示", "您查看的用户不存在！")))
    }
  }

  def message() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      messages <- mongo.find[Message](obj("uid" -> request.session("uid"))).sort(obj("createTime" -> -1)).limit(15).list
      count <- mongo.count[Message](obj("uid" -> request.session("uid")))
    } yield {
      Ok(views.html.user.message(messages, count.toInt))
    }
  }

  def messageCount() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      count <- mongo.count[Message](obj("uid" -> request.session("uid"), "read" -> false))
    } yield {
      Ok(Json.obj("status" -> 0, "count" -> count))
    }
  }

  def readMessage() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      wr <- mongo.updateMany[Message](obj("uid" -> request.session("uid"), "read" -> false), obj("$set" -> Json.obj("read" -> true)))
    } yield {
      Ok(Json.obj("status" -> 0))
    }
  }

  def removeMessage = checkLogin.async { implicit request: Request[AnyContent] =>
    Form(single("id" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors))),
      _id => {
        for {
          wr <- mongo.deleteMany[Message](obj("_id" -> _id, "uid" -> request.session("uid")))
        } yield {
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def clearMessage() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      wr <- mongo.deleteMany[Message](obj("uid" -> request.session("uid")))
    } yield {
      Ok(Json.obj("status" -> 0))
    }
  }

  def activate() = (checkLogin andThen userAction) { implicit request =>
    Ok(views.html.user.activate(request.user))
  }

  def setting(focus: String) = (checkLogin andThen userAction) { implicit request =>
    Ok(views.html.user.setting(request.user, focus))
  }

  def doSetting() = checkLogin.async { implicit request: Request[AnyContent] =>
    Form(tuple("name" -> nonEmptyText, "gender" -> optional(text), "city" -> text, "introduction" -> text)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors))),
      tuple => {
        val (name, gender, city, introduction) = tuple
        mongo.updateOne[User](
          obj("_id" -> request.session("uid")),
          obj(
            "$set" -> obj("setting.name" -> name, "setting.gender" -> gender.getOrElse[String](""), "setting.city" -> city, "setting.introduction" -> introduction)
          )).map { wr =>
          Redirect(routes.UserController.setting())
            .addingToSession("name" -> name)
        }
      }
    )
  }

  // 演示如何将请求转发至ResourceController.saveResource
  /*def doSetHeadImg() = Action.async { implicit request: Request[AnyContent] =>
    resourceController.saveResource("")(request).mapFuture(_.body.consumeData.map(_.utf8String)).run().map(s => (Json.parse(s) \ "rid").asOpt[String]).map{
      case Some(rid) => Ok(Json.obj("success" -> true, "url" -> s"/resource/${rid}"))
      case None      => Ok(Json.obj("success" -> false, "message" -> "Upload failed."))
    }
  }*/

  def doSetHeadImg = checkLogin.async { implicit request: Request[AnyContent] =>
    Form(single("avatar" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "您的输入有误！"))),
      url => {
        mongo.updateOne[User](
          obj("_id" -> request.session("uid")),
          obj(
            "$set" -> Json.obj("setting.headImg" -> url)
          )).map { wr =>
          Ok(Json.obj("status" -> 0))
            .addingToSession("headImg" -> url)
        }
      }
    )
  }

  def doSetPassword() = (checkLogin andThen userAction) { implicit request =>
    Form(tuple("password" -> optional(text), "password1" -> nonEmptyText, "password2" -> nonEmptyText).verifying("两次输入不一致！", t => t._2 == t._3)).bindFromRequest().fold(
      errForm => Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors.map(_.message).mkString("|"))),
      tuple => {
        val (password, password1, _) = tuple
        if (password.isEmpty && request.user.password == "" || HashUtil.sha256(password.get) == request.user.password ||
          request.user.argon2Hash.isDefined && request.user.salt.isDefined
            && request.user.argon2Hash == passwordEncoder.hash(password.get, request.user.salt.get.getBytes)
        ) {
          passwordEncoder.updateUserPassword(request.session("uid"), HashUtil.sha256(password1))
          Redirect(routes.Application.message("系统提示", "密码修改成功！"))
        } else {
          Redirect(routes.Application.message("系统提示", "您的输入有误！"))
        }
      }
    )
  }
}
