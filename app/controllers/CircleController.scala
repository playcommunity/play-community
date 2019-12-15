package controllers

import java.time.Instant

import javax.inject._
import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.mongo.{MongoCorporationRepository, MongoLeaderRepository}
import models.{Corporation, Leader, Site, ViewStat, VoteStat}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import services.{CommonService, EventService, LeaderService}
import utils.{AppUtil, DateTimeUtil, RequestHelper}
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json._

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class CircleController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, eventService: EventService,
    corporationRepo: MongoCorporationRepository, leaderRepo: MongoLeaderRepository, leaderService: LeaderService)(implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {

  def corporations(sortBy: String) = Action.async { implicit request: Request[AnyContent] =>
    val sort = sortBy match {
      case "time" => obj("createTime" -> -1)
      case _ => obj("voteStat.count" -> -1)
    }
    corporationRepo.findActiveList(sort).map { list =>
      Ok(views.html.circle.corporation(sortBy, list))
    }
  }

  def doAddCorporation = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(tuple("title" -> nonEmptyText, "city" -> nonEmptyText, "grade" -> nonEmptyText, "link" -> nonEmptyText, "desc" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "您的输入有误！"))),
      tuple => {
        val (title, city, grade, url, description) = tuple
        for {
          _id <- commonService.getNextSequence("corporation")
          _  <- corporationRepo.add(Corporation(RequestHelper.getUid + "-" +  _id.toString, title, city, grade, url, "/assets/images/corporation.jpg", description, RequestHelper.getAuthor, false, VoteStat(0, ""), ViewStat(0, ""), false, DateTimeUtil.now(), DateTimeUtil.now()))
        } yield {
          Ok(Json.obj("status" -> 0, "msg" -> "非常感谢，审核通过后才会展示哦！"))
        }
      }
    )
  }

  def doVoteCorporation = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(single("id" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "invalid args."))),
      id => {
        for {
          Some(c) <- corporationRepo.findById(id)
        } yield {
          val uid = RequestHelper.getUid.toInt
          val voteStat = AppUtil.toggleVote(c.voteStat, uid)

          corporationRepo.update(id, Json.obj("$set" -> Json.obj("voteStat" -> voteStat)))
          Ok(Json.obj("status" -> 0, "count" -> (voteStat.count - c.voteStat.count), "praise" -> voteStat.count))
        }
      }
    )
  }

  def leaders() = Action.async { implicit request: Request[AnyContent] =>
    leaderRepo.findActiveList().map { list =>
      Ok(views.html.circle.leaders(list))
    }
  }

  def doAddLeader = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(tuple("name" -> nonEmptyText, "url" -> nonEmptyText, "desc" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "您的输入有误！"))),
      tuple => {
        val (name, url, description) = tuple
        for {
          _id <- commonService.getNextSequence("leader")
          _  <- leaderRepo.add(Leader(RequestHelper.getUid + "-" +  _id.toString, name, "", description, None, None, Some(Site(url, "", "", "", "", Instant.now(), Instant.now())), VoteStat(0, ""), false, Instant.now(), Instant.now()))
        } yield {
          Ok(Json.obj("status" -> 0, "msg" -> "非常感谢，审核通过后才会展示哦！"))
        }
      }
    )
  }

  def doVoteLeader = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
    Form(single("id" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "invalid args."))),
      id => {
        for {
          Some(c) <- leaderRepo.findById(id)
        } yield {
          val uid = RequestHelper.getUid.toInt
          val voteStat = AppUtil.toggleVote(c.voteStat, uid)

          leaderRepo.update(id, Json.obj("$set" -> Json.obj("voteStat" -> voteStat)))
          Ok(Json.obj("status" -> 0, "count" -> (voteStat.count - c.voteStat.count), "praise" -> voteStat.count))
        }
      }
    )
  }

  def crawlLeader(_id: String) = (checkLogin andThen checkAdmin).async { implicit request: Request[AnyContent] =>
    leaderRepo.findById(_id) flatMap {
      case Some(leader) =>
        leaderService.crawlLeader(leader).map{
          case true => Ok("OK")
          case false => Ok("Fail")
        }
      case None => Future.successful(Ok("Not Found"))
    }
  }
}
