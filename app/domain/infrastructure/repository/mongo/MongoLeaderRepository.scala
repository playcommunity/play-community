package domain.infrastructure.repository.mongo

import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.{CorporationRepository, LeaderRepository}
import javax.inject.{Inject, Singleton}
import models.{Board, Corporation, Leader}
import play.api.libs.json.Json.obj
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class MongoLeaderRepository @Inject()(mongo: Mongo) extends LeaderRepository {

  /**
    * 根据Id构建领域实体。
    * @param id 实体标识。
    * @return 领域实体。
    */
  def findById(id: String): Future[Option[Leader]] = {
    mongo.findById[Leader](id)
  }

  /**
    * 更新
    */
  def update(id: String, update: JsObject): Future[Boolean] = {
    mongo.updateOne[Leader](Json.obj("_id" -> id), update).map(_.getModifiedCount == 1)
  }

  /**
    * 新增
    */
  def add(leader: Leader): Future[Boolean] = {
    mongo.insertOne[Leader](leader).map{ _ => true}
  }

  def findAll(): Future[List[Leader]] = {
    mongo.find[Leader]().sort(obj("site.updateTime" -> -1)).list()
  }

  def findActiveList(): Future[List[Leader]] = {
    mongo.find[Leader](obj("active" -> true)).sort(obj("site.updateTime" -> -1)).list()
  }
}
