package controllers

import java.time.Instant

import akka.stream.Materializer
import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.mongo.{MongoCategoryRepository, MongoResourceRepository, MongoUserRepository}
import javax.inject._
import models._
import org.bson.types.ObjectId
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.{tuple, _}
import play.api.libs.json.Json
import play.api.mvc._
import services.{CommonService, EventService, ResourceService}
import utils._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class ResourceController @Inject()(cc: ControllerComponents, mongo: Mongo, resourceController: GridFSController, userAction: UserAction, eventService: EventService, commonService: CommonService, resourceService: ResourceService, resourceRepo: MongoResourceRepository, userRepo: MongoUserRepository, categoryRepo: MongoCategoryRepository)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends AbstractController(cc) {

  // 分页大小
  val PAGE_SIZE = 15

  /**
    * 按分页查看资源
    */
  def index(category: String, resType: String, status: String, page: Int) = Action.async { implicit request: Request[AnyContent] =>
    val cPage = AppUtil.parsePage(page)
    var q = Json.obj("resType" -> resType, "categoryPath" -> Json.obj("$regex" -> s"^${category}"))
    status match {
      case "0" =>
      case "1" =>
        q ++= Json.obj("closed" -> false)
      case "2" =>
        q ++= Json.obj("closed" -> true)
      case "3" =>
        q ++= Json.obj("recommended" -> true)
      case _ =>
    }

    for {
      resources <- resourceRepo.findList(q, Json.obj("createTime" -> -1), (cPage-1) * PAGE_SIZE, PAGE_SIZE)
      topViewResources <- resourceRepo.findTopViewList(resType, 10)
      topReplyResources <- resourceRepo.findTopReplyList(resType, 10)
      total <- mongo.count[Resource](q)
    } yield {
      if (total > 0 && cPage > math.ceil(1.0*total/PAGE_SIZE).toInt) {
        Redirect(s"/${resType}s")
      } else {
        Ok(views.html.resource.index(category, resType, status, resources, topViewResources, topReplyResources, cPage, total.toInt))
      }
    }
  }

  def add(resType: String) = (checkLogin andThen checkActive) { implicit request: Request[AnyContent] =>
    Ok(views.html.resource.edit(resType, None))
  }

  def edit(_id: String) = checkPermissionOnResource("_id").async { implicit request: Request[AnyContent] =>
    resourceRepo.findById(_id) map {
      case Some(r) =>
        Ok(views.html.resource.edit(r.resType, Some(r)))
      case None => Redirect(routes.Application.notFound)
    } recover { case NonFatal(t) =>
      Logger.error(t.getMessage, t)
      Ok(views.html.message("系统提示", "很抱歉，请稍后再试！"))
    }
  }

  /**
    * 新增或更新资源
    */
  def doAdd = (checkPermissionOnResource("_id") andThen userAction).async { implicit request =>
    Form(tuple("_id" -> optional(text), "title" -> nonEmptyText,"content" -> nonEmptyText, "keywords" -> text, "categoryPath" -> nonEmptyText, "resType" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(views.html.message("系统提示", "您的输入有误！"))),
      tuple => {
        val (_idOpt, title, rawContent, keywords, categoryPath, resType) = tuple
        for {
          category <- categoryRepo.findByPath(categoryPath)
          content <- resourceService.process(rawContent)
          success <-  _idOpt match {
            case Some(_id) =>
              eventService.updateResource(RequestHelper.getAuthor, _id, "article", title)
              resourceRepo.update(_id, Json.obj("$set" -> Json.obj(
                "title" -> title,
                "content" -> content,
                "keywords" -> keywords,
                "categoryPath" -> categoryPath,
                "categoryName" -> category.map(_.name).getOrElse[String]("-"),
                "updateTime" -> Instant.now()
              )))
            case None =>
              val _id = RequestHelper.generateId
              val resource = Resource(_id, title, keywords, Some(content), author = RequestHelper.getAuthor, resType = resType, categoryPath = categoryPath, categoryName = category.map(_.name).getOrElse("-"))
              for {
                s1 <- resourceRepo.add(resource)
                s2 <- request.user.incArticleCount(1)
              } yield {
                if(s1 && s2) {
                  eventService.createResource(RequestHelper.getAuthor, _id, "article", title)
                  true
                } else {
                  false
                }
              }
          }
        } yield {
          if(success){
            //Redirect(routes.ResourceController.index(categoryPath, resType, "0", 1))
            Redirect(routes.BoardController.index(categoryPath, resType, "0", 1))
          } else {
            Ok(views.html.message("系统提示", "请稍后再试！"))
          }
        }
      }
    )
  }

  /**
   * 查看资源
   */
  def view(_id: String) = Action.async { implicit request: Request[AnyContent] =>
    for {
      resourceOpt <- resourceRepo.findById(_id)
    } yield {
      resourceOpt match {
        case Some(r) =>
          val requestUid = request.session.get("uid").getOrElse("-1").toInt

          // 帖子访问计数
          var viewStat = r.viewStat
          /*if(requestUid != -1 && !r.hasViewer(requestUid)){
            viewStat = viewStat.copy(count = viewStat.count + 1)
            r.addViewer(requestUid)
          }*/

          viewStat = viewStat.copy(count = viewStat.count + 1)
          r.incViewCount(1)

          // 根据资源类型展示页面
          r.resType match {
            case "exam" => Ok(views.html.resource.exam.detail(r.copy(viewStat = viewStat)))
            case _ => Ok(views.html.resource.detail(r.copy(viewStat = viewStat)))
          }
        case None => Redirect(routes.Application.notFound)
      }
    }
  }

  /**
   * 收藏或取消收藏资源
   */
  def doCollect() = (checkLogin andThen userAction).async { implicit request =>
    Form(single("resId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("success" -> false, "message" -> "invalid args."))),
      resId => {
        val uid = request.session("uid").toInt
        resourceRepo.findById(resId).flatMap{
          case Some(resource) =>
            // 添加收藏
            if(!resource.hasCollector(uid)){
              for {
                s1 <- resource.addCollector(uid)
                s2 <- request.user.collectResource(resource)
              } yield {
                if(s1 && s2) {
                  eventService.collectResource(RequestHelper.getAuthor, resId, resource.resType, resource.title)
                  true
                } else {
                  false
                }
              }
              // 移除收藏
            } else {
              for {
                s1 <- resource.removeCollector(uid)
                s2 <- request.user.uncollectResource(resource)
              } yield if(s1 && s2) true else false
            }
          case None => Future.successful(false)
        }.map{
          case true => Ok(Json.obj("status" -> 0))
          case false => Ok(Json.obj("status" -> 1, "msg" -> "操作失败"))
        }
      }
    )
  }

  /**
    * 删除资源
    */
  def doRemove = (checkPermissionOnResource("_id") andThen userAction).async { implicit request =>
    Form(tuple("resType" -> nonEmptyText,"resId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "输入有误！"))),
      tuple => {
        val (resType, resId) = tuple
        val resCol = mongo.collection(s"common-resource")
        for{
          titleOpt <- resourceRepo.findTitleById(resId)
          r <- resCol.deleteOne(Json.obj("_id" -> resId))
        } yield {
          if(r.getDeletedCount == 1){
            val resTitle = titleOpt.getOrElse("-")
            eventService.removeResource(RequestHelper.getAuthor, resId, resType, resTitle)
            request.user.incArticleCount(-1)
            Ok(Json.obj("status" -> 0))
          } else {
            Ok(Json.obj("status" -> 1, "msg" -> "操作失败"))
          }
        }
      }
    )
  }

  /**
    * 将资源推至首页
    */
  def doPush = checkAdmin.async { implicit request: Request[AnyContent] =>
    Form(tuple("resType" -> nonEmptyText,"resId" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "输入有误！"))),
      tuple => {
        val (resType, resId) = tuple
        val resCol = mongo.collection(s"common-resource")
        for{
          Some(resource) <- resourceRepo.findById(resId)
        } yield {
          val resOwner = resource.author
          val resTitle = resource.title
          commonService.getNextSequence("common-news").map{index =>
            //mongo.insertOne[News](News(index.toString, resTitle, s"/${resType}/view?_id=${resId}", resOwner, resType, Some(false), DateTimeUtil.now()))
          }
          Ok(Json.obj("status" -> 0))
        }
      }
    )
  }

  /**
    * 设置最佳答案
    */
  def doAcceptReply = (checkActive andThen checkAdminOrOwner("_id") andThen userAction).async { implicit request =>
    Form(tuple("_id" -> nonEmptyText, "rid" -> nonEmptyText)).bindFromRequest().fold(
      errForm => Future.successful(Ok(Json.obj("status" -> 1, "msg" -> "弄啥嘞？"))),
      tuple => {
        val (_id, rid) = tuple
        resourceRepo.findById(_id).flatMap{
          case Some(resource) =>
            resource.replyStat.replies.find(_._id == rid) match {
              case Some(reply) =>
                for {
                  s1 <- resource.setBestReply(reply)
                  s2 <- request.user.incScore(10)
                } yield {
                  if(s1 && s2){
                    eventService.acceptReply(RequestHelper.getAuthor, _id, "qa", resource.title)
                    // TODO: 移至MessageRepository实现
                    mongo.insertOne[Message](Message(ObjectId.get.toHexString, reply.author._id, "qa", resource._id, resource.title, RequestHelper.getAuthorOpt.get, "accept", "恭喜您，您的回复已被采纳！", DateTimeUtil.now(), false))
                  }
                  s1 && s2
                }
              case None => Future.successful(false)
            }
          case None => Future.successful(false)
        } map {
          case true => Ok(Json.obj("status" -> 0))
          case false => Ok(Json.obj("status" -> 1, "msg" -> "操作失败"))
        }
      }
    )
  }

  /**
   * 生成sitemap，方便搜索引擎收录
   */
  def sitemap = Action.async {
    resourceRepo.generateSitemapContent().map{ content =>
      Ok(content)
    }
  }

}
