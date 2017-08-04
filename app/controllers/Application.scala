package controllers

import java.io.ByteArrayOutputStream
import javax.inject._
import akka.stream.Materializer
import akka.util.ByteString
import models._
import models.JsonFormats._
import reactivemongo.play.json._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.QueryOpts
import reactivemongo.play.json.collection.JSONCollection
import services.{CounterService, ElasticService, MailerService}
import utils.{DateTimeUtil, HashUtil, RequestHelper, VerifyCodeUtils}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random


@Singleton
class Application @Inject()(cc: ControllerComponents, val reactiveMongoApi: ReactiveMongoApi, counterService: CounterService, elasticService: ElasticService, mailer: MailerService, userAction: UserAction)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def articleColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-article"))
  def oplogColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("oplog.rs"))

  def icons = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.icons())
  }

  def index(nav: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = if(page < 1){1}else{page}
    var q = Json.obj()
    var sort = Json.obj("timeStat.createTime" -> -1)
    nav match {
      case "1" =>
        q ++= Json.obj()
        sort = Json.obj("lastReply.replyTime" -> -1)
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
      topArticles <- articleCol.find(Json.obj("$or" -> Json.arr(Json.obj("top" -> true), Json.obj("recommended" -> true)))).cursor[Article]().collect[List](5)
      articles <- articleCol.find(q).sort(sort).options(QueryOpts(skipN = (cPage-1) * 15, batchSizeN = 15)).cursor[Article]().collect[List](15)
      total <- articleCol.count(Some(q))
      topViewArticles <- articleCol.find(Json.obj()).sort(Json.obj("viewStat.count" -> -1)).cursor[Article]().collect[List](10)
      topReplyArticles <- articleCol.find(Json.obj()).sort(Json.obj("replyStat.count" -> -1)).cursor[Article]().collect[List](10)
      topReplyUsers <- userCol.find(Json.obj()).sort(Json.obj("userStat.replyCount" -> -1)).cursor[User]().collect[List](12)
    } yield {
      Ok(views.html.index(nav, topArticles, articles, topViewArticles, topReplyArticles, topReplyUsers, cPage, total))
    }
  }

  def login(login: Option[String]) = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.login())
  }

  def logout = checkLogin(parser, ec) { implicit request: Request[AnyContent] =>
    Redirect(routes.Application.login(request.session.get("login"))).withNewSession
  }

  def doLogin = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("login" -> nonEmptyText, "password" -> nonEmptyText, "verifyCode" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("系统提示", "用户名或密码错误！"))),
      tuple => {
        val (login, password, verifyCode) = tuple
        if (HashUtil.sha256(verifyCode.toLowerCase) == request.session.get("verifyCode").getOrElse("")) {
          for{
            userCol <- userColFuture
            userOpt <- userCol.find(Json.obj("login" -> login, "password" -> HashUtil.sha256(password))).one[User]
          } yield {
            userOpt match {
              case Some(u) =>
                if (u.activeCode.nonEmpty) {
                  Redirect(routes.UserController.activate())
                    .withSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "0")
                } else {
                  Redirect(routes.UserController.home(Some(u._id)))
                    .withSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1")
                }
              case None =>
                Redirect(routes.Application.message("操作出错了！", "用户名或密码错误！"))
            }
          }
        } else {
          Future.successful(Redirect(routes.Application.message("操作出错了！", "验证码输入错误！")))
        }
      }
    )
  }

  def message(title: String, message: String) = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.message(title, message))
  }

  def search(q: String, plate: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = if(page < 1){1}else{page}

    elasticService.search("localhost", 9200, q).map{ t =>
      Ok(views.html.search(q, plate, t._2, cPage, t._1))
    }
  }

  def register = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.register())
  }

  def doRegister = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("login" -> nonEmptyText, "name" -> nonEmptyText, "password" -> nonEmptyText, "repassword" -> nonEmptyText, "verifyCode" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Redirect(routes.Application.message("注册出错了", "您的填写有误！"))),
      tuple => {
        val (login, name, password, repassword, verifyCode) = tuple
        if (HashUtil.sha256(verifyCode.toLowerCase) == request.session.get("verifyCode").getOrElse("")) {
          (for {
            userCol <- userColFuture
            userOpt <- userCol.find(Json.obj("login" -> login)).one[User]
          } yield {
            userOpt match {
              case Some(u) =>
                Future.successful(Redirect(routes.Application.message("注册出错了", "您已经注册过了！")))
              case None =>
                if (password == repassword) {
                  val activeCode = (0 to 7).map(i => Random.nextInt(10).toString).mkString
                  for {
                    uid <- counterService.getNextSequence("user-sequence")
                    wr <- userCol.insert(User(uid.toString, Role.COMMON_USER, login, HashUtil.sha256(password), UserSetting(name, "", "", "/assets/images/head.png", ""), UserStat(0, 0, 0, 0, 0, 0, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now()), 0, true, "register", request.remoteAddress, None, Some(activeCode)))
                  } yield {
                    if (wr.ok && wr.n == 1) {
                      // 发送激活码
                      mailer.sendEmail(name, login, views.html.mail.activeMail(name, activeCode).body)
                      Redirect(routes.UserController.activate())
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
        } else {
          Future.successful(Redirect(routes.Application.message("操作出错了！", "验证码输入错误！")))
        }
      }
    )
  }

  def doSendActiveMail = (checkLogin andThen userAction) { implicit request =>
    // 发送激活码
    mailer.sendEmail(RequestHelper.getName, RequestHelper.getLogin, views.html.mail.activeMail(RequestHelper.getName, request.user.activeCode.getOrElse("您的账号已经激活了！")).body)
    Ok(Json.obj("status" -> 0))
  }

  def doActive = (checkLogin andThen userAction) { implicit request =>
    Form(single("activeCode" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Redirect(routes.Application.message("系统提示", "您的填写有误！")),
      activeCode => {
        if (activeCode.trim == request.user.activeCode.getOrElse("")) {
          userColFuture.map(_.update(Json.obj("_id" -> request.user._id), Json.obj("$unset" -> Json.obj("activeCode" -> 1))))
          Redirect(routes.UserController.activate())
            .addingToSession("active" -> "1")
        } else {
          Redirect(routes.Application.message("系统提示", "您输入的激活码不正确！"))
        }
      }
    )
  }

  /**
    * 自动注册单点登录用户
    */
  def autoRegister = Action.async { implicit request: Request[AnyContent] =>
    userColFuture.flatMap(_.find(Json.obj("login" -> RequestHelper.getLogin)).one[User]).flatMap {
      case Some(u) =>
        Future.successful {
          Redirect(routes.DocController.index())
            .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role)
        }
      case None =>
        for{
          userCol <- userColFuture
          uid <- counterService.getNextSequence("user-sequence")
          _ <- userCol.insert(User(uid.toString, Role.COMMON_USER, RequestHelper.getLogin, "", UserSetting(RequestHelper.getName, "", "", RequestHelper.getHeadImg, ""), UserStat(0, 0, 0, 0, 0, 0, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now()), 0, true, request.session.get("from").getOrElse(""), request.remoteAddress, None, None))
        } yield {
          Redirect(routes.DocController.index())
            .addingToSession("uid" -> uid.toString, "role" -> Role.COMMON_USER)
        }
    }
  }

  def notFound = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.notFound())
  }

  def verifyCode() = Action { implicit request: Request[AnyContent] =>
    val verifyCode = VerifyCodeUtils.generateVerifyCode(4)
    val out: ByteArrayOutputStream = new ByteArrayOutputStream
    VerifyCodeUtils.outputImage(200, 80, out, verifyCode)
    Ok(ByteString(out.toByteArray))
      .as("image/jpeg")
      .addingToSession("verifyCode" -> HashUtil.sha256(verifyCode.toLowerCase()))
  }

}
