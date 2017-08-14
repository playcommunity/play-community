package controllers

import javax.inject._

import models.JsonFormats._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.QueryOpts
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import services.{CounterService, EventService}
import utils.{BitmapUtil, DateTimeUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class QAController @Inject()(cc: ControllerComponents, val reactiveMongoApi: ReactiveMongoApi, counter: CounterService, eventService: EventService) (implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {
  def qaColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-qa"))
  def categoryColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-category"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def msgColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-message"))
  def getColFuture(name: String) = reactiveMongoApi.database.map(_.collection[JSONCollection](name))

  def index(nav: String, path: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = if(page < 1){1}else{page}
    var q = Json.obj("categoryPath" -> Json.obj("$regex" -> s"^${path}"))
    var sort = Json.obj("timeStat.createTime" -> -1)
    nav match {
      case "1" =>
        q ++= Json.obj()
        sort = Json.obj("lastReply.replyTime" -> -1)
      case "2" =>
        q ++= Json.obj()
        sort = Json.obj("timeStat.createTime" -> -1)
      case "3" =>
        q ++= Json.obj("replies.0" -> Json.obj("$exists" -> false))
        sort = Json.obj("timeStat.createTime" -> -1)
      case "4" =>
        q ++= Json.obj("recommended" -> true)
        sort = Json.obj("timeStat.lastReplyTime" -> -1)
      case _ =>
    }
    for {
      qaCol <- qaColFuture
      qas <- qaCol.find(q).sort(sort).options(QueryOpts(skipN = (cPage-1) * 15, batchSizeN = 15)).cursor[QA]().collect[List](15)
      topViewQAs <- qaCol.find(Json.obj()).sort(Json.obj("viewStat.count" -> -1)).cursor[QA]().collect[List](10)
      topReplyQAs <- qaCol.find(Json.obj()).sort(Json.obj("replyStat.count" -> -1)).cursor[QA]().collect[List](10)
      total <- qaCol.count(None)
    } yield {
      if (total > 0 && cPage > math.ceil(total/15.0).toInt) {
        Redirect(routes.QAController.index(nav, path, math.ceil(total/15.0).toInt))
      } else {
        Ok(views.html.qa.index(nav, path, qas, topViewQAs, topReplyQAs, cPage, total))
      }
    }
  }

  def add = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    for {
      categoryCol <- categoryColFuture
      categoryList <- categoryCol.find(Json.obj("parentPath" -> "/")).cursor[Category]().collect[List]()
    } yield {
      Ok(views.html.qa.add(None, categoryList))
    }
  }

  def edit(_id: String) = checkAdminOrOwner("_id").async { implicit request: Request[AnyContent] =>
    for {
      qaCol <- qaColFuture
      qa <- qaCol.find(Json.obj("_id" -> _id)).one[QA]
      categoryCol <- categoryColFuture
      categoryList <- categoryCol.find(Json.obj("parentPath" -> "/")).cursor[Category]().collect[List]()
    } yield {
      qa match {
        case Some(a) => Ok(views.html.qa.add(Some(a), categoryList))
        case None => Redirect(routes.Application.notFound)
      }
    }
  }

  def doAdd = (checkActive andThen checkAdminOrOwner("_id")).async { implicit request: Request[AnyContent] =>
    Form(tuple("_id" -> optional(text), "title" -> nonEmptyText,"content" -> nonEmptyText, "categoryPath" -> nonEmptyText, "score" -> number)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      tuple => {
        val (_idOpt, title, content, categoryPath, score) = tuple
        for {
          qaCol <- qaColFuture
          categoryCol <- categoryColFuture
          category <- categoryCol.find(Json.obj("path" -> categoryPath)).one[Category]
          _ <-  _idOpt match {
                  case Some(_id) =>
                    eventService.updateResource(RequestHelper.getAuthor, _id, "qa", title)
                    qaCol.update(Json.obj("_id" -> _id), Json.obj("$set" -> Json.obj(
                      "title" -> title,
                      "content" -> content,
                      "categoryPath" -> categoryPath,
                      "categoryName" -> category.map(_.name).getOrElse[String]("-"),
                      "score" -> score,
                      "author.name" -> request.session("name"),
                      "author.headImg" -> request.session("headImg"),
                      "timeStat.updateTime" -> DateTimeUtil.now()
                    )))
                  case None =>
                    val _id = RequestHelper.generateId
                    eventService.createResource(RequestHelper.getAuthor, _id, "qa", title)
                    qaCol.insert(QA(_id, title, content, "quill", RequestHelper.getAuthor, categoryPath, category.map(_.name).getOrElse("-"), score, List.empty[String], List.empty[Reply], None, None, ViewStat(0, ""), VoteStat(0, ""), ReplyStat(0, 0, ""),  CollectStat(0, ""), QATimeStat(DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now)))
                    userColFuture.map(_.update(Json.obj("_id" -> RequestHelper.getUid), Json.obj("$inc" -> Json.obj("stat.resCount" -> 1, "stat.qaCount" -> 1))))
                }
        } yield {
          Redirect(routes.QAController.index("0", categoryPath, 1))
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
          qaCol <- qaColFuture
          Some(qa) <- qaCol.find(Json.obj("_id" -> _id)).one[QA]
        } yield {
          if (qa.answer.isEmpty) {
            eventService.acceptReply(RequestHelper.getAuthor, _id, "qa", qa.title)
            qa.replies.find(_._id == rid) match {
              case Some(reply) =>
                qaCol.update(Json.obj("_id" -> _id), Json.obj("$set" -> Json.obj("answer" -> reply)))
                userColFuture.map(_.update(Json.obj("_id" -> reply.author._id), Json.obj("$inc" -> Json.obj("score" -> qa.score))))
                userColFuture.map(_.update(Json.obj("_id" -> qa.author._id), Json.obj("$inc" -> Json.obj("score" -> -qa.score))))

                // 消息提醒
                msgColFuture.map(_.insert(Message(BSONObjectID.generate().stringify, reply.author._id, "qa", qa._id, qa.title, RequestHelper.getAuthorOpt.get, "accept", "恭喜您，您的回复已被采纳！", DateTimeUtil.now(), false)))

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

  def view(_id: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      qaCol <- qaColFuture
      qa <- qaCol.find(Json.obj("_id" -> _id)).one[QA]
      topViewQAs <- qaCol.find(Json.obj()).sort(Json.obj("viewStat.count" -> -1)).cursor[QA]().collect[List](10)
      topReplyQAs <- qaCol.find(Json.obj()).sort(Json.obj("replyStat.count" -> -1)).cursor[QA]().collect[List](10)
    } yield {
      qa match {
        case Some(a) =>
          request.session.get("uid") match {
            case Some(uid) =>
              val uid = request.session("uid").toInt
              val viewBitmap = BitmapUtil.fromBase64String(a.viewStat.bitmap)
              if (!viewBitmap.contains(uid)) {
                viewBitmap.add(uid)
                qaCol.update(Json.obj("_id" -> _id), Json.obj("$set" -> Json.obj("viewStat" -> ViewStat(a.viewStat.count + 1, BitmapUtil.toBase64String(viewBitmap)))))
                Ok(views.html.qa.detail(a.copy(viewStat = a.viewStat.copy(count = a.viewStat.count + 1))))
              } else {
                Ok(views.html.qa.detail(a))
              }
            case None =>
              Ok(views.html.qa.detail(a))
          }
        case None => Redirect(routes.Application.notFound)
      }
    }
  }

}
