package service

import java.io.{File, FileInputStream}
import javax.inject.{Inject, Singleton}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.{NamedDatabase, ReactiveMongoApi}
import reactivemongo.akkastream.State
import reactivemongo.api.QueryOpts
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import scala.concurrent.{ExecutionContext, Future}
import akka.stream.scaladsl.Source
import pl.allegro.tech.embeddedelasticsearch.{EmbeddedElastic, IndexSettings, PopularProperties}
import reactivemongo.akkastream.{State, cursorProducer}
import java.lang.ClassLoader._
import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit
import models.{Article, IndexedDocument}
import play.api.Environment
import models.JsonFormats.articleFormat

@Singleton
class InitializeService @Inject()(env: Environment,  @NamedDatabase("local") val reactiveMongoApi: ReactiveMongoApi, elasticService: ElasticService)(implicit ec: ExecutionContext, mat: Materializer) {
  def oplogColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("oplog.rs"))

  val elasticSearch = EmbeddedElastic.builder()
    //.withDownloadUrl()
    .withElasticVersion("5.5.0")
    .withSetting(PopularProperties.TRANSPORT_TCP_PORT, 9350)
    .withSetting(PopularProperties.CLUSTER_NAME, "cluster-0")
    //.withPlugin("http://127.0.0.1:8888/elasticsearch-analysis-ik-5.5.0.zip")
    .withIndex("community", IndexSettings.builder().withType("document", new FileInputStream(s"${env.rootPath}${File.separator}conf${File.separator}document-mapping.json")).build())
    .withInstallationDirectory(new File(s"${env.rootPath}${File.separator}embed"))
    .withCleanInstallationDirectoryOnStop(false)
    .withStartTimeout(120, TimeUnit.SECONDS)
    .build()
    .start()

    println("ElasticSearch start on " + elasticSearch.getHttpPort)

  /**
    * Tail oplog.
    */
  oplogColFuture.map{ oplogCol =>
    val source: Source[BSONDocument, Future[State]] = oplogCol.find(Json.obj()).options(QueryOpts().tailable.awaitData.noCursorTimeout).cursor[BSONDocument]().documentSource()
    source.runForeach{ doc =>
      println("oplog: " + BSONDocument.pretty(doc))
      val plate =
        doc.getAs[String]("ns") match {
          case Some("community.common-article") => "article"
          case _ => ""
        }
      if (plate != "") {
        doc.getAs[String]("op") match {
          case Some("i") =>
            val a = doc.getAs[JsObject]("o").get.as[Article]
            elasticService.insert("localhost", 9200, IndexedDocument(a._id, plate, a.title, a.content, a.author.name, a.author._id, a.author.headImg, a.timeStat.createTime.toEpochSecond * 1000, a.viewStat.count, a.replyStat.count, a.voteStat.count, None))
            println("insert " + a)
          case Some("u") =>
            val _id = doc.getAs[JsObject]("o2").get("_id").as[String]
            val modifier: JsObject = doc.getAs[JsObject]("o").get("$set").as[JsObject]
            elasticService.update("localhost", 9200, _id, modifier)
            println("update " + _id)
          case Some("d") =>
            val _id = doc.getAs[JsObject]("o").get("_id").as[String]
            elasticService.remove("localhost", 9200, _id)
            println("remove " + _id)
        }
      }
    }
  }
}
