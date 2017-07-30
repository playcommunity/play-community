package controllers.admin

import java.io.ByteArrayOutputStream
import javax.inject._

import akka.stream.Materializer
import akka.util.ByteString
import models.JsonFormats._
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.QueryOpts
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import services.{CounterService, ElasticService, MailerService}
import utils.{DateTimeUtil, HashUtil, UserHelper, VerifyCodeUtils}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random


@Singleton
class AdminController @Inject()(cc: ControllerComponents, val reactiveMongoApi: ReactiveMongoApi, counterService: CounterService, elasticService: ElasticService, mailer: MailerService)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def articleColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-article"))
  def oplogColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("oplog.rs"))

  def index = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.admin.index())
  }

}
