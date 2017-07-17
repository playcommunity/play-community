package services

import models.JsonFormats.indexedDocumentFormat
import javax.inject.{Inject, Singleton}

import models.IndexedDocument
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

  def search(host: String, port: Int, q: String): Future[(Int, List[IndexedDocument])] = {
    val query =
      if (q.trim != "") {
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
      } else {
        Json.obj("query" -> Json.obj("match_all" -> Json.obj()))
      }

    ws.url(s"http://${host}:${port}/community/_search").post(query).map{ resp =>
      println(resp.json)
      val total = resp.json("hits")("total").as[Int]
      val docs =
        resp.json("hits")("hits").as[List[JsObject]].map{ hit =>
          (hit \ "highlight").asOpt[JsObject].map(_("content").head.as[String]) match {
            case Some(h) => hit("_source").as[IndexedDocument].copy(highlight = Some(h))
            case None =>  hit("_source").as[IndexedDocument]
          }
        }
      (total, docs)
    }
  }

  def insert(host: String, port: Int, doc: IndexedDocument): Future[Boolean] = {
    ws.url(s"http://${host}:${port}/community/document/${doc.id}").post(Json.toJson(doc)).map{ resp =>
      println("insert resp:" + resp.json)
      resp.json("created").asOpt[Boolean].nonEmpty
    }
  }

  def update(host: String, port: Int, _id: String, fragment: JsObject): Future[Boolean] = {
    ws.url(s"http://${host}:${port}/community/document/${_id}/_update").post(Json.obj("doc" -> fragment)).map{ resp =>
      println("update resp: " + resp.json)
      resp.json("created").asOpt[Boolean].nonEmpty
    }
  }

  def remove(host: String, port: Int, _id: String): Future[Boolean] = {
    ws.url(s"http://${host}:${port}/community/document/${_id}").delete().map{ resp =>
      resp.json("found").asOpt[Boolean].getOrElse(false)
    }
  }
}
