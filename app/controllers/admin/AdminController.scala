package controllers.admin

import javax.inject._
import akka.stream.Materializer
import akka.util.ByteString
import cn.playscala.mongo.Mongo
import com.hankcs.hanlp.HanLP
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.mvc._
import services.{CommonService, ElasticService, MailerService}
import utils.{HashUtil}
import scala.concurrent.{ExecutionContext, Future}
import controllers.checkAdmin
import play.api.libs.json.Json._

@Singleton
class AdminController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, elasticService: ElasticService, mailer: MailerService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {
  val settingCol = mongo.collection("common-setting")

  def index = checkAdmin.async { implicit request: Request[AnyContent] =>
    Future.successful(Ok(views.html.admin.index()))
  }

  def base = checkAdmin.async { implicit request: Request[AnyContent] =>
    for {
      opt <- settingCol.findById[SiteSetting]("siteSetting")
    } yield {
      Ok(views.html.admin.setting.base(opt.getOrElse(app.Global.siteSetting)))
    }
  }

  def doBaseSetting = checkAdmin.async { implicit request: Request[AnyContent] =>
    request.body.asJson match {
      case Some(obj) =>
        obj.validate[SiteSetting] match {
          case JsSuccess(s, _) =>
            app.Global.siteSetting = s
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
            ),
            true
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

  def notices = checkAdmin.async { implicit request =>
    Future.successful {
      Ok(arr(obj(
        "id" -> "000000001",
        "avatar" -> "https://gw.alipayobjects.com/zos/rmsportal/ThXAXghbEsBCCSDihZxY.png",
        "title" -> "你收到了 14 份新周报",
        "datetime" -> "2017-08-09",
        "type" -> "notification"
      )))
    }
  }

  def currentUser = checkAdmin.async { implicit request =>
    Future.successful {
      Ok(Json.parse(
        """
          |{
          |    "name": "Serati Ma",
          |    "avatar": "https://gw.alipayobjects.com/zos/rmsportal/BiazfanxmamNRoxxVxka.png",
          |    "userid": "00000001",
          |    "email": "antdesign@alipay.com",
          |    "signature": "海纳百川，有容乃大",
          |    "title": "交互专家",
          |    "group": "蚂蚁金服－某某某事业群－某某平台部－某某技术部－UED",
          |    "tags": [
          |      {
          |        "key": "0",
          |        "label": "很有想法的"
          |      },
          |      {
          |        "key": "1",
          |        "label": "专注设计"
          |      }
          |    ],
          |    "notifyCount": 12,
          |    "unreadCount": 11,
          |    "country": "China",
          |    "geographic": {
          |      "province": {
          |        "label": "浙江省",
          |        "key": "330000"
          |      },
          |      "city": {
          |        "label": "杭州市",
          |        "key": "330100"
          |      }
          |    },
          |    "address": "西湖区工专路 77 号",
          |    "phone": "0752-268888888"
          |  }
          |""".stripMargin))
    }
  }

}
