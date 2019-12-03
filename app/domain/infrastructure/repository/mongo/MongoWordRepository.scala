package domain.infrastructure.repository.mongo

import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.WordRepository
import javax.inject.{Inject, Singleton}
import models.Word
import play.api.libs.json.Json.obj
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Word资源库
 */
@Singleton
class MongoWordRepository @Inject()(mongo: Mongo) extends WordRepository {

  /**
   * 根据Id构建领域实体。
   * @param id 实体标识。
   * @return 领域实体。
   */
  def findById(id: String): Future[Option[Word]] = {
    mongo.findById[Word](id)
  }

  /**
   * 新增资源
   */
  def add(word: Word): Future[Boolean] = {
    mongo.insertOne[Word](word).map { _ =>
      true
    }
  }

  /**
   * 更新资源
   */
  def update(id: String, update: JsObject): Future[Boolean] = {
    mongo.updateOne[Word](Json.obj("_id" -> id), update).map(_.getModifiedCount == 1)
  }

  /**
   * 查询审核的资源
   *
   * @param count 分页大小
   * @return
   */
  def findReviewedList(count: Int, isReviewed: Boolean = false): Future[List[Word]] = {
    mongo.find[Word](obj("isReviewed" -> isReviewed)).limit(count).list()
  }

  /**
   * 删除资源
   * @param id
   */
  def deleteById(id: String): Future[Boolean] = {
    mongo.deleteById[Word](id).map(_.getDeletedCount == 1)
  }
}
