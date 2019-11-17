package infrastructure.repository.mongo

import java.time.Instant

import cn.playscala.mongo.Mongo
import infrastructure.repository.{BoardRepository, TweetRepository}
import javax.inject.Inject
import models.{Board, Category, StatBoardTraffic, Tweet}
import play.api.libs.json.Json
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
  def findByPath(path: String): Future[Option[Board]] = {
    mongo.find[Board](Json.obj("path" -> path)).first
  }

  def findAll(): Future[List[Board]] = {
    mongo.find[Board](obj("parentPath" -> "/")).sort(obj("index" -> 1)).list()
  }

  def findTop(count: Int): Future[List[Board]] = {
    mongo.find[Board](obj("parentPath" -> "/")).sort(obj("index" -> 1)).limit(count).list()
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

}