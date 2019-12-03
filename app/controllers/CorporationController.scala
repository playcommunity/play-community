package controllers

import javax.inject._
import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.mongo.MongoCorporationRepository
import models.{Corporation, ViewStat, VoteStat}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import services.{CommonService, EventService}
import utils.{AppUtil, DateTimeUtil, RequestHelper}
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json._

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class CorporationController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, eventService: EventService,
                                      corporationRepo: MongoCorporationRepository)(implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {

  def index(sortBy: String) = Action.async { implicit request: Request[AnyContent] =>
    val sort = sortBy match {
      case "time" => obj("createTime" -> -1)
      case _ => obj("voteStat.count" -> -1)
    }
    corporationRepo.findActiveList(sort).map { list =>
      Ok(views.html.corporation.index(sortBy, list))
    }
  }

  def doAdd = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
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

  def doVote = (checkLogin andThen checkActive).async { implicit request: Request[AnyContent] =>
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

}
