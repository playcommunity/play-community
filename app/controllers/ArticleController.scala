package controllers

import javax.inject._

import models.{Document, Reply}
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import play.api.mvc._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.play.json.collection.JSONCollection
import utils.{HashUtil, RoaringBitmapUtil}
import reactivemongo.play.json._
import collection._
import reactivemongo.play.json.BSONDateTimeFormat
import models.JsonFormats._
import org.joda.time.{DateTime, DateTimeZone}
import org.roaringbitmap.RoaringBitmap
import play.api.libs.concurrent.Execution.Implicits.defaultContext

@Singleton
class ArticleController @Inject()(val reactiveMongoApi: ReactiveMongoApi) extends Controller {
  def articleColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-article"))

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def add = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.article.add())
  }

  def vote(articleId: String, up: Boolean) = Action.async { implicit request: Request[AnyContent] =>

    val login = "joymufeng1"
    (for {
      articleCol <- articleColFuture
      objOpt <- articleCol.find(Json.obj("_id" -> articleId)).one[JsObject]
    } yield {
      //articleCol.insert(Article("0", "", "", "", "", "", "", "", "", List.empty[String], DateTime.now(DateTimeZone.UTC), DateTime.now(DateTimeZone.UTC), List.empty[Reply], 0, List.empty[Long], 0, List.empty[Long], 0)).foreach(println _)

      objOpt.fold(Ok(Json.obj("success" -> false))){ obj =>
        val bitmapStr = (obj \ "viewBitMap").as[String].trim
        val bitmap = RoaringBitmapUtil.fromBase64String(bitmapStr)
        val userHash = HashUtil.toInt(login)
        println(bitmap.contains(userHash))
        if (!bitmap.contains(userHash)) {
          println("add user")
          bitmap.add(userHash)
          articleCol.update(Json.obj("_id" -> articleId), Json.obj("$inc" -> Json.obj("voteCount" -> 1), "$set" -> Json.obj("viewBitMap" -> RoaringBitmapUtil.toBase64String(bitmap))))
        } else {
          println("Already voted.")
        }

        Ok(Json.obj("success" -> false))
      }

    }).recover{ case t: Throwable =>
      println(t.getMessage)
      t.printStackTrace()
      Ok("error")
    }
  }
}
