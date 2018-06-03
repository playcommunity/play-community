package controllers.admin

import javax.inject._
import akka.stream.Materializer
import akka.util.ByteString
import cn.playscala.mongo.Mongo
import com.hankcs.hanlp.HanLP
import models.JsonFormats._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.mvc._
import models.JsonFormats.siteSettingFormat
import services.{CommonService, ElasticService, MailerService}
import utils.{HashUtil}
import scala.concurrent.{ExecutionContext, Future}
import controllers.checkAdmin
import play.api.libs.json.Json._

@Singleton
class AdminController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, elasticService: ElasticService, mailer: MailerService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {
  val settingCol = mongo.getCollection("common-setting")

  def index = checkAdmin.async { implicit request: Request[AnyContent] =>
    Future.successful(Ok(views.html.admin.index()))
  }

  def base = checkAdmin.async { implicit request: Request[AnyContent] =>
    for {
      opt <- settingCol.findById("siteSetting")
    } yield {
      Ok(views.html.admin.setting.base(opt.map(_.as[SiteSetting]).getOrElse(App.siteSetting)))
    }
  }

  def doBaseSetting = checkAdmin.async { implicit request: Request[AnyContent] =>
    request.body.asJson match {
      case Some(obj) =>
        obj.validate[SiteSetting] match {
          case JsSuccess(s, _) =>
            App.siteSetting = s
            settingCol.updateOne(Json.obj("_id" -> "siteSetting"), Json.obj("$set" -> obj), upsert = true).map{ wr =>
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
      list <- mongo.find[Category]().list
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
          opt <- mongo.find[Category](Json.obj("name" -> name)).first
        } yield {
          val _id = _idOpt.getOrElse(HashUtil.md5(name.trim))
          mongo.updateOne[Category](
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
        mongo.deleteById[Category](_id).map{ wr =>
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

}
