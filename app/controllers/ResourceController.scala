package controllers

import java.io.File
import java.nio.ByteBuffer
import java.util
import javax.inject._

import akka.stream.Materializer
import models.JsonFormats.userFormat
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc._
import play.modules.reactivemongo.MongoController.JsReadFile
import play.modules.reactivemongo.{JSONFileToSave, MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.gridfs.{GridFS, ReadFile}
import MongoController.readFileReads
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cn.playscala.mongo.{Mongo, MongoClient, MongoDatabase}
import com.mongodb.async.client.{AggregateIterable, MongoClients}
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{Await, ExecutionContext, Future}
import cn.playscala.mongo.codecs.Macros._
import cn.playscala.mongo.gridfs.GridFSUploadOptions
import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.MongoClients
import org.bson.codecs.BsonDocumentCodec
import org.bson.{BsonDocument, BsonString, Document}
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}

@Singleton
class ResourceController @Inject()(val mongo: Mongo, val reactiveMongoApi: ReactiveMongoApi)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends MongoController with ReactiveMongoComponents {
  def robotColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-robot"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))

  val codecRegistry = fromRegistries(fromProviders(classOf[Channel]), MongoClient.DEFAULT_CODEC_REGISTRY, MongoClients.getDefaultCodecRegistry)

  val mongodb: MongoDatabase = mongo.database.withCodecRegistry(codecRegistry)

  val mongodb2: MongoDatabase = mongo.database

  val gridFS = reactiveMongoApi.gridFS
  /*val fsParserAndSaver =
    gridFSBodyParser(
      reactiveMongoApi.asyncGridFS,
      (filename:String, contentType: Option[String]) => JSONFileToSave(JsString(filename), contentType, None, Json.obj(), JsString(""))
    )*/

  def testMongo = Action.async {
    /*testCol.fetch[TestRole]("roleId").foreach{ list =>
      println("Find " + list.size)
      list.foreach(println _)
    }*/

    //testCol.find[TestUser3](Json.obj("_id" -> "-1")).map{ list =>
    /*testCol.find[User]().limit(5).list().map { list =>
      Ok(list.toString)
    }*/

    /*import com.mongodb.Block
    val printBlock = new Block[Document]() {
      override def apply(document: Document): Unit = {
        println(document.toJson)
      }
    }

    import com.mongodb.async.SingleResultCallback
    val callbackWhenFinished = new SingleResultCallback[Void]() {
      override def onResult(result: Void, t: Throwable): Unit = {
        if(t != null) {
          println(t.getMessage)
          t.printStackTrace()
        }
        System.out.println("Operation Finished!")
      }
    }

    val it =
      testCol.wrapped.aggregate(util.Arrays.asList(
        new Document("$lookup",
              new Document("from", "role")
                   .append("localField", "roleId")
                   .append("foreignField", "_id")
                   .append("as", "role")
        ),
        new Document("$unwind", "$role")
      ), classOf[BsonDocument])

    it.forEach(new Block[BsonDocument]() {
      override def apply(document: BsonDocument): Unit = {
        import org.bson.BsonBinaryWriter
        import org.bson.BsonWriter
        import org.bson.codecs.EncoderContext
        import org.bson.io.BasicOutputBuffer
        import org.bson.io.OutputBuffer


        val buffer = new BasicOutputBuffer
        val writer = new BsonBinaryWriter(buffer)
        new BsonDocumentCodec().encode(writer, document.getDocument("role"), EncoderContext.builder.build)


        import org.bson.BsonBinaryReader
        import org.bson.ByteBufNIO
        import org.bson.codecs.DecoderContext
        import org.bson.io.ByteBufferBsonInput
        import java.nio.ByteBuffer
        val reader = new BsonBinaryReader(new ByteBufferBsonInput(new ByteBufNIO(ByteBuffer.wrap(buffer.toByteArray))))
        val role = mongodb2.codecRegistry.get(classOf[TestRole]).decode(reader, DecoderContext.builder.build)

        println(role)
        println(document.toJson)
      }
    }, callbackWhenFinished)*/

    /*val downloadStream = mongo.gridFSBucket.openDownloadStream("5ab4fe322c3ba50ed077c215")
    downloadStream.getGridFSFile.map{ f =>
      Ok.chunked(downloadStream.toSource)
        .as(f.getContentType)
    }*/


    /*mongo.gridFSBucket.uploadFromFile(new File("D:\\开发资料\\常用图片\\后台Logo.png"), "image/png").map{ str =>
      println(str)
    }*/

    Future.successful(Ok("Finish."))
  }

  def saveResource(ownerId: String) = checkLogin.async(gridFSBodyParser(gridFS)) { request =>
    // here is the future file!
    val futureFile = request.body.files.head.ref

    // when the upload is complete, we add the owner id to the file entry (in order to find the attachments of the owner)
    for {
      file <- futureFile
      wr <- gridFS.files.update(Json.obj("_id" -> file.id), Json.obj("$set" -> Json.obj("owner" -> ownerId, "uid" -> request.session("uid"))))
    } yield {
      if(wr.ok && wr.n == 1){
        Ok(Json.obj("success" -> true, "url" -> s"/resource/${file.id.as[String]}"))
      } else {
        Ok(Json.obj("success" -> false, "message" -> "Upload failed."))
      }
    }
  }

  def getResource(rid: String) = Action.async { implicit request: Request[AnyContent] =>
    val file = gridFS.find[JsObject, JsReadFile[JsString]](Json.obj("_id" -> rid))
    request.getQueryString("inline") match {
      case Some("true") =>
        serve[JsString, JsReadFile[JsString]](gridFS)(file, CONTENT_DISPOSITION_INLINE).map{ result =>
          result.withHeaders("Cache-Control" -> s"max-age=${31 * 86400}")
        }
      case _            =>
        serve[JsString, JsReadFile[JsString]](gridFS)(file).map{ result =>
          result.withHeaders("Cache-Control" -> s"max-age=${31 * 86400}")
        }
    }
  }

  def removeResource(rid: String) = checkAdmin.async {
    gridFS.remove(JsString(rid)).map{ wr =>
      if (wr.ok && wr.n == 1) {
        Ok(Json.obj("success" -> true))
      } else {
        Ok(Json.obj("success" -> false))
      }
    }
  }

  def ueditorGet(action: String) = Action.async { request =>
    action match{
      case "config" =>
        Future.successful(TemporaryRedirect("/assets/plugins/ueditor/config.json"))
      case "listimage" =>
        gridFS.find[JsObject, JsReadFile[JsString]](Json.obj("resType" -> "ueditor")).collect[List](100).map{ list =>
          val urls = list.map{ i => Json.obj("url" -> s"/resource/${i.id.as[String]}", "mtime" -> 0)}

          Ok(Json.obj("state" -> "SUCCESS", "start" -> "0", "total" -> list.size, "list" -> urls))
        }
      case _ =>
        Future.successful(Ok("unknown action"))
    }
  }

  def ueditorPost(action: String) = Action.async(gridFSBodyParser(gridFS)) { request =>
    action match{
      case action if action == "uploadimage" || action == "uploadvideo" || action == "uploadfile" =>
        for {
          file  <- request.body.files.head.ref
          ur    <- gridFS.files.update(Json.obj("_id" -> file.id), Json.obj("$set" -> Json.obj("resType" -> "ueditor", "action" -> action)))
        } yield {
          val url = s"/resource/${file.id.as[String]}"
          Ok(Json.obj("original" -> file.filename.getOrElse[String]("file"), "name" -> file.filename.getOrElse[String]("file"), "url" -> url, "type" -> ".", "state" -> "SUCCESS"))
        }

      case _ =>
        Future.successful(Ok("unknown action"))
    }
  }

}
