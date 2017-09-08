package controllers.admin

import java.io.ByteArrayOutputStream
import javax.inject._

import akka.stream.Materializer
import akka.util.ByteString
import com.hankcs.hanlp.HanLP
import controllers.routes
import models.JsonFormats._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json._
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoApi
import models.JsonFormats.siteSettingFormat
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import services.{CommonService, ElasticService, MailerService}
import utils.{DateTimeUtil, HashUtil}
import scala.concurrent.{ExecutionContext, Future}
import controllers.checkAdmin

@Singleton
class AdminController @Inject()(cc: ControllerComponents, reactiveMongoApi: ReactiveMongoApi, commonService: CommonService, elasticService: ElasticService, mailer: MailerService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def categoryColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-category"))
  def articleColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-article"))
  def settingColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-setting"))
  def docCatalogFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("doc-catalog"))

  def index = checkAdmin.async { implicit request: Request[AnyContent] =>
    Future.successful(Ok(views.html.admin.index()))
  }

  def base = checkAdmin.async { implicit request: Request[AnyContent] =>
    for {
      settingCol <- settingColFuture
      opt <- settingCol.find(Json.obj("_id" -> "siteSetting")).one[SiteSetting]
    } yield {
      Ok(views.html.admin.setting.base(opt.getOrElse(App.siteSetting)))
    }
  }

  def doBaseSetting = checkAdmin.async { implicit request: Request[AnyContent] =>
    request.body.asJson match {
      case Some(obj) =>
        obj.validate[SiteSetting] match {
          case JsSuccess(s, _) =>
            App.siteSetting = s
            settingColFuture.flatMap(_.update(Json.obj("_id" -> "siteSetting"), Json.obj("$set" -> obj), upsert = true)).map{ wr =>
              println(wr)
              Ok(Json.obj("status" -> 0))
            }
          case JsError(_) =>
            Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "请求格式有误！")))
        }
      case None => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "请求格式有误！")))
    }
  }

  def category = checkAdmin.async { implicit request: Request[AnyContent] =>
    for{
      categoryCol <- categoryColFuture
      list <- categoryCol.find(Json.obj()).cursor[Category]().collect[List]()
    } yield {
      Ok(views.html.admin.category(list))
    }
  }

  // 临时实现，重命名时允许重名
  def doAddCategory = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> optional(nonEmptyText), "name" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      tuple => {
        val (_idOpt, name) = tuple
        for{
          categoryCol <- categoryColFuture
          opt <- categoryCol.find(Json.obj("name" -> name)).one[Category]
        } yield {
          val _id = _idOpt.getOrElse(HashUtil.md5(name.trim))
          categoryCol.update(
            Json.obj("_id" -> _id),
            Json.obj(
              "$set" -> Json.obj("name" -> name.trim),
              "$setOnInsert" -> Json.obj(
                "path" -> s"/${HanLP.convertToPinyinString(name.trim, "-", false)}",
                "parentPath" -> "/",
                "index" -> 0,
                "disabled" -> false
              )
            )
          )
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def doRemoveCategory = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(single("_id" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      _id => {
        categoryColFuture.flatMap(_.remove(Json.obj("_id" -> _id))).map{ wr =>
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

}
