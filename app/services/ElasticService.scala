package services

import models.JsonFormats.indexedDocumentFormat
import javax.inject.{Inject, Singleton}
import cn.playscala.mongo.Mongo
import models.IndexedDocument
import play.api.{Configuration, Environment}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.libs.ws.WSClient
import scala.concurrent.duration._
import scala.concurrent.Future

@Singleton
class ElasticService @Inject()(env: Environment, config: Configuration, mongo: Mongo, ws: WSClient) {
  private val esIndexName = config.getOptional[String]("es.esIndexName").getOrElse("community")
  private val useExternalES = config.getOptional[Boolean]("es.useExternalES").getOrElse(false)
  private val esServer = if (useExternalES) { config.getOptional[String]("es.externalESServer").getOrElse("127.0.0.1:9200") } else { "127.0.0.1:9200" }

  def search(q: String, page: Int): Future[(Int, List[IndexedDocument])] = {
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
            "fields" -> Json.obj("title" -> Json.obj(), "content" -> Json.obj())
          ),
          "from" -> (page - 1) * 15,
          "size" -> 15
        )
      } else {
        Json.obj("query" -> Json.obj("match_all" -> Json.obj()))
      }

    ws.url(s"http://${esServer}/${esIndexName}/_search").post(query).map{ resp =>
      val total = (resp.json \ "hits")("total").as[Int]
      val docs =
        (resp.json \ "hits" \ "hits").as[List[JsObject]].map{ hit =>
          val hlTitle = (hit \ "highlight" \ "title").asOpt[List[String]].map(_.mkString("..."))
          val hlContent = (hit \ "highlight" \ "content").asOpt[List[String]].map(_.mkString("..."))
          hit("_source").as[IndexedDocument].copy(hlTitle = hlTitle, hlContent = hlContent)
        }
      (total, docs)
    }
  }

  def insert(doc: IndexedDocument): Future[Boolean] = {
    ws.url(s"http://${esServer}/${esIndexName}/document/${doc.id}").post(Json.toJson(doc)).map{ resp =>
      (resp.json \ "created").asOpt[Boolean].nonEmpty
    }
  }


  def update(_id: String, fragment: JsObject): Future[Boolean] = {
    ws.url(s"http://${esServer}/${esIndexName}/document/${_id}/_update").post(Json.obj("doc" -> fragment)).map{ resp =>
      (resp.json \ "created").asOpt[Boolean].nonEmpty
    }
  }

  def remove(_id: String): Future[Boolean] = {
    ws.url(s"http://${esServer}/${esIndexName}/document/${_id}").delete().map{ resp =>
      (resp.json \ "found").asOpt[Boolean].getOrElse(false)
    }
  }

  def createIndex: Future[Boolean] = {
    val mapping = Json.parse(scala.io.Source.fromFile(env.getFile("conf/document-mapping.json"), "utf-8").getLines().mkString("\n"))
    ws.url(s"http://${esServer}/${esIndexName}").withRequestTimeout(60 seconds).put(Json.obj("mappings" -> mapping))
      .map(r => (r.json \ "acknowledged").asOpt[Boolean].getOrElse(false))
      .fallbackTo(Future.successful(false))
  }

  def existsIndex: Future[Boolean] = {
    ws.url(s"http://${esServer}/${esIndexName}").get().map{ resp =>
    resp.status != 404
    }
  }
}
