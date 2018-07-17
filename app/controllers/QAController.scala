package controllers

import javax.inject._

import cn.playscala.mongo.Mongo
import models._
import org.bson.types.ObjectId
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.mvc._
import services.{CommonService, EventService}
import utils.{BitmapUtil, DateTimeUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Json._

@Singleton
class QAController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, eventService: EventService) (implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {

}
