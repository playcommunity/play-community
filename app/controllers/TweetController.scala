package controllers

import javax.inject._
import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.mongo.MongoTweetRepository
import models._
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.mvc._
import services.{CommonService, EventService}
import utils.{DateTimeUtil, RequestHelper}

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Json._

@Singleton
class TweetController @Inject()(cc: ControllerComponents, mongo: Mongo, commonService: CommonService, eventService: EventService,tweetRepo:MongoTweetRepository) (implicit ec: ExecutionContext, parser: BodyParsers.Default) extends AbstractController(cc) {

  // 分页大小
  val PAGE_SIZE = 15

  def index(nav: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = if(page < 1){1}else{page}
    val sort = if (nav == "0") { Json.obj("createTime" -> -1) } else { Json.obj("voteStat.count" -> -1) }
    for {
      tweets <- tweetRepo.findList(sort ,(cPage-1) * PAGE_SIZE, PAGE_SIZE)
      hotTweets <- tweetRepo.findHotList(PAGE_SIZE)
      total <- tweetRepo.count()
    } yield {
      if (total > 0 && cPage > math.ceil(1.0*total/PAGE_SIZE).toInt) {
        Redirect(routes.TweetController.index("0", math.ceil(1.0*total/PAGE_SIZE).toInt))
      } else {
        Ok(views.html.tweet.index(nav, Json.toJson(tweets.map(_.toJson)).toString, Json.toJson(hotTweets.map(_.toJson)).toString, cPage, total.toInt))
      }
    }
  }

  def getLatestAndHot(count: Int) = Action.async { implicit request: Request[AnyContent] =>
    for {
      tweets <- tweetRepo.findLatestList(count)
      hotTweets <- tweetRepo.findHotList(count)
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
        val _id = RequestHelper.generateId
        eventService.createResource(RequestHelper.getAuthor, _id, "tweet", content)
        val tweet = Tweet(_id, RequestHelper.getAuthor, content, content, images, DateTimeUtil.now(), VoteStat(0, ""))
        tweetRepo.add(tweet).map{ _ =>
          Ok(Json.obj("status" -> 0, "tweet" -> tweet.toJson))
        }
      }
    )
  }

  /**
    * 删除资源
    */
  def doRemove(_id: String) = checkAdminOrOwner("_id").async { implicit request =>
    for{
      r <- tweetRepo.delete(_id)
    } yield {
      if(r.getDeletedCount == 1){
        Redirect(routes.TweetController.index())
      } else {
        Ok(views.html.message("系统提示", "操作失败!"))
      }
    }
  }

  def view(_id: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      tweetOpt <- tweetRepo.findById(_id)
    } yield {
      tweetOpt match {
        case Some(t) =>
          Ok(views.html.tweet.detail(t))
        case None => Redirect(routes.Application.notFound)
      }
    }
  }

}
