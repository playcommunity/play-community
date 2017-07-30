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
import play.api.libs.json.Json
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
      Ok(views.html.admin.setting.base(opt.getOrElse(SiteSetting(App.name, App.url, App.logo))))
    }
  }

  def doBaseSetting = Action.async { implicit request: Request[AnyContent] =>
    Form(tuple("name" -> nonEmptyText, "url" -> nonEmptyText, "logo" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      tuple => {
        val (name, url, logo) = tuple
        for {
          settingCol <- settingColFuture
          wr <- settingCol.update(Json.obj("_id" -> "siteSetting"), Json.obj("$set" -> Json.obj("name" -> name, "url" -> url, "logo" -> logo)), upsert = true)
        } yield {
          App.name = name
          App.logo = logo
          App.url = url
          Redirect(routes.AdminController.base())
        }
      }
    )
  }

}
