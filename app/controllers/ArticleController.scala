package controllers

import java.time.Instant
import javax.inject._
import models._
import play.api.libs.json.Json
import play.api.mvc._
import utils.{BitmapUtil, RequestHelper}
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import scala.concurrent.{ExecutionContext, Future}
import cn.playscala.mongo.Mongo
import services.EventService

@Singleton
class ArticleController @Inject()(cc: ControllerComponents, mongo: Mongo, eventService: EventService) (implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {


}
