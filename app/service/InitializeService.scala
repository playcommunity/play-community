package service

import java.io.{File, FileInputStream}
import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.Json
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

import play.api.Environment

@Singleton
class InitializeService @Inject()(env: Environment,  @NamedDatabase("local") val reactiveMongoApi: ReactiveMongoApi)(implicit ec: ExecutionContext, mat: Materializer) {
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
  /*oplogColFuture.map{ oplogCol =>
    val source: Source[BSONDocument, Future[State]] = oplogCol.find(Json.obj()).options(QueryOpts().tailable.awaitData.noCursorTimeout).cursor[BSONDocument]().documentSource()
    source.runForeach(doc =>
      println(BSONDocument.pretty(doc))
    )
  }*/
}
