package controllers

import javax.inject._

import akka.stream.Materializer
import models.JsonFormats.userFormat
import models.User
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc._
import play.modules.reactivemongo.MongoController.JsReadFile
import play.modules.reactivemongo.{JSONFileToSave, MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.gridfs.{GridFS, ReadFile}
import MongoController.readFileReads
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ResourceController @Inject()(val reactiveMongoApi: ReactiveMongoApi)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default) extends MongoController with ReactiveMongoComponents {
  def robotColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-robot"))
  def userColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-user"))
  val gridFS = reactiveMongoApi.gridFS
  /*val fsParserAndSaver =
    gridFSBodyParser(
      reactiveMongoApi.asyncGridFS,
      (filename:String, contentType: Option[String]) => JSONFileToSave(JsString(filename), contentType, None, Json.obj(), JsString(""))
    )*/


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
      case Some("true") => serve[JsString, JsReadFile[JsString]](gridFS)(file, CONTENT_DISPOSITION_INLINE)
      case _            => serve[JsString, JsReadFile[JsString]](gridFS)(file)
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

}
