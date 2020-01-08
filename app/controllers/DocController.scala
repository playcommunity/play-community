package controllers

import javax.inject._
import cn.playscala.mongo.Mongo
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

  def index = checkLogin.async { implicit request: Request[AnyContent] =>
    for{
      catalogOpt <- mongo.collection("doc-catalog").find().first
      firstDocOpt <- mongo.find[Resource](obj("resType" -> Resource.Doc)).sort(Json.obj("createTime" -> 1)).first
      defaultDocOpt <- mongo.collection("common-setting").find[DocSetting](obj("_id" -> "docSetting")).first.flatMap{
        case Some(s) => mongo.find[Resource](obj("catalogId" -> s.defaultCatalogId)).first
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

  def viewCatalog(_id: String) = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      Some(catalog) <- mongo.collection("doc-catalog").find().first
      docOpt <- mongo.find[Resource](obj("doc.catalogId" -> _id)).first
    } yield {
      docOpt match {
        case Some(doc) =>
          // 记录访问次数
          if (RequestHelper.isLogin) {
            val uid = request.session("uid").toInt
            val viewBitmap = BitmapUtil.fromBase64String(doc.viewStat.bitmap)
            if (!viewBitmap.contains(uid)) {
              viewBitmap.add(uid)
              mongo.updateOne[Resource](obj("_id" -> doc._id), obj("$set" -> obj("viewStat" -> ViewStat(doc.viewStat.count + 1, BitmapUtil.toBase64String(viewBitmap)))))
            }
          }
          Ok(views.html.doc.index((catalog \ "nodes").as[JsArray], _id, Some(doc)))

        case None => Ok(views.html.doc.index((catalog \ "nodes").as[JsArray], _id, None))
      }
    }
  }

  def viewDoc(_id: String) = checkLogin.async { implicit request: Request[AnyContent] =>
    for {
      Some(catalog) <- mongo.collection("doc-catalog").find().first
      docOpt <- mongo.find[Resource](Json.obj("_id" -> _id)).first
    } yield {
      docOpt match {
        case Some(doc) =>
          // 记录访问次数
          /*if (RequestHelper.isLogin) {
            val uid = request.session("uid").toInt
            val viewBitmap = BitmapUtil.fromBase64String(doc.viewStat.bitmap)
            if (!viewBitmap.contains(uid)) {
              viewBitmap.add(uid)
              mongo.updateOne[Resource](obj("_id" -> doc._id), obj("$set" -> obj("viewStat" -> ViewStat(doc.viewStat.count + 1, BitmapUtil.toBase64String(viewBitmap)))))
            }
          }*/
          mongo.updateOne[Resource](obj("_id" -> doc._id), obj("$set" -> obj("viewStat" -> ViewStat(doc.viewStat.count + 1, doc.viewStat.bitmap))))

          Ok(views.html.doc.index((catalog \ "nodes").as[JsArray], _id, Some(doc)))

        case None => Ok(views.html.doc.index((catalog \ "nodes").as[JsArray], _id, None))
      }
    }
  }

}
