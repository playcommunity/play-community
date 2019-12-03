package domain.infrastructure.repository.mongo

import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.CorporationRepository
import javax.inject.{Inject, Singleton}
import models.{Corporation, Resource}
import play.api.libs.json.Json.obj
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class MongoCorporationRepository @Inject()(mongo: Mongo) extends CorporationRepository {

  /**
    * 根据Id构建领域实体。
    * @param id 实体标识。
    * @return 领域实体。
    */
  def findById(id: String): Future[Option[Corporation]] = {
    mongo.findById[Corporation](id)
  }

  /**
    * 更新资源
    */
  def update(id: String, update: JsObject): Future[Boolean] = {
    mongo.updateOne[Corporation](Json.obj("_id" -> id), update).map(_.getModifiedCount == 1)
  }

  /**
    * 新增资源
    */
  def add(corporation: Corporation): Future[Boolean] = {
    mongo.insertOne[Corporation](corporation).map{ _ => true}
  }

  /**
    * 查询资源列表, 为提高查询效率，忽略资源内容。
    */
  def findActiveList(sort: JsObject): Future[List[Corporation]] = {
    mongo.find[Corporation](obj("active" -> true)).sort(sort).list()
  }
}
