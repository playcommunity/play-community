package controllers.admin

import javax.inject._

import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import controllers.checkAdmin
import models._
import play.api.data.Form
import play.api.data.Forms.{optional, text, tuple, _}
import play.api.libs.json._
import play.api.mvc._
import services._
import utils.{DateTimeUtil, RequestHelper}
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Json._

@Singleton
class AdminDocController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, elasticService: ElasticService, eventService: EventService, catalogService: CatalogService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {

  def index(page: Int) = checkAdmin.async { implicit request: Request[AnyContent] =>
    val cPage = if(page < 1){1}else{page}
    for {
      docs <- mongo.find[Resource](obj("resType" -> Resource.Doc)).sort(Json.obj("timeStat.updateTime" -> -1)).skip((cPage-1) * 15).limit(15).list
      total <- mongo.count[Resource](obj("resType" -> Resource.Doc))
    } yield {
      Ok(views.html.admin.doc.index(docs, cPage, total.toInt))
    }
  }

  def add = checkAdmin.async { implicit request: Request[AnyContent] =>
    Future.successful(Ok(views.html.admin.doc.edit(None)))
  }

  def edit(_id: String) = checkAdmin.async { implicit request: Request[AnyContent] =>
    for {
      docOpt <- mongo.findById[Resource](_id)
    } yield {
      Ok(views.html.admin.doc.edit(docOpt))
    }

  }

  def doAdd = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> optional(text), "title" -> nonEmptyText, "content" -> text, "catalogId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "您的输入有误！"))),
      tuple => {
        val (_idOpt, title, content, catalogId) = tuple
        _idOpt match {
          case Some(_id) =>
            eventService.updateResource(RequestHelper.getAuthor, _id, "doc", title)
            mongo.updateOne[Resource](Json.obj("_id" -> _id), Json.obj("$set" -> Json.obj(
              "title" -> title,
              "content" -> content,
              "keywords" -> "",
              "author.name" -> request.session("name"),
              "author.headImg" -> request.session("headImg"),
              "updateTime" -> DateTimeUtil.now(),
              "catalogId" -> catalogId
            ))).map{ wr =>
              Ok(Json.obj("status" -> 0, "action" -> "update"))
            }
          case None =>
            val _id = RequestHelper.generateId
            eventService.createResource(RequestHelper.getAuthor, _id, "doc", title)
            mongo.updateOne[User](Json.obj("_id" -> RequestHelper.getUid), Json.obj("$inc" -> Json.obj("stat.resCount" -> 1, "stat.docCount" -> 1)))
            mongo.insertOne[Resource](Resource(_id, title, "", Some(content), author = RequestHelper.getAuthor, resType = Resource.Doc, visible = false, doc = Some(DocInfo("", catalogId)))).map{ wr =>
              Ok(Json.obj("status" -> 0, "action" -> "create", "_id" -> _id))
            }
        }
      }
    )
  }

  def doRemove = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(single("_id" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      _id => {
        mongo.deleteById[Resource](_id).map{ wr =>
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def chooseCatalog(selectOpt: Option[String]) = checkAdmin.async { implicit request: Request[AnyContent] =>
    for{
      catalogOpt <- mongo.collection("doc-catalog").find().first
    } yield {
      catalogOpt match {
        case Some(c) => Ok(views.html.admin.doc.chooseCatalog((c \ "nodes").as[JsArray], selectOpt))
        case None => Ok(views.html.admin.doc.chooseCatalog(Json.arr(), None))
      }
    }
  }

  def getCatalogName(catalogId: String) = checkAdmin.async { implicit request: Request[AnyContent] =>
    for{
      name <- catalogService.getCatalogName(catalogId)
    } yield {
      Ok(Json.obj("name" -> name))
    }
  }

  def docSetting = checkAdmin.async { implicit request: Request[AnyContent] =>
    for {
      opt <- mongo.collection("common-setting").findById("docSetting")
    } yield {
      Ok(views.html.admin.doc.setting(opt.map(_.as[DocSetting]).getOrElse(DocSetting("docSetting", "", "未设置"))))
    }
  }

  def doDocSetting = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(tuple("defaultCatalogId" -> nonEmptyText, "defaultCatalogName" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      tuple => {
        val (defaultCatalogId, defaultCatalogName) = tuple
        mongo.collection("common-setting").updateOne(Json.obj("_id" -> "docSetting"), Json.obj("$set" -> Json.obj("defaultCatalogId" -> defaultCatalogId, "defaultCatalogName" -> defaultCatalogName)), upsert = true).map{ _ =>
          Redirect(routes.AdminDocController.docSetting())
        }
      }
    )
  }

  def catalog = checkAdmin.async { implicit request: Request[AnyContent] =>
    for{
      catalogOpt <- mongo.collection("doc-catalog").find().first
    } yield {
      catalogOpt match {
        case Some(c) => Ok(views.html.admin.doc.catalog((c \ "nodes").as[JsArray]))
        case None => Ok(views.html.admin.doc.catalog(Json.arr()))
      }
    }
  }

  def doSetCatalog = checkAdmin.async { implicit request: Request[AnyContent] =>
    val js = request.body.asJson
    mongo.updateOne[DocCatalog](
      Json.obj("_id" -> "1.6.x"),
      Json.obj(
        "$set" -> Json.obj("nodes" -> js, "updateTime" -> DateTimeUtil.now()),
        "$setOnInsert" -> Json.obj("isDefault" -> false, "createTime" -> DateTimeUtil.now())
      ),
      upsert = true
    ).map{ _ =>
      Ok(Json.obj("status" -> 0))
    }
  }

}
