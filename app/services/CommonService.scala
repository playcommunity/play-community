package services

import javax.inject.{Inject, Singleton}
import cn.playscala.mongo.Mongo
import com.mongodb.client.model.FindOneAndUpdateOptions
import models.Word
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json.obj

@Singleton
class CommonService @Inject()(mongo: Mongo) {
  val counterCol = mongo.collection("common-counter")
  private val dictCol = mongo.collection("common-dict")
  private val dictQueryCol = mongo.collection("dict-query")

  def getNextSequence(name: String): Future[Int] = {
    counterCol.findOneAndUpdate(
      Json.obj("_id" -> name),
      Json.obj("$inc" -> Json.obj("value" -> 1)),
      new FindOneAndUpdateOptions().upsert(true)
    ).map{
      case Some(obj) =>
        (obj \ "value").as[Int]
      case None => 0
    }
  }

  /**
  * 查询资源
   * @param q 查询条件
   * @param projection
   * @param sort 排序
   * @param count 分页
   * @return
   */
  def getObjListByDictCol(q: JsObject, projection: Option[JsObject] = None, sort: JsObject, count: Int): Future[List[JsObject]] = {
    projection.map(dictCol.find[JsObject](q).projection(_).sort(sort).limit(count).list()).getOrElse(dictCol.find[JsObject](q).sort(sort).limit(count).list())
  }

  def getWordListByDictCol(q: JsObject, projection: Option[JsObject] = None, sort: JsObject, count: Int): Future[List[Word]] = {
    projection.map(dictCol.find[Word](q).projection(_).sort(sort).limit(count).list()).getOrElse(dictCol.find[Word](q).sort(sort).limit(count).list())
  }


  /**
  * 更新单条数据
   * @param id
   * @param fields
   * @return
   */
  def updateOneByDictQuery(id: String, fields: JsObject): Future[Boolean] = {
    dictQueryCol.updateOne(
      obj("_id" -> id),
      fields,
      true
    ).map(_.getModifiedCount == 1)
  }
}