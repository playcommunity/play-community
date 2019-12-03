package controllers

import akka.stream.Materializer
import domain.infrastructure.repository.mongo.{MongoEventRepository, MongoMessageRepository, MongoResourceRepository, MongoUserRepository}
import javax.inject._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.libs.json.Json.obj
import play.api.mvc._
import security.PasswordEncoder
import utils.{AppUtil, HanLPUtil, HashUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

/**
 * 用户层
 *
 * @author 梦境迷离
 * @since 2019-11-10
 * @version v1.0 DDD重构
 */
@Singleton
class UserController @Inject()(cc: ControllerComponents, userAction: UserAction,
                               passwordEncoder: PasswordEncoder, resourceRepo: MongoResourceRepository, userRepo: MongoUserRepository,
                               messageRepo: MongoMessageRepository,
                               mongoEventRepo: MongoEventRepository)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {

  private val DEFAULT_LIMIT_SIZE_15 = 15
  private val DEFAULT_LIMIT_SIZE_30 = 30

  def index() = checkLogin.async { implicit request: Request[AnyContent] =>
    //查询条件，下同
    val uid = request.session("uid")
    for {
      articles <- resourceRepo.findResourceBy(Json.obj("timeStat.updateTime" -> -1), DEFAULT_LIMIT_SIZE_15, Json.obj("resType" -> Resource.Article, "author._id" -> uid))
      articlesCount <- resourceRepo.countResourceBy(Json.obj("resType" -> Resource.Article, "author._id" -> uid))
      qas <- resourceRepo.findResourceBy(Json.obj("timeStat.updateTime" -> -1), DEFAULT_LIMIT_SIZE_15, Json.obj("resType" -> Resource.QA, "author._id" -> uid))
      qaCount <- resourceRepo.countResourceBy(Json.obj("resType" -> Resource.QA, "author._id" -> uid))
      collectRes <- resourceRepo.findStatBy(Json.obj("collectTime" -> -1), DEFAULT_LIMIT_SIZE_15, Json.obj("uid" -> uid))
      collectResCount <- resourceRepo.countStatBy(Json.obj("uid" -> uid))
    } yield {
      Ok(views.html.user.index(articles, articlesCount.toInt, qas, qaCount.toInt, collectRes, collectResCount.toInt))
    }
  }

  def home(uidOpt: Option[String]) = Action.async { implicit request: Request[AnyContent] =>
    uidOpt orElse RequestHelper.getUidOpt match {
      case Some(uid) =>
        for {
          createEvents <- mongoEventRepo.findBy(Json.obj("createTime" -> -1), DEFAULT_LIMIT_SIZE_30, Json.obj("actor._id" -> uid, "action" -> "create"))
          events <- mongoEventRepo.findBy(Json.obj("createTime" -> -1), DEFAULT_LIMIT_SIZE_30, Json.obj("actor._id" -> uid))
          userOpt <- userRepo.findById(uid) //uid就是MongoDB中的user的_id?
        } yield {
          Ok(views.html.user.home(uidOpt, userOpt, createEvents, events))
        }
      case None =>
        Future.successful(Ok(views.html.message("系统提示", "您查看的用户不存在！")))
    }
  }

  def message() = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      messages <- messageRepo.findBy(Json.obj("createTime" -> -1), DEFAULT_LIMIT_SIZE_15, Json.obj("uid" -> request.session("uid")))
      count <- messageRepo.countBy(Json.obj("uid" -> request.session("uid")))
    } yield {
      Ok(views.html.user.message(messages, count.toInt))
    }
  }

  def messageCount() = checkLogin.async { implicit request: Request[AnyContent] =>
    messageRepo.countBy(Json.obj("uid" -> request.session("uid"), "read" -> false)).map {
      count => Ok(Json.obj("status" -> 0, "count" -> count))
    }
  }

  //TODO 这怎么批量
  def readMessage() = checkLogin.async { implicit request: Request[AnyContent] =>
    messageRepo.readMessage(request.session("uid")).map(_ => Ok(Json.obj("status" -> 0)))
  }

  def removeMessage = checkLogin.async { implicit request: Request[AnyContent] =>
    Form(single("id" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "您的输入有误！" + errForm.errors))),
      _id => {
        messageRepo.deleteMessage(_id, request.session("uid")).map(_ => Ok(Json.obj("status" -> 0)))
      }
    )
  }

  def clearMessage() = checkLogin.async { implicit request: Request[AnyContent] =>
    messageRepo.clearMessage(request.session("uid")).map(_ => Ok(Json.obj("status" -> 0)))
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
        userRepo.updateUser(
          request.session("uid"),
          Json.obj(
            "setting.name" -> name,
            "setting.pinyin" -> HanLPUtil.convertToPinyin(name),
            "setting.gender" -> gender.getOrElse[String](""),
            "setting.city" -> city, "setting.introduction" -> introduction)
        ).map { _ =>
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
        userRepo.updateUser(request.session("uid"), Json.obj("setting.headImg" -> url)).map { _ =>
          Ok(Json.obj("status" -> 0)).addingToSession("headImg" -> url)
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
            && request.user.argon2Hash == passwordEncoder.hash(password.get, request.user.salt.get)
        ) {
          passwordEncoder.updateUserPassword(request.session("uid"), HashUtil.sha256(password1))
          Redirect(routes.Application.message("系统提示", "密码修改成功！"))
        } else {
          Redirect(routes.Application.message("系统提示", "您的输入有误！"))
        }
      }
    )
  }

  def findUserList(searchTerm: String, limit: Int) = Action.async { implicit request: Request[AnyContent] =>
    val queries = searchTerm.mkString(".*", ".*", ".*")
    for {
      list <- userRepo.findList(obj("setting.pinyin" -> obj("$regex" -> queries)), obj("setting.name" -> 1), 0, limit)
    } yield {
      val json = list map { u =>
        obj(
          "id" -> u._id,
          "value" -> u.setting.name,
          "link" -> s"/user/home?uid=${u._id}",
          "data" -> ""
        )
      }

      Ok(obj("code" -> 0, "data" -> json))
    }

    /*Source.fromFile("D:/name.txt", "utf-8").getLines().foreach{ line =>
      val arr = line.split("###")
      if(arr.length == 2) {
        userRepo.updateUser(arr(0), obj("setting.name" -> arr(1)))
        println(arr(0), arr(1))
      }

    }*/

  }

}
