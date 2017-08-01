package controllers.admin

import java.io.ByteArrayOutputStream
import javax.inject._

import akka.stream.Materializer
import akka.util.ByteString
import controllers.routes
import models.JsonFormats._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.QueryOpts
import models.JsonFormats.siteSettingFormat
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import services.{CounterService, ElasticService, MailerService}
import utils.{DateTimeUtil, HashUtil, UserHelper, VerifyCodeUtils}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random


@Singleton
class AdminController @Inject()(cc: ControllerComponents, val reactiveMongoApi: ReactiveMongoApi, counterService: CounterService, elasticService: ElasticService, mailer: MailerService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def articleColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-article"))
  def settingColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-setting"))

  def index = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.admin.index())
  }

  def base = Action.async { implicit request: Request[AnyContent] =>
    for {
      settingCol <- settingColFuture
      opt <- settingCol.find(Json.obj("_id" -> "siteSetting")).one[SiteSetting]
    } yield {
      Ok(views.html.admin.setting.base(opt.getOrElse(SiteSetting(App.name, App.url, App.logo, Nil, "/assets/favicon.ico"))))
    }
  }

  def doBaseSetting = Action.async { implicit request: Request[AnyContent] =>
    request.body.asJson match {
      case Some(obj) =>
        obj.validate[SiteSetting] match {
          case JsSuccess(s, _) =>
            App.name = s.name
            App.logo = s.logo
            App.url = s.url
            App.links = s.links
            settingColFuture.flatMap(_.update(Json.obj("_id" -> "siteSetting"), Json.obj("$set" -> obj))).map{ _ =>
              Ok(Json.obj("status" -> 0))
            }
          case JsError(_) =>
            Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "请求格式有误！")))
        }
      case None => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "请求格式有误！")))
    }
  }

}
