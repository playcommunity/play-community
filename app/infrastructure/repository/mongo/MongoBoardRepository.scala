package infrastructure.repository.mongo

import cn.playscala.mongo.Mongo
import infrastructure.repository.{BoardRepository, TweetRepository}
import javax.inject.Inject
import models.{Board, Tweet}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class MongoBoardRepository @Inject()(mongo: Mongo) extends BoardRepository {

  /**
    * 新增说说
    */
  def add(board: Board): Future[Boolean] = {
    mongo.insertOne[Board](board).map{ _ => true}
  }

  def findById(id: String): Future[Option[Board]] = {
    mongo.findById[Board](id)
  }

}
