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
