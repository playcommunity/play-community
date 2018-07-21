package controllers

import java.time.Instant
import javax.inject._
import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import models._
import org.bson.types.ObjectId
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.mvc._
import services.{CommonService, EventService}
import utils._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class ResourceController @Inject()(cc: ControllerComponents, mongo: Mongo, resourceController: GridFSController, userAction: UserAction, eventService: EventService, commonService: CommonService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {

  def index(resType: String, status: String, path: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = if(page < 1){1}else{page}
    var q = Json.obj("resType" -> resType, "categoryPath" -> Json.obj("$regex" -> s"^${path}"))
    val sort = Json.obj("timeStat.createTime" -> -1)
    status match {
      case "0" =>
      case "1" =>
        q ++= Json.obj("closed" -> false)
      case "2" =>
        q ++= Json.obj("closed" -> true)
      case "3" =>
        q ++= Json.obj("recommended" -> true)
      case _ =>
    }
    for {
      resources <- mongo.find[Resource](q).sort(sort).skip((cPage-1) * 15).limit(15).list()
      topViewResources <- mongo.find[Resource]().sort(Json.obj("viewStat.count" -> -1)).limit(10).list()
      topReplyResources <- mongo.find[Resource]().sort(Json.obj("replyCount" -> -1)).limit(10).list()
      total <- mongo.count[Resource](q)
    } yield {
      if (total > 0 && cPage > math.ceil(total/15.0).toInt) {
        Redirect(s"/${resType}s")
      } else {
        Ok(views.html.resource.index(resType, status, path, resources, topViewResources, topReplyResources, cPage, total.toInt))
      }
    }
  }

  def add(resType: String) = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    for {
      categoryList <- mongo.find[Category](Json.obj("parentPath" -> "/", "disabled" -> false)).list()
    } yield {
      Ok(views.html.resource.edit(resType, None, categoryList))
    }
  }

  def edit(_id: String) = checkAdminOrOwner("_id").async { implicit request: Request[AnyContent] =>
    (for {
      resource <- mongo.findById[Resource](_id)
      categoryList <- mongo.find[Category](Json.obj("parentPath" -> "/")).list()
    } yield {
      resource match {
        case Some(r) =>
          Ok(views.html.resource.edit(r.resType, Some(r), categoryList))
        case None => Redirect(routes.Application.notFound)
      }
    }).recover{ case NonFatal(t) =>
      Logger.error(t.getMessage, t)
      Ok("Error")
    }
  }

  def doAdd = (checkActive andThen checkAdminOrOwner("_id")).async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> optional(text), "title" -> nonEmptyText,"content" -> nonEmptyText, "keywords" -> nonEmptyText, "categoryPath" -> nonEmptyText, "resType" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      tuple => {
        val (_idOpt, title, content, keywords, categoryPath, resType) = tuple
        for {
          category <- mongo.find[Category](Json.obj("path" -> categoryPath)).first
          wr <-  _idOpt match {
            case Some(_id) =>
              eventService.updateResource(RequestHelper.getAuthor, _id, "article", title)
              mongo.updateOne[Resource](Json.obj("_id" -> _id), Json.obj("$set" -> Json.obj(
                "title" -> title,
                "content" -> content,
                "keywords" -> keywords,
                "categoryPath" -> categoryPath,
                "categoryName" -> category.map(_.name).getOrElse[String]("-"),
                "author.name" -> request.session("name"),
                "author.headImg" -> request.session("headImg"),
                "updateTime" -> Instant.now()
              )))
            case None =>
              val _id = RequestHelper.generateId
              eventService.createResource(RequestHelper.getAuthor, _id, "article", title)
              mongo.updateOne[User](Json.obj("_id" -> RequestHelper.getUid), Json.obj("$inc" -> Json.obj("stat.resCount" -> 1, "stat.articleCount" -> 1)))
              mongo.insertOne[Resource](Resource(_id, title, content, keywords, "quill", RequestHelper.getAuthor, Nil, None, ViewStat(0, ""), VoteStat(0, ""), 0, CollectStat(0, ""), Instant.now, Instant.now, false, false, false, resType, categoryPath, category.map(_.name).getOrElse("-"), None, None))
          }
        } yield {
          Redirect(routes.ResourceController.index(resType, "0", categoryPath, 1))
        }
      }
    )
  }

  def view(_id: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      article <- mongo.findById[Resource](_id)
    } yield {
      article match {
        case Some(a) =>
          request.session.get("uid") match {
            case Some(id) =>
              val uid = id.toInt
              val viewBitmap = BitmapUtil.fromBase64String(a.viewStat.bitmap)
              if (!viewBitmap.contains(uid)) {
                viewBitmap.add(uid)
                mongo.updateOne[Resource](Json.obj("_id" -> _id), Json.obj("$set" -> Json.obj("viewStat" -> ViewStat(a.viewStat.count + 1, BitmapUtil.toBase64String(viewBitmap)))))
                Ok(views.html.resource.detail(a.copy(viewStat = a.viewStat.copy(count = a.viewStat.count + 1))))
              } else {
                Ok(views.html.resource.detail(a))
              }
            case None =>
              Ok(views.html.resource.detail(a))
          }
        case None => Redirect(routes.Application.notFound)
      }
    }
  }
  def doCollect() = checkLogin.async { implicit request: Request[AnyContent] =>
    Form(single("resId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("success" -> false, "message" -> "invalid args."))),
      resId => {
        val uid = request.session("uid").toInt
        for {
          Some(resObj) <- mongo.collection[Resource].find(Json.obj("_id" -> resId), Json.obj("title" -> 1, "author" -> 1, "timeStat" -> 1, "collectStat" -> 1, "resType" -> 1, "createTime" -> 1)).first
        } yield {
          val collectStat = resObj("collectStat").as[CollectStat]
          val resOwner = resObj("author").as[Author]
          val resTitle = resObj("title").as[String]
          val resType = resObj("resType").as[String]
          val resCreateTime = resObj("createTime").as[Long]
          val bitmap = BitmapUtil.fromBase64String(collectStat.bitmap)
          // 收藏
          if (!bitmap.contains(uid)) {
            eventService.collectResource(RequestHelper.getAuthor, resId, resType, resTitle)
            bitmap.add(uid)
            mongo.updateOne[Resource](Json.obj("_id" -> resId), Json.obj("$set" -> Json.obj("collectStat" -> CollectStat(collectStat.count + 1, BitmapUtil.toBase64String(bitmap)))))
            mongo.insertOne[StatCollect](StatCollect(ObjectId.get.toHexString, request.session("uid"), resType, resId, resOwner, resTitle, Instant.ofEpochMilli(resCreateTime), DateTimeUtil.now()))
            Ok(Json.obj("status" -> 0))
          }
          // 取消收藏
          else {
            bitmap.remove(uid)
            mongo.updateOne[Resource](Json.obj("_id" -> resId), Json.obj("$set" -> Json.obj("collectStat" -> CollectStat(collectStat.count - 1, BitmapUtil.toBase64String(bitmap)))))
            mongo.deleteMany[StatCollect](Json.obj("uid" -> request.session("uid"), "resId" -> resId, "resType" -> resType))
            Ok(Json.obj("status" -> 0))
          }
        }
      }
    )
  }

  def doRemove = checkAdminOrOwner("_id").async { implicit request: Request[AnyContent] =>
    Form(tuple("resType" -> nonEmptyText,"resId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "输入有误！"))),
      tuple => {
        val (resType, resId) = tuple
        val resCol = mongo.collection(s"common-${resType}")
        for{
          objOpt <- resCol.find(Json.obj("_id" -> resId), Json.obj("title" -> 1)).first
          wr <- resCol.deleteOne(Json.obj("_id" -> resId))
        } yield {
          val resTitle = (objOpt.get \ "title").as[String]
          eventService.removeResource(RequestHelper.getAuthor, resId, resType, resTitle)
          mongo.updateOne[User](Json.obj("_id" -> resId.split("-")(0)), Json.obj("$inc" -> Json.obj("stat.resCount" -> -1, s"stat.${resType}Count" -> -1)))
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  // 将资源推至首页
  def doPush = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(tuple("resType" -> nonEmptyText,"resId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "输入有误！"))),
      tuple => {
        val (resType, resId) = tuple
        val resCol = mongo.collection(s"common-${resType}")
        for{
          Some(resObj) <- resCol.find(Json.obj("_id" -> resId), Json.obj("title" -> 1, "author" -> 1)).first
        } yield {
          val resOwner = resObj("author").as[Author]
          val resTitle = resObj("title").as[String]
          commonService.getNextSequence("common-news").map{index =>
            //mongo.insertOne[News](News(index.toString, resTitle, s"/${resType}/view?_id=${resId}", resOwner, resType, Some(false), DateTimeUtil.now()))
          }
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def doAcceptReply = (checkActive andThen checkAdminOrOwner("_id")).async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> nonEmptyText, "rid" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "弄啥嘞？"))),
      tuple => {
        val (_id, rid) = tuple
        for {
          Some(qa) <- mongo.find[QA](Json.obj("_id" -> _id)).first
        } yield {
          if (qa.answer.isEmpty) {
            eventService.acceptReply(RequestHelper.getAuthor, _id, "qa", qa.title)
            qa.replies.find(_._id == rid) match {
              case Some(reply) =>
                mongo.updateOne[QA](Json.obj("_id" -> _id), Json.obj("$set" -> Json.obj("answer" -> reply)))
                mongo.updateOne[User](Json.obj("_id" -> reply.author._id), Json.obj("$inc" -> Json.obj("score" -> 10)))

                // 消息提醒
                mongo.insertOne[Message](Message(ObjectId.get.toHexString, reply.author._id, "qa", qa._id, qa.title, RequestHelper.getAuthorOpt.get, "accept", "恭喜您，您的回复已被采纳！", DateTimeUtil.now(), false))

                Ok(Json.obj("status" -> 0))
              case None =>
                Ok(Json.obj("status" -> 1, "msg" -> "弄啥嘞？"))
            }

          } else {
            Ok(Json.obj("status" -> 1, "msg" -> "该问题已有采纳答案！"))
          }
        }
      }
    )
  }

}
