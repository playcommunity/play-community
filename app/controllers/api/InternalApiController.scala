package controllers.api

import java.io.{ByteArrayOutputStream, File}
import javax.inject._

import akka.stream.Materializer
import akka.util.ByteString
import controllers.routes
import models.JsonFormats._
import models._
import org.apache.pdfbox.pdmodel.PDDocument
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.libs.oauth.{ConsumerKey, OAuth, ServiceInfo}
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.QueryOpts
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import services.{CommonService, ElasticService, MailerService}
import utils.PDFUtil.getCatalogs
import utils._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

@Singleton
class InternalApiController @Inject()(cc: ControllerComponents, val reactiveMongoApi: ReactiveMongoApi, config: Configuration, ws: WSClient, counterService: CommonService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))

  private val githubClientId = config.getOptional[String]("oauth.github.clientId").getOrElse("")
  private val githubClientSecret = config.getOptional[String]("oauth.github.clientSecret").getOrElse("")

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
          val email = js("email").as[String]
          val name = js("name").as[String]
          val headImg = js("avatar_url").as[String]
          val location = js("location").as[String]
          val bio = js("bio").as[String]
          val githubUrl = js("html_url").as[String]
          userColFuture.flatMap(_.find(Json.obj("login" -> email)).one[User]).flatMap {
            case Some(u) => Future.successful(u)
            case None => for {
              userCol <- userColFuture
              uid <- counterService.getNextSequence("user-sequence")
              u = User(uid.toString, Role.USER, email, "", UserSetting(name, "", bio, headImg, location), UserStat.DEFAULT, 0, true, "register", request.remoteAddress, None, List(Channel("github", "GitHub", githubUrl)), None)
              wr <- userCol.insert(u)
            } yield u
          }.map { u =>
            if (u.password == "") {
              Redirect(controllers.routes.UserController.setting("pass"))
                .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role)
            } else {
              Redirect(controllers.routes.Application.index(1))
                .addingToSession("uid" -> u._id, "login" -> u.login, "name" -> u.setting.name, "headImg" -> u.setting.headImg, "role" -> u.role)
            }
          }
        }
    } else {
      Future.successful(Ok(views.html.message("系统提示", "系统暂不支持GitHub登录！")))
    }
  }

  def test = Action { implicit request =>
    Ok("Test")
  }

}
