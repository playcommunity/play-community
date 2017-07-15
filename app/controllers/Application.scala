package controllers

import javax.inject._

import akka.stream.Materializer
import models._
import models.JsonFormats._
import reactivemongo.play.json._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.QueryOpts
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.collection.JSONCollection
import service.{CounterService, ElasticService}
import utils.{DateTimeUtil, HashUtil}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random


@Singleton
class Application @Inject()(cc: ControllerComponents, val reactiveMongoApi: ReactiveMongoApi, counterService: CounterService, elasticService: ElasticService)(implicit ec: ExecutionContext, mat: Materializer) extends AbstractController(cc) {
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def articleColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-article"))
  def oplogColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("oplog.rs"))

  def index(nav: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = if(page < 1){1}else{page}
    var q = Json.obj()
    var sort = Json.obj("timeStat.createTime" -> -1)
    nav match {
      case "1" =>
        q ++= Json.obj()
        sort = Json.obj("timeStat.lastReplyTime" -> -1)
      case "2" =>
        q ++= Json.obj()
        sort = Json.obj("timeStat.createTime" -> -1)
      case "3" =>
        q ++= Json.obj("replies.0" -> Json.obj("$exists" -> false))
        sort = Json.obj("timeStat.createTime" -> -1)
      case "4" =>
        q ++= Json.obj("recommended" -> true)
        sort = Json.obj("timeStat.lastReplyTime" -> -1)
      case _ =>
    }
    for {
      userCol <- userColFuture
      articleCol <- articleColFuture
      topArticles <- articleCol.find(Json.obj("top" -> true, "recommended" -> true)).cursor[Article]().collect[List](5)
      articles <- articleCol.find(q).sort(sort).options(QueryOpts(skipN = (cPage-1) * 15, batchSizeN = 15)).cursor[Article]().collect[List](15)
      topViewArticles <- articleCol.find(Json.obj()).sort(Json.obj("viewStat.count" -> -1)).cursor[Article]().collect[List](10)
      topReplyArticles <- articleCol.find(Json.obj()).sort(Json.obj("replyStat.count" -> -1)).cursor[Article]().collect[List](10)
      topReplyUsers <- userCol.find(Json.obj()).sort(Json.obj("userStat.replyCount" -> -1)).cursor[User]().collect[List](12)
    } yield {
      Ok(views.html.index(nav, topArticles, articles, topViewArticles, topReplyArticles, topReplyUsers, 0, 0))
    }
  }

  def message(title: String, message: String) = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.tips(title, message))
  }

  def register = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.register())
  }

  def doRegister = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("login" -> nonEmptyText, "name" -> nonEmptyText, "password" -> nonEmptyText, "repassword" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (login, name, password, repassword) = tuple
        (for{
          userCol <- userColFuture
          userOpt <- userCol.find(Json.obj("login" -> login)).one[User]
        } yield {
          userOpt match {
            case Some(u) =>
              Future.successful(Redirect(routes.Application.message("注册出错了", "您已经注册过了！")))
            case None =>
              if (password == repassword) {
                val verifyCode = (0 to 7).map(i => Random.nextInt(10).toString).mkString
                for{
                  uid <- counterService.getNextSequence("user-sequence")
                  wr <-  userCol.insert(User(uid.toString, Role.COMMON_USER, login, HashUtil.sha256(password), UserSetting(name, "", "", "/assets/images/head.png", ""), request.remoteAddress, UserStat(0, 0, 0, 0, 0, 0, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now()), 0, true, verifyCode))
                } yield {
                  if (wr.ok && wr.n == 1) {
                    Redirect(routes.UserController.home(uid.toString))
                      .withSession("login" -> login, "uid" -> uid.toString, "name" -> name, "headImg" -> "/assets/images/head.png")
                  } else {
                    Redirect(routes.Application.message("注册出错了", "很抱歉，似乎是发生了系统错误！"))
                  }
                }
              } else {
                Future.successful(Redirect(routes.Application.message("注册出错了", "您两次输入的密码不一致！")))
              }
          }
        }).flatMap(f1 => f1)
      }
    )
  }

  def login(login: Option[String]) = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.login())
  }

  def logout = Action { implicit request: Request[AnyContent] =>
    Redirect(routes.Application.login(request.session.get("login"))).withNewSession
  }

  def doLogin = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("login" -> nonEmptyText, "password" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (login, password) = tuple
        for{
          userCol <- userColFuture
          userOpt <- userCol.find(Json.obj("login" -> login, "password" -> HashUtil.sha256(password))).one[User]
        } yield {
          userOpt match {
            case Some(u) =>
              Redirect(routes.UserController.home(u._id))
                .withSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg)
            case None =>
              Redirect(routes.Application.message("操作出错了！", "用户名或密码错误！"))
          }
        }
      }
    )
  }

  def notFound = Action { implicit request: Request[AnyContent] =>
    elasticService.search("localhost", 9200, "中国").map(println _)
    Ok(views.html.notFound())
  }

}
