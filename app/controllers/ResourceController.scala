package controllers

import javax.inject._
import akka.stream.Materializer
import play.api.libs.json.Json
import play.api.mvc._
import cn.playscala.mongo.Mongo
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Json._

@Singleton
class ResourceController @Inject()(cc: ControllerComponents, mongo: Mongo)(implicit ec: ExecutionContext, mat: Materializer, parser: BodyParsers.Default)  extends AbstractController(cc) {

  def testMongo = Action.async {
    Future.successful(Ok("Finish."))
  }

  def saveResource(ownerId: String) = checkLogin.async { request =>
    request.body.asMultipartFormData.get.files.headOption match {
      case Some(filePart) =>
        mongo.gridFSBucket.uploadFromFile(filePart.filename, filePart.contentType.getOrElse("application/octet-stream"), filePart.ref.toFile).map{ fileId =>
          Ok(Json.obj("success" -> true, "url" -> s"/resource/${fileId}"))
        }
      case None =>
        Future.successful(Ok(Json.obj("success" -> false, "message" -> "No files found.")))
    }
  }

  def getResource(rid: String, inline: String) = Action.async { implicit request: Request[AnyContent] =>
    val inlineStr = if (inline.toLowerCase == "true") { "inline"} else { "attachment" }
    mongo.gridFSBucket.findById(rid).map{
      case Some(file) =>
        Ok.chunked(file.stream.toSource)
          .as(file.getContentType)
          .withHeaders("Content-Disposition" -> s"""${inlineStr}; filename="${file.getFilename}"""")
      case None =>
        NotFound
    }
  }

  def removeResource(rid: String) = checkAdmin.async {
    mongo.gridFSBucket.deleteById(rid).map{ dr =>
      Ok(Json.obj("success" -> true))
    }
  }

  def ueditorGet(action: String) = Action.async { request =>
    action match{
      case "config" =>
        Future.successful(TemporaryRedirect("/assets/plugins/ueditor/config.json"))
      case "listimage" =>
        Future.successful{
          Ok(Json.obj("state" -> "SUCCESS", "start" -> "0", "total" -> 0, "list" -> Json.arr()))
        }
      case _ =>
        Future.successful(Ok("unknown action"))
    }
  }

  def ueditorPost(action: String) = Action.async { request =>
    action match{
      case action if action == "uploadimage" || action == "uploadvideo" || action == "uploadfile" =>
        request.body.asMultipartFormData.get.files.headOption match {
          case Some(filePart) =>
            mongo.gridFSBucket.uploadFromFile(filePart.filename, filePart.contentType.getOrElse("application/octet-stream"), filePart.ref.toFile).map{ fileId =>
              Ok(Json.obj("original" -> filePart.filename, "name" -> filePart.filename, "url" -> s"/resource/${fileId}", "type" -> ".", "state" -> "SUCCESS"))
            }
          case None =>
            Future.successful{
              Ok(Json.obj("original" -> "", "name" -> "", "url" -> "", "type" -> ".", "state" -> "ERROR"))
            }
        }
      case _ =>
        Future.successful(Ok("unknown action"))
    }
  }

}
