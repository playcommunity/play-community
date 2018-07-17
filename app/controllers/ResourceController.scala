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

  def index(resType: String, filter: String, path: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = if(page < 1){1}else{page}
    var q = Json.obj("resType" -> resType, "categoryPath" -> Json.obj("$regex" -> s"^${path}"))
    var sort = Json.obj("timeStat.createTime" -> -1)
    filter match {
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
      resources <- mongo.find[Resource](q).sort(sort).skip((cPage-1) * 15).limit(15).list()
      topViewResources <- mongo.find[Resource]().sort(Json.obj("viewStat.count" -> -1)).limit(10).list()
      topReplyResources <- mongo.find[Resource]().sort(Json.obj("replyCount" -> -1)).limit(10).list()
      total <- mongo.count[Resource]()
    } yield {
      if (total > 0 && cPage > math.ceil(total/15.0).toInt) {
        Redirect(s"/${resType}s")
      } else {
        Ok(views.html.resource.index(resType, filter, path, resources, topViewResources, topReplyResources, cPage, total.toInt))
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


  def doReply = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(tuple("resId" -> nonEmptyText, "resType" -> nonEmptyText, "content" -> nonEmptyText, "at" -> text)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (resId, resType, content, at) = tuple
        val atIds = at.split(",").filter(_.trim != "").toList
        val reply = Reply(RequestHelper.generateId, content, "lay-editor", RequestHelper.getAuthor, atIds, DateTimeUtil.now(), DateTimeUtil.now(), VoteStat(0, ""), Nil)
        mongo.findOneAndUpdate[Resource](
          obj("_id" -> resId),
          obj(
            "$push" -> Json.obj("replies" -> reply),
            "$set" -> Json.obj("lastReply" -> reply),
            "$inc" -> obj("replyCount" -> 1)
          )
        ).map{ case Some(res) =>
          // 记录回复事件
          eventService.replyResource(RequestHelper.getAuthor, resId, resType, res.title)

          // 通知楼主
          if (!RequestHelper.getUidOpt.exists(_ == res.author._id)) {
            mongo.insertOne[Message](Message(ObjectId.get().toHexString, res.author._id, resType, resId, res.title, RequestHelper.getAuthorOpt.get, "reply", content, DateTimeUtil.now(), false))
          }

          // 通知被@用户
          atIds.foreach{ uid =>
            mongo.insertOne[Message](Message(ObjectId.get().toHexString, uid, resType, resId, res.title, RequestHelper.getAuthorOpt.get, "at", content, DateTimeUtil.now(), false))
          }

          // 用户统计
          mongo.updateOne[User](Json.obj("_id" -> request.session("uid")), Json.obj("$inc" -> Json.obj("stat.replyCount" -> 1), "$set" -> Json.obj("stat.lastReplyTime" -> DateTimeUtil.now())))

          Redirect(s"/${resType}/view?_id=${resId}")
        }
      }
    )
  }


  def editReply(aid: String, rid: String) = checkOwner("rid").async { implicit request: Request[AnyContent] =>
    for {
      reply <- mongo.collection("common-article").find(Json.obj("_id" -> aid), Json.obj("replies" -> Json.obj("$elemMatch" -> Json.obj("_id" -> rid)))).first.map(objOpt => (objOpt.get)("replies")(0).as[Reply])
    } yield {
      Ok(Json.obj("status" -> 0, "rows" -> Json.obj("content" -> reply.content)))
    }
  }

  def doEditReply = checkOwner("rid").async { implicit request: Request[AnyContent] =>
    Form(tuple("aid" -> nonEmptyText, "rid" ->nonEmptyText, "content" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (aid, rid, content) = tuple
        val reply = Reply(ObjectId.get().toHexString, content, "lay-editor", RequestHelper.getAuthor, Nil, DateTimeUtil.now(), DateTimeUtil.now(), VoteStat(0, ""), Nil)
        val uid = request.session("uid").toInt
        for{
          wr <- mongo.updateOne[Article](Json.obj("_id" -> aid, "replies._id" -> rid), Json.obj("$set" -> Json.obj("replies.$.content" -> content)))
        } yield {
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def doRemoveReply = checkAdminOrOwner("rid").async { implicit request: Request[AnyContent] =>
    Form(tuple("id" -> nonEmptyText, "rid" ->nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (resId, rid) = tuple
        val uid = request.session("uid").toInt
        val resCol = mongo.collection[Resource]
        for{
          answerObjOpt <- resCol.find(Json.obj("_id" -> resId), Json.obj("answer" -> 1)).first
        } yield {
          val answerId = answerObjOpt.flatMap(obj => (obj \ "answer" \ "_id").asOpt[String]).getOrElse("")
          if (answerId != rid) {
            resCol.updateOne(Json.obj("_id" -> resId), Json.obj("$pull" -> Json.obj("replies" -> Json.obj("_id" -> rid)), "$inc" -> Json.obj("replyCount" -> -1)))
            mongo.updateOne[User](Json.obj("_id" -> request.session("uid")), Json.obj("$inc" -> Json.obj("stat.replyCount" -> -1)))
            Ok(Json.obj("status" -> 0))
          } else {
            Ok(Json.obj("status" -> 1, "msg" -> "已采纳回复不允许删除！"))
          }
        }
      }
    )
  }

  def doVoteReply = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(tuple("resId" -> nonEmptyText, "replyId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("success" -> false, "message" -> "invalid args."))),
      tuple => {
        val (resId, replyId) = tuple
        val resCol = mongo.collection[Resource]
        for{
          reply <- resCol.find(Json.obj("_id" -> resId), Json.obj("replies" -> Json.obj("$elemMatch" -> Json.obj("_id" -> replyId)))).first.map(objOpt => (objOpt.get \ "replies")(0).as[Reply])
        } yield {
          val uid = RequestHelper.getUid.toInt
          val newVoteStat = AppUtil.toggleVote(reply.voteStat, uid)
          val count = newVoteStat.count - reply.voteStat.count
          resCol.updateOne(Json.obj("_id" -> resId, "replies._id" -> replyId), Json.obj("$set" -> Json.obj("replies.$.voteStat" -> newVoteStat)))
          mongo.updateOne[User](Json.obj("_id" -> RequestHelper.getUid), Json.obj("$inc" -> Json.obj("stat.voteCount" -> count)))
          mongo.updateOne[User](Json.obj("_id" -> resId.split("-")(0)), Json.obj("$inc" -> Json.obj("stat.votedCount" -> count)))
          Ok(Json.obj("status" -> 0, "count" -> 1))
        }
      }
    )
  }

  def doVote = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(single("resId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "invalid args."))),
      resId => {
        val resCol = mongo.collection[Resource]
        for{
          objOpt <- resCol.find(Json.obj("_id" -> resId), Json.obj("voteStat" -> 1, "title" -> 1, "resType" -> 1)).first
        } yield {
          val voteStat = (objOpt.get \ "voteStat").as[VoteStat]
          val resTitle = (objOpt.get \ "title").as[String]
          val resType = (objOpt.get \ "resType").as[String]

          val uid = RequestHelper.getUid.toInt
          val newVoteStat = AppUtil.toggleVote(voteStat, uid)
          val count = newVoteStat.count - voteStat.count
          if (count > 0) {
            eventService.voteResource(RequestHelper.getAuthor, resId, resType, resTitle)
          }
          resCol.updateOne(Json.obj("_id" -> resId), Json.obj("$set" -> Json.obj("voteStat" -> newVoteStat)))
          mongo.updateOne[User](Json.obj("_id" -> RequestHelper.getUid), Json.obj("$inc" -> Json.obj("stat.voteCount" -> count)))
          mongo.updateOne[User](Json.obj("_id" -> resId.split("-")(0)), Json.obj("$inc" -> Json.obj("stat.votedCount" -> count)))
          Ok(Json.obj("status" -> 0, "count" -> 1))
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

  def doSetStatus = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(tuple("id" -> nonEmptyText, "field" -> nonEmptyText, "rank" -> boolean)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "输入有误！"))),
      tuple => {
        val (_id, field, status) = tuple
        val modifier = field match {
          case "stick" => Json.obj("$set" -> Json.obj("top" -> status))
          case _ => Json.obj("$set" -> Json.obj("recommended" -> status))
        }
        for{
          wr <- mongo.updateOne[Article](Json.obj("_id" -> _id), modifier)
        } yield {
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
