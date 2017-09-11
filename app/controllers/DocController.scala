package controllers

import javax.inject._

import models.JsonFormats._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.QueryOpts
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import services.{CommonService, EventService}
import utils.{BitmapUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DocController @Inject()(cc: ControllerComponents, val reactiveMongoApi: ReactiveMongoApi, commonService: CommonService, eventService: EventService) (implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {
  def docColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-doc"))
  def docCatalogFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("doc-catalog"))
  def categoryColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-category"))
  def msgColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-message"))
  def settingColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-setting"))
  def getColFuture(name: String) = reactiveMongoApi.database.map(_.collection[JSONCollection](name))


  def index = Action.async { implicit request: Request[AnyContent] =>
    for{
      docCol <- docColFuture
      docCatalog <- docCatalogFuture
      settingCol <- settingColFuture
      catalogOpt <- docCatalog.find(Json.obj()).one[JsValue]
      firstDocOpt <- docCol.find(Json.obj()).sort(Json.obj("timeStat.createTime" -> 1)).one[Doc]
      defaultDocOpt <- settingCol.find(Json.obj("_id" -> "docSetting")).one[DocSetting].flatMap{
        case Some(s) => docCol.find(Json.obj("catalogId" -> s.defaultCatalogId)).one[Doc]
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

  def view(catalogId: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      docCol <- docColFuture
      docCatalog <- docCatalogFuture
      Some(catalog) <- docCatalog.find(Json.obj()).one[JsValue]
      docOpt <- docCol.find(Json.obj("catalogId" -> catalogId)).one[Doc]
    } yield {
      docOpt match {
        case Some(doc) =>
          // 记录访问次数
          if (RequestHelper.isLogin) {
            val uid = request.session("uid").toInt
            val viewBitmap = BitmapUtil.fromBase64String(doc.viewStat.bitmap)
            if (!viewBitmap.contains(uid)) {
              viewBitmap.add(uid)
              docCol.update(Json.obj("_id" -> doc._id), Json.obj("$set" -> Json.obj("viewStat" -> ViewStat(doc.viewStat.count + 1, BitmapUtil.toBase64String(viewBitmap)))))
            }
          }
          Ok(views.html.doc.index((catalog \ "nodes").as[JsArray], catalogId, Some(doc)))

        case None => Ok(views.html.doc.index((catalog \ "nodes").as[JsArray], catalogId, None))
      }
    }
  }

}
