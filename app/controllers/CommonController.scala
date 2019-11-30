package controllers

import javax.inject._
import cn.playscala.mongo.Mongo
import models.{Author, Corporation, Message, Reply, Resource, User, VoteStat}
import org.bson.types.ObjectId
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.mvc._
import services.{CommonService, EventService, ResourceService}
import utils.{AppUtil, DateTimeUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CommonController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, eventService: EventService, resourceService: ResourceService)(implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {

  def doVote = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(tuple("resId" -> nonEmptyText, "resType" -> nonEmptyText, "resTitle" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "invalid args."))),
      tuple => {
        val (resId, resType, resTitle) = tuple
        val resCol = mongo.collection(AppUtil.getCollectionName(resType))
        for{
          objOpt <- resCol.find(Json.obj("_id" -> resId), Json.obj("voteStat" -> 1, "author" -> 1)).first
        } yield {
          objOpt match {
            case Some(obj) =>
              val voteStat = (obj \ "voteStat").as[VoteStat]
              val authorOpt = (obj \ "author").asOpt[Author]

              val uid = RequestHelper.getUid.toInt
              val newVoteStat = AppUtil.toggleVote(voteStat, uid)
              val count = newVoteStat.count - voteStat.count
              if (count > 0) {
                eventService.voteResource(RequestHelper.getAuthor, resId, resType, resTitle)
              } else {
                eventService.unvoteResource(RequestHelper.getAuthor, resId, resType, resTitle)
              }
              resCol.updateOne(Json.obj("_id" -> resId), Json.obj("$set" -> Json.obj("voteStat" -> newVoteStat)))
              if (authorOpt.nonEmpty) {
                mongo.updateOne[User](Json.obj("_id" -> authorOpt.get._id), Json.obj("$inc" -> Json.obj("stat.votedCount" -> count)))
              }
              Ok(Json.obj("status" -> 0, "count" -> count))
            case None =>
              Ok(Json.obj("status" -> 1, "msg" -> "Resource not found."))
          }
        }
      }
    )
  }

  def doVoteReply = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(tuple("resId" -> nonEmptyText, "resType" -> nonEmptyText)).fill(("user@playscala.cn", "123456"))
    Form(tuple("resId" -> nonEmptyText, "resType" -> nonEmptyText, "resTitle" -> nonEmptyText, "replyId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("success" -> false, "message" -> "invalid args."))),
      tuple => {
        val (resId, resType, resTitle, replyId) = tuple
        val resCol = mongo.collection(AppUtil.getCollectionName(resType))
        for{
          replyOpt <- resCol.find(Json.obj("_id" -> resId)).first
        } yield {
          val reply = (replyOpt.get \ "replyStat" \ "replies").as[List[Reply]].find(_._id == replyId).get
          val uid = RequestHelper.getUid.toInt
          val newVoteStat = AppUtil.toggleVote(reply.voteStat, uid)
          val count = newVoteStat.count - reply.voteStat.count
          resCol.updateOne(Json.obj("_id" -> resId, "replyStat.replies._id" -> replyId), Json.obj("$set" -> Json.obj("replyStat.replies.$.voteStat" -> newVoteStat)))
          mongo.updateOne[User](Json.obj("_id" -> reply.author._id), Json.obj("$inc" -> Json.obj("stat.votedCount" -> count)))
          Ok(Json.obj("status" -> 0, "count" -> 1))
        }
      }
    )
  }

  def doReply = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(tuple("resId" -> nonEmptyText, "resType" -> nonEmptyText, "resTitle" -> nonEmptyText, "content" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (resId, resType, resTitle, content) = tuple
        val atIds = resourceService.parseMentionedUsers(content)
        val reply = Reply(RequestHelper.generateId, content, "quill", RequestHelper.getAuthor, atIds, DateTimeUtil.now(), DateTimeUtil.now(), VoteStat(0, ""), Nil)
        val idObj = resType match {
          case "tweet" => obj("_id" -> resId)
          case _ => obj("_id" -> resId, "closed" -> false)
        }
        mongo
          .collection(AppUtil.getCollectionName(resType))
          .findOneAndUpdate(
            idObj,
            obj(
              "$push" -> Json.obj("replyStat.replies" -> reply),
              "$set" -> Json.obj("replyStat.lastReply" -> reply),
              "$inc" -> obj("replyStat.replyCount" -> 1)
            )
          ).map{
            case Some(obj) =>
              val authorOpt = (obj \ "author").asOpt[Author]

              // 记录回复事件
              eventService.replyResource(RequestHelper.getAuthor, resId, resType, resTitle)

              // 通知楼主
              if (authorOpt.nonEmpty) {
                if (!RequestHelper.getUidOpt.exists(_ == authorOpt.get._id)) {
                  mongo.insertOne[Message](Message(ObjectId.get().toHexString, authorOpt.get._id, resType, resId, resTitle, RequestHelper.getAuthorOpt.get, "reply", content, DateTimeUtil.now(), false))
                }
              }

              // 通知被@用户
              atIds.foreach{ uid =>
                mongo.insertOne[Message](Message(ObjectId.get().toHexString, uid, resType, resId, resTitle, RequestHelper.getAuthorOpt.get, "at", content, DateTimeUtil.now(), false))
              }

              // 用户统计
              mongo.updateOne[User](Json.obj("_id" -> request.session("uid")), Json.obj("$inc" -> Json.obj("stat.replyCount" -> 1), "$set" -> Json.obj("stat.lastReplyTime" -> DateTimeUtil.now())))

              Redirect(s"/${resType}/view?_id=${resId}")

            case None => Ok(views.html.message("系统提示", "很抱歉，您回复的资源不存在！"))
          }
      }
    )
  }


  def editReply(aid: String, rid: String) = checkOwner("rid").async { implicit request: Request[AnyContent] =>
    for {
      reply <- mongo.collection("common-resource").find(Json.obj("_id" -> aid), Json.obj("replyStat.replies" -> Json.obj("$elemMatch" -> Json.obj("_id" -> rid)))).first.map(objOpt => (objOpt.get)("replyStat")("replies")(0).as[Reply])
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
          wr <- mongo.updateOne[Resource](Json.obj("_id" -> aid, "replies._id" -> rid), Json.obj("$set" -> Json.obj("replies.$.content" -> content)))
        } yield {
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  def doRemoveReply = checkAdminOrOwner("rid").async { implicit request: Request[AnyContent] =>
    Form(tuple("resId" -> nonEmptyText, "resType" -> nonEmptyText, "rid" ->nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok("err")),
      tuple => {
        val (resId, resType, rid) = tuple
        val uid = request.session("uid").toInt
        val resCol = mongo.collection(AppUtil.getCollectionName(resType))
        for{
          bestReplyOpt <- resCol.find(Json.obj("_id" -> resId), Json.obj("replyStat.bestReply" -> 1)).first
        } yield {
          val bestReplyId = bestReplyOpt.flatMap(obj => (obj \ "answer" \ "_id").asOpt[String]).getOrElse("")
          if (bestReplyId != rid) {
            resCol.updateOne(Json.obj("_id" -> resId), Json.obj("$pull" -> Json.obj("replyStat.replies" -> Json.obj("_id" -> rid)), "$inc" -> Json.obj("replyStat.replyCount" -> -1)))
            mongo.updateOne[User](Json.obj("_id" -> request.session("uid")), Json.obj("$inc" -> Json.obj("stat.replyCount" -> -1)))
            Ok(Json.obj("status" -> 0))
          } else {
            Ok(Json.obj("status" -> 1, "msg" -> "已采纳回复不允许删除！"))
          }
        }
      }
    )
  }

  def doSetStatus = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(tuple("resId" -> nonEmptyText, "resType" -> nonEmptyText, "field" -> nonEmptyText, "rank" -> boolean)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "输入有误！"))),
      tuple => {
        val (resId, resType, field, status) = tuple
        val resCol = mongo.collection(AppUtil.getCollectionName(resType))
        var isValidField = true
        val modifier = field match {
          case "stick" => Json.obj("$set" -> Json.obj("top" -> status))
          case "closed" => Json.obj("$set" -> Json.obj("closed" -> status))
          case "recommended" => Json.obj("$set" -> Json.obj("recommended" -> status))
          case _ =>
            isValidField = false
            Json.obj()
        }
        if (isValidField) {
          resCol.updateOne(Json.obj("_id" -> resId), modifier).map{ _ => Ok(Json.obj("status" -> 0)) }
        } else {
          Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "Invalid field.")))
        }
      }
    )
  }

}
