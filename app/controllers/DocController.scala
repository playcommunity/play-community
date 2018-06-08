package controllers

import javax.inject._

import cn.playscala.mongo.Mongo
import models.JsonFormats._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.mvc._
import services.{CommonService, EventService}
import utils.{BitmapUtil, RequestHelper}
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Json._

@Singleton
class DocController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, eventService: EventService) (implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {

  def index = Action.async { implicit request: Request[AnyContent] =>
    for{
      catalogOpt <- mongo.collection("doc-catalog").find().first
      firstDocOpt <- mongo.find[Doc]().sort(Json.obj("timeStat.createTime" -> 1)).first
      defaultDocOpt <- mongo.find[DocSetting](obj("_id" -> "docSetting")).first.flatMap{
        case Some(s) => mongo.find[Doc](obj("catalogId" -> s.defaultCatalogId)).first
        case None => Future.successful(None)
      }
    } yield {
      if (firstDocOpt.nonEmpty && catalogOpt.nonEmpty) {
        defaultDocOpt match {
          case Some(doc) =>
            Ok(views.html.doc.index((catalogOpt.get \ "nodes").as[JsArray], "", Some(doc)))
          case None =>
            Ok(views.html.doc.index((catalogOpt.get \ "nodes").as[JsArray], "", firstDocOpt))
        }
      } else {
        Ok(views.html.message("系统提示", "版主很懒，还未整理任何文档！"))
      }
    }
  }

  def viewCatalog(_id: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      Some(catalog) <- mongo.collection("doc-catalog").find().first
      docOpt <- mongo.find[Doc](obj("catalogId" -> _id)).first
    } yield {
      docOpt match {
        case Some(doc) =>
          // 记录访问次数
          if (RequestHelper.isLogin) {
            val uid = request.session("uid").toInt
            val viewBitmap = BitmapUtil.fromBase64String(doc.viewStat.bitmap)
            if (!viewBitmap.contains(uid)) {
              viewBitmap.add(uid)
              mongo.updateOne[Doc](obj("_id" -> doc._id), obj("$set" -> obj("viewStat" -> ViewStat(doc.viewStat.count + 1, BitmapUtil.toBase64String(viewBitmap)))))
            }
          }
          Ok(views.html.doc.index((catalog \ "nodes").as[JsArray], _id, Some(doc)))

        case None => Ok(views.html.doc.index((catalog \ "nodes").as[JsArray], _id, None))
      }
    }
  }

  def viewDoc(_id: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      Some(catalog) <- mongo.collection("doc-catalog").find().first
      docOpt <- mongo.find[Doc](Json.obj("_id" -> _id)).first
    } yield {
      docOpt match {
        case Some(doc) =>
          // 记录访问次数
          if (RequestHelper.isLogin) {
            val uid = request.session("uid").toInt
            val viewBitmap = BitmapUtil.fromBase64String(doc.viewStat.bitmap)
            if (!viewBitmap.contains(uid)) {
              viewBitmap.add(uid)
              mongo.updateOne[Doc](obj("_id" -> doc._id), obj("$set" -> obj("viewStat" -> ViewStat(doc.viewStat.count + 1, BitmapUtil.toBase64String(viewBitmap)))))
            }
          }
          Ok(views.html.doc.index((catalog \ "nodes").as[JsArray], _id, Some(doc)))

        case None => Ok(views.html.doc.index((catalog \ "nodes").as[JsArray], _id, None))
      }
    }
  }

}
