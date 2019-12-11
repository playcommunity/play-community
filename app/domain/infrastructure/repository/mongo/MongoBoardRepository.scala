package domain.infrastructure.repository.mongo

import java.time.Instant

import cn.playscala.mongo.Mongo
import cn.playscala.mongo.internal.AsyncResultHelper
import domain.infrastructure.repository.{BoardRepository, TweetRepository}
import javax.inject.Inject
import models.{Board, Category, Resource, StatBoard, StatBoardTraffic, Tweet}
import org.bson.BsonDocument
import play.api.libs.json.{JsObject, Json}
import play.api.libs.json.Json._
import utils.{BoardUtil, DateTimeUtil}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class MongoBoardRepository @Inject()(mongo: Mongo) extends BoardRepository {

  /**
    * 新增版块
    */
  def add(board: Board): Future[Boolean] = {
    mongo.insertOne[Board](board).map{ _ => true}
  }

  def findById(id: String): Future[Option[Board]] = {
    mongo.findById[Board](id)
  }

  /**
    * 根据path查询分类
    */
  def findByPath(boardPath: String): Future[Option[Board]] = {
    mongo.find[Board](Json.obj("path" -> boardPath)).first
  }

  def findAll(): Future[List[Board]] = {
    mongo.find[Board](obj("parentPath" -> "/")).sort(obj("index" -> 1)).list()
  }

  def findTop(count: Int): Future[List[Board]] = {
    mongo.find[Board](obj("parentPath" -> "/")).sort(obj("index" -> 1)).limit(count).list()
  }

  /**
    * 查询阅读量最高的资源列表, 为提高查询效率，忽略资源内容。
    */
  def findTopViewList(boardPath: String, count: Int): Future[List[Resource]] = {
    mongo.find[Resource](obj("categoryPath" -> Json.obj("$regex" -> s"^${boardPath}"), "visible" -> true), obj("content" -> 0)).sort(Json.obj("viewStat.count" -> -1)).limit(count).list()
  }

  def findBoardCategoryList(path: String): Future[List[Category]] = {
    BoardUtil.getBoardPath(path) match {
      case Some(boardPath) =>
        mongo.find[Category](Json.obj("path" -> Json.obj("$regex" -> s"^${boardPath}"))).list()
      case None =>
        Future.successful(Nil)
    }
  }

  /**
   * 记录版块的访客信息
   */
  def recordTraffic(boardPath: String, uid: String): Future[Boolean] = {
    val dayStr = DateTimeUtil.toString(DateTimeUtil.now(), "yyyy-MM-dd")
    mongo.collection[StatBoardTraffic].updateOne(
      obj("boardPath" -> boardPath, "uid" -> uid, "dayStr" -> dayStr),
      obj(
        "$inc" -> obj("count" -> 1),
        "$setOnInsert" -> obj("createTime" -> Instant.now(), "updateTime" -> Instant.now())
      ),
      true
    ).map(_.getModifiedCount == 1)
  }

  def getTodayTraffic(boardPath: String): Future[Long] = {
    val dayStr = DateTimeUtil.toString(DateTimeUtil.now(), "yyyy-MM-dd")
    val it = mongo.collection[StatBoardTraffic].aggregate[BsonDocument](
      Seq(
        obj("$match" -> obj("boardPath" -> boardPath, "dayStr" -> dayStr)),
        obj(
          "$group" -> obj(
            "_id" -> "dayStr",
            "totalCount" -> obj("$sum" -> "$count")
          )
        )
      ))
    AsyncResultHelper.toFuture(it) map { list =>
      list.headOption match {
        case Some(bs) => bs.getInt32("totalCount").getValue
        case None => 0L
      }
    }
  }

  def getTotalTraffic(boardPath: String): Future[Long] = {
    val it = mongo.collection[StatBoardTraffic].aggregate[BsonDocument](
      Seq(
        obj("$match" -> obj("boardPath" -> boardPath)),
        obj(
          "$group" -> obj(
            "_id" -> "dayStr",
            "totalCount" -> obj("$sum" -> "$count")
          )
        )
      ))
    AsyncResultHelper.toFuture(it) map { list =>
      list.headOption match {
        case Some(bs) => bs.getInt32("totalCount").getValue
        case None => 0L
      }
    }
  }

  /**
    *  获取管理版块列表
    */
  def findBoardsByOwnerId(uid: String): Future[List[Board]] = {
    mongo.find[Board](obj("owner._id" -> uid)).list()
  }

  def getFollowers(boardPath: String): Future[Seq[String]] = {
    mongo.find[StatBoard](obj("boardPath" -> boardPath)).first map {
      case Some(sb) => sb.followers.getOrElse(Seq.empty[String])
      case None => Seq.empty[String]
    }
  }

  /**
    * 关注版块
    */
  def followBoard(boardPath: String, uid: String, isFollow: Boolean): Future[Boolean] = {
    val addOrPull = isFollow match {
      case true => "$addToSet"
      case false => "$pull"
    }
    mongo.updateOne[StatBoard](
      obj("boardPath" -> boardPath),
      obj(addOrPull -> obj("followers" -> uid)),
      true
    ).map(_.getModifiedCount == 1)
  }

}
