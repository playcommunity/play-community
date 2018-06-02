package controllers.api

import javax.inject._
import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import models.JsonFormats._
import models._
import org.apache.pdfbox.pdmodel.PDDocument
import play.api.{Configuration, Logger}
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.libs.oauth.{ConsumerKey, OAuth, ServiceInfo}
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.{CommonService, ElasticService, MailerService}
import utils.PDFUtil.getCatalogs
import utils._
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

@Singleton
class InternalApiController @Inject()(cc: ControllerComponents, mongo: Mongo, config: Configuration, ws: WSClient, counterService: CommonService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {

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
                .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1")
            } else {
              Redirect(controllers.routes.Application.index(1))
                .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role, "active" -> "1")
            }
          }
        }
    } else {
      Future.successful(Ok(views.html.message("系统提示", "系统暂不支持GitHub登录！")))
    }
  }
}
