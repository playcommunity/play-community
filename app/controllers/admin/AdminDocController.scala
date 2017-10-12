package controllers.admin

import javax.inject._

import akka.stream.Materializer
import controllers.checkAdmin
import models.JsonFormats.siteSettingFormat
import models._
import models.JsonFormats._
import play.api.data.Form
import play.api.data.Forms.{optional, text, tuple, _}
import play.api.libs.json._
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.QueryOpts
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import services._
import utils.{DateTimeUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class AdminDocController @Inject()(cc: ControllerComponents, reactiveMongoApi: ReactiveMongoApi, commonService: CommonService, elasticService: ElasticService, eventService: EventService, catalogService: CatalogService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {
  def docColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-doc"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def articleColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-article"))
  def settingColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-setting"))
  def docCatalogFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("doc-catalog"))


  def index(page: Int) = checkAdmin.async { implicit request: Request[AnyContent] =>
    val cPage = if(page < 1){1}else{page}
    for {
      docCol <- docColFuture
      docs <- docCol.find(Json.obj()).sort(Json.obj("title" -> 1)).options(QueryOpts(skipN = (cPage-1) * 15, batchSizeN = 15)).cursor[Doc]().collect[List](15)
      total <- docCol.count(None)
    } yield {
      Ok(views.html.admin.doc.index(docs, cPage, total))
    }
  }

  def add = checkAdmin.async { implicit request: Request[AnyContent] =>
    Future.successful(Ok(views.html.admin.doc.edit(None)))
  }

  def edit(_id: String) = checkAdmin.async { implicit request: Request[AnyContent] =>
    for {
      docCol <- docColFuture
      docOpt <- docCol.find(Json.obj("_id" -> _id)).one[Doc]
    } yield {
      Ok(views.html.admin.doc.edit(docOpt))
    }

  }

  def doAdd = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> optional(text), "title" -> nonEmptyText, "content" -> nonEmptyText, "catalogId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      tuple => {
        val (_idOpt, title, content, catalogId) = tuple
        for {
          docCol <- docColFuture
          _ <-  _idOpt match {
            case Some(_id) =>
              eventService.updateResource(RequestHelper.getAuthor, _id, "doc", title)
              docCol.update(Json.obj("_id" -> _id), Json.obj("$set" -> Json.obj(
                "title" -> title,
                "content" -> content,
                "keywords" -> "",
                "author.name" -> request.session("name"),
                "author.headImg" -> request.session("headImg"),
                "timeStat.updateTime" -> DateTimeUtil.now(),
                "catalogId" -> catalogId
              )))
            case None =>
              val _id = RequestHelper.generateId
              eventService.createResource(RequestHelper.getAuthor, _id, "doc", title)
              userColFuture.map(_.update(Json.obj("_id" -> RequestHelper.getUid), Json.obj("$inc" -> Json.obj("stat.resCount" -> 1, "stat.docCount" -> 1))))
              docCol.insert(Doc(_id, title, content, "", RequestHelper.getAuthor, List.empty[Reply], ViewStat(0, ""), VoteStat(0, ""), ReplyStat(0, 0, ""),  CollectStat(0, ""), DocTimeStat(DateTimeUtil.now, DateTimeUtil.now), catalogId))
          }
        } yield {
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def doRemove = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(single("_id" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      _id => {
        docColFuture.flatMap(_.remove(Json.obj("_id" -> _id))).map{ wr =>
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def chooseCatalog(selectOpt: Option[String]) = checkAdmin.async { implicit request: Request[AnyContent] =>
    for{
      docCatalog <- docCatalogFuture
      catalogOpt <- docCatalog.find(Json.obj()).one[JsValue]
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
      settingCol <- settingColFuture
      opt <- settingCol.find(Json.obj("_id" -> "docSetting")).one[DocSetting]
    } yield {
      Ok(views.html.admin.doc.setting(opt.getOrElse(DocSetting("docSetting", "", "未设置"))))
    }
  }

  def doDocSetting = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(tuple("defaultCatalogId" -> nonEmptyText, "defaultCatalogName" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      tuple => {
        val (defaultCatalogId, defaultCatalogName) = tuple
        for {
          settingCol <- settingColFuture
        } yield {
          settingCol.update(Json.obj("_id" -> "docSetting"), Json.obj("$set" -> Json.obj("defaultCatalogId" -> defaultCatalogId, "defaultCatalogName" -> defaultCatalogName)), upsert = true)
          Redirect(routes.AdminDocController.docSetting())
        }
      }
    )
  }

  def catalog = checkAdmin.async { implicit request: Request[AnyContent] =>
    for{
      docCatalog <- docCatalogFuture
      catalogOpt <- docCatalog.find(Json.obj()).one[JsValue]
    } yield {
      catalogOpt match {
        case Some(c) => Ok(views.html.admin.doc.catalog((c \ "nodes").as[JsArray]))
        case None => Ok(views.html.admin.doc.catalog(Json.arr()))
      }
    }
  }

  def doSetCatalog = checkAdmin.async { implicit request: Request[AnyContent] =>
    for {
      docCatalog <- docCatalogFuture
    } yield {
      val js = request.body.asJson
      docCatalog.update(
        Json.obj("_id" -> "1.6.x"),
        Json.obj(
          "$set" -> Json.obj("nodes" -> js, "updateTime" -> DateTimeUtil.now()),
          "$setOnInsert" -> Json.obj("isDefault" -> false, "createTime" -> DateTimeUtil.now())
        ),
        upsert = true
      )

      Ok(Json.obj("status" -> 0))
    }
  }

}
