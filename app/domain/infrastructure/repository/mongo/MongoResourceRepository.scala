package domain.infrastructure.repository.mongo

import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.ResourceRepository
import javax.inject.{ Inject, Singleton }
import models.{ Resource, StatCollect }
import play.api.libs.json.Json.obj
import play.api.libs.json.{ JsObject, Json }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Resource资源库
 */
@Singleton
class MongoResourceRepository @Inject()(mongo: Mongo) extends ResourceRepository {

  /**
   * 新增资源
   */
  def add(resource: Resource): Future[Boolean] = {
    mongo.insertOne[Resource](resource).map { _ => true }
  }

  /**
   * 更新资源
   */
  def update(id: String, update: JsObject): Future[Boolean] = {
    println(update)
    mongo.updateOne[Resource](Json.obj("_id" -> id), update).map(_.getModifiedCount == 1)

  }

  /**
   * 根据Id构建领域实体。
   *
   * @param id 实体标识。
   * @return 领域实体。
   */
  override def findById(id: String): Future[Option[Resource]] = {
    mongo.findById[Resource](id)
  }

  /**
    * 根据Id构建领域实体，忽略资源正文。
    * @param id 实体标识。
    * @return 领域实体。
    */
  def findContentlessById(id: String): Future[Option[Resource]] = {
    mongo.find[Resource](obj("_id" -> id), obj("content" -> 0)).first
  }

  /**
   * 根据Id查询标题。
   *
   * @param id 实体标识。
   * @return 资源标题。
   */
  def findTitleById(id: String): Future[Option[String]] = {
    mongo.collection[Resource].find(Json.obj("_id" -> id), Json.obj("title" -> 1)).first.map { jsOpt =>
      jsOpt.map(js => (js \ "title").as[String])
    }
  }

  /**
   * 根据Id查询JsObject。
   *
   * @param id 实体标识。
   * @return JsObject。
   */
  def findJsObjectById(id: String, projection: JsObject): Future[Option[JsObject]] = {
    mongo.collection[Resource].find(Json.obj("_id" -> id), projection).first
  }

  /**
   * 查询资源列表, 为提高查询效率，忽略资源内容。
   */
  def findList(query: JsObject, sort: JsObject, skip: Int, limit: Int): Future[List[Resource]] = {
    mongo.find[Resource](query, obj("content" -> 0)).sort(sort).skip(skip).limit(limit).list()
  }

  /**
   * 查询置顶的资源列表, 为提高查询效率，忽略资源内容。
   */
  def findTopList(count: Int): Future[List[Resource]] = {
    mongo.find[Resource](obj("top" -> true, "visible" -> true)).sort(obj("createTime" -> -1)).limit(count).list()
  }

  /**
   * 查询阅读量最高的资源列表, 为提高查询效率，忽略资源内容。
   */
  def findTopViewList(count: Int): Future[List[Resource]] = {
    mongo.find[Resource](obj("visible" -> true), obj("content" -> 0)).sort(Json.obj("viewStat.count" -> -1)).limit(count).list()
  }

  /**
   * 查询阅读量最高的资源列表, 为提高查询效率，忽略资源内容。
   */
  def findTopViewList(resType: String, count: Int): Future[List[Resource]] = {
    mongo.find[Resource](obj("resType" -> resType, "visible" -> true), obj("content" -> 0)).sort(Json.obj("viewStat.count" -> -1)).limit(count).list()
  }

  /**
   * 查询回复量最高的资源列表, 为提高查询效率，忽略资源内容。
   */
  def findTopReplyList(resType: String, count: Int): Future[List[Resource]] = {
    mongo.find[Resource](obj("resType" -> resType, "visible" -> true), obj("content" -> 0)).sort(Json.obj("replyCount" -> -1)).limit(count).list()
  }

  /**
   * 查询资源总数。
   */
  def count(resType: String): Future[Long] = {
    mongo.collection[Resource].count(obj("resType" -> resType, "visible" -> true))
  }

  /**
   * 查询资源总数。
   */
  def count(query: JsObject): Future[Long] = {
    mongo.collection[Resource].count(query)
  }

  /**
   * 生成sitemap内容
   */
  def generateSitemapContent(): Future[String] = {
    mongo.collection("common-resource").find[JsObject]().projection(obj("_id" -> 1, "resType" -> 1)).list().map { list =>
      val urls: List[String] = list map { obj =>
        val _id = (obj \ "_id").as[String]
        val resType = (obj \ "resType").as[String]
        s"${app.Global.homeUrl}/${resType}/view?_id=${_id}"
      }

      urls.mkString("\r\n")
    }
  }

  /**
   * 根据资源的查询条件和排序条件，分页大小来查询
   *
   * @param sortBy Json.obj对象
   * @param count  分页大小
   * @param findBy Json.obj对象
   * @return
   */
  def findResourceBy(sortBy: JsObject, count: Int, findBy: JsObject) = {
    mongo.find[Resource](sortBy).sort(sortBy).limit(count).list
  }

  def countResourceBy(countBys: JsObject) = {
    mongo.collection[Resource].count(countBys)
  }

  def findStatBy(sortBy: JsObject, count: Int, findBy: JsObject) = {
    mongo.find[StatCollect](findBy).sort(sortBy).limit(count).list
  }

  def countStatBy(countBys: JsObject) = {
    mongo.collection[StatCollect].count(countBys)
  }
}