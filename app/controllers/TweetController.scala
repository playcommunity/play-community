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
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import services.{CommonService, EventService}
import utils.{BitmapUtil, DateTimeUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TweetController @Inject()(cc: ControllerComponents, val reactiveMongoApi: ReactiveMongoApi, commonService: CommonService, eventService: EventService) (implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {
  def tweetColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-tweet"))
  def docColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-doc"))
  def categoryColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-category"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  def msgColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-message"))
  def getColFuture(name: String) = reactiveMongoApi.database.map(_.collection[JSONCollection](name))

  def index(nav: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = if(page < 1){1}else{page}
    val sort = if (nav == "0") { Json.obj("createTime" -> -1) } else { Json.obj("voteStat.count" -> -1) }
    for {
      tweetCol <- tweetColFuture
      tweets <- tweetCol.find(Json.obj()).sort(sort).cursor[Tweet]().collect[List](15)
      hotTweets <- tweetCol.find(Json.obj()).sort(Json.obj("voteStat.count" -> -1)).cursor[Tweet]().collect[List](15)
      total <- tweetCol.count(None)
    } yield {
      if (total > 0 && cPage > math.ceil(total/15.0).toInt) {
        Redirect(routes.TweetController.index("0", math.ceil(total/15.0).toInt))
      } else {
        Ok(views.html.tweet.index(nav, Json.toJson(tweets.map(_.toJson)).toString, Json.toJson(hotTweets.map(_.toJson)).toString, cPage, total))
      }
    }
  }

  def getLatestAndHot(count: Int) = Action.async { implicit request: Request[AnyContent] =>
    for {
      tweetCol <- tweetColFuture
      tweets <- tweetCol.find(Json.obj()).sort(Json.obj("createTime" -> -1)).cursor[Tweet]().collect[List](count)
      hotTweets <- tweetCol.find(Json.obj()).sort(Json.obj("voteStat.count" -> -1)).cursor[Tweet]().collect[List](count)
    } yield {
      val js1 = tweets.map(_.toJson)
      val js2 = hotTweets.map(_.toJson)
      Ok(Json.obj("status" -> 0, "tweets" -> js1, "hotTweets" -> js2))
    }
  }

  def doAdd = checkLogin.async { implicit request: Request[AnyContent] =>
    Form(tuple("content" -> nonEmptyText, "images" -> list(nonEmptyText))).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "您的输入有误！"))),
      tuple => {
        val (content, images) = tuple
        for {
          tweetCol <- tweetColFuture
        } yield {
          val _id = RequestHelper.generateId
          eventService.createResource(RequestHelper.getAuthor, _id, "tweet", content)
          val tweet = Tweet(_id, RequestHelper.getAuthor, content, content, images, DateTimeUtil.now(), VoteStat(0, ""), ReplyStat(0, 0, ""), List.empty[Reply])
          tweetCol.insert(tweet)
          Ok(Json.obj("status" -> 0, "tweet" -> tweet.toJson))
        }
      }
    )
  }

  def view(_id: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      tweetCol <- tweetColFuture
      tweetOpt <- tweetCol.find(Json.obj("_id" -> _id)).one[Tweet]
    } yield {
      tweetOpt match {
        case Some(t) =>
          Ok(views.html.tweet.detail(t))
        case None => Redirect(routes.Application.notFound)
      }
    }
  }

}
