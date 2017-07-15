package service

import javax.inject.{Inject, Singleton}

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.libs.ws.WSClient
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.Future

@Singleton
class ElasticService @Inject()(val reactiveMongoApi: ReactiveMongoApi, ws: WSClient) {
  def articleColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-counter"))

  def search(host: String, port: Int, q: String): Future[List[JsObject]] = {
    ws.url(s"http://${host}:${port}/community/_search").post(
      Json.obj(
        "query" -> Json.obj(
           "bool" -> Json.obj("should" -> Json.arr(
              Json.obj("match" -> Json.obj("title" -> Json.obj("query" -> q, "boost" -> 5))),
              Json.obj("match" -> Json.obj("content" -> Json.obj("query" -> q, "boost" -> 1)))
        ))),
        "highlight" -> Json.obj(
          "pre_tags" -> Json.arr(JsString("<b>")),
          "post_tags" -> Json.arr(JsString("</b>")),
          "fields" -> Json.obj("content" -> Json.obj())
        )
      )
    ).map{ resp =>
      val total = resp.json("hits")("total").as[Int]
      resp.json("hits")("hits").as[List[JsObject]].map(_("_source").as[JsObject])
    }
  }

  def index(): Unit = {

  }

}
