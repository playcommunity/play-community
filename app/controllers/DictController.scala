package controllers

import cn.playscala.mongo.Mongo
import javax.inject._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import services.{CommonService, EventService}
import utils.{DateTimeUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DictController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, eventService: EventService)(implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {

  def index = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.dict.index())
  }

  def query(_id: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      opt <- mongo.findById[Word](_id)
      topList <- mongo.collection("common-dict").find[JsObject]().projection(Json.obj("_id" -> 1)).sort(Json.obj("viewCount" -> -1)).limit(10).list()
    } yield {
      Ok(views.html.dict.search(_id, opt, topList.map(obj => (obj \ "_id").as[String])))
    }
  }

}
