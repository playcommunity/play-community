package controllers.api

import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import javax.inject._
import models._
import play.api.{Configuration, Logger}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.{CommonService, QQService}
import utils._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class InternalApiController @Inject()(cc: ControllerComponents, mongo: Mongo, config: Configuration, ws: WSClient, counterService: CommonService, qqService: QQService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {

  private val githubClientId = config.getOptional[String]("oauth.github.clientId").getOrElse("")
  private val githubClientSecret = config.getOptional[String]("oauth.github.clientSecret").getOrElse("")

  /**
   * 如果GitHub用户未公开邮箱，则无法获取邮箱信息
   */
  def githubOauthCallback(code: String, state: String) = Action.async { implicit request =>
    if (githubClientId != "" && githubClientSecret != "") {
      ws.url("https://github.com/login/oauth/access_token")
        .withHttpHeaders("Accept" -> "application/json")
        .post(Map("client_id" -> githubClientId, "client_secret" -> githubClientSecret, "code" -> code, "state" -> "play"))
        .map(_.json("access_token").as[String])
        .flatMap { access_token =>
          ws.url(s"https://api.github.com/user?access_token=${access_token}")
            .withHttpHeaders("Accept" -> "application/json")
            .get().map(_.json)
        }.flatMap { js =>
        val login = js("login").as[String]
        val email = js("email").asOpt[String].getOrElse(login)
        val name = js("name").asOpt[String].getOrElse(login)
        val headImg = js("avatar_url").as[String]
        val location = js("location").asOpt[String].getOrElse("")
        val bio = js("bio").asOpt[String].getOrElse("")
        val githubUrl = js("html_url").as[String]

        mongo.find[User](Json.obj("$or" -> Json.arr(Json.obj("login" -> email), Json.obj("login" -> login)))).first.flatMap {
          case Some(u) => Future.successful(u)
          case None => for {
            uid <- counterService.getNextSequence("user-sequence")
            u = User(uid.toString, Role.USER, email, "", UserSetting(name, "", bio, headImg, location), UserStat.DEFAULT, 0, true, "register", request.remoteAddress, None, List(Channel("github", "GitHub", githubUrl)), None)
            wr <- mongo.insertOne[User](u)
          } yield u
        }.map { u =>
          if (u.password == "" && email.contains("@")) {
            Redirect(controllers.routes.UserController.setting("pass"))
              .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1",
                "loginType" -> LoginType.GITHUB)
          } else {
            Redirect(controllers.routes.Application.index("0", "/", 1))
              .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1",
                "loginType" -> LoginType.GITHUB)
          }
        }
      }
    } else {
      Future.successful(Ok(views.html.message("系统提示", "系统暂不支持GitHub登录！")))
    }
  }

  /**
   * QQ登录的不需要重定向到绑定页面，目前没有存accessToken
   *
   * @param code
   * @param state
   * @return
   */
  def qqOauthCallback(code: String, state: String) = Action.async { implicit request =>
    (for {
      accessToken <- qqService.getAccessToken(code)
      openId <- qqService.getOpenId(accessToken)
      userOpt <- qqService.getUserInfo(accessToken, openId)
    } yield {
      userOpt match {
        case Some(u) =>
          Redirect(controllers.routes.Application.index("0", "/", 1))
            .withSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1", "loginType" -> LoginType.QQ)

        case None =>
          Ok(views.html.message("系统提示", "很抱歉，QQ登录失败！"))
      }
    }).recover{
      case t: Throwable =>
        Logger.error("QQ登录异常：" + t.getMessage, t)
        Ok(views.html.message("系统提示", "很抱歉，QQ登录异常！"))
    }
  }
}