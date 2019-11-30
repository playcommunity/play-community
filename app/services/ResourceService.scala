package services

import java.io.InputStream
import java.net.URL
import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.stream.scaladsl.StreamConverters
import cn.playscala.mongo.Mongo
import com.mongodb.client.model.FindOneAndUpdateOptions
import javax.inject.{Inject, Singleton}
import org.jsoup.Jsoup
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.concurrent.{Future, Promise}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

@Singleton
class ResourceService @Inject()(mongo: Mongo, system: ActorSystem, ws: WSClient) {

  /**
    * 将输入的HTML内容中图片资源本地化。
    * 处理每个文件时设置6秒超时时间。
    * @param html
    * @return 图片资源本地化后的html内容。
    */
  def process(html: String): Future[String] = {
    val doc = Jsoup.parse(html)
    val fList =
      doc.select("img").asScala.toList.filter(e => e.attr("src").startsWith("http") || e.attr("src").startsWith("//")).map{ e =>
        val imgSrc = e.attr("src")
        val imgUrl = if (imgSrc.startsWith("//")) { "http:" + imgSrc } else { imgSrc }
        Logger.info(s"Process img ${imgUrl}")
        ws.url(imgUrl)
          .withRequestTimeout(6 seconds)
          .execute("GET").flatMap{ resp =>
          mongo.gridFSBucket.uploadFromInputStream("", resp.bodyAsBytes.iterator.asInputStream).map { fid =>
            e.attr("src", s"/resource/${fid}")
            fid
          }
        }.recover { case t: Throwable =>
          Logger.error(s"ResourceService.process Error: ${t.getMessage}", t)
          ""
        }
      }

    Future.sequence(fList).map{ _ =>
      doc.outerHtml();
    }
  }

  /**
    * 将输入的HTML内容中解析被提及的用户Id。
    * @param html
    * @return 被提及的用户Id列表。
    */
  def parseMentionedUsers(html: String): List[String] = {
    val doc = Jsoup.parse(html)
    doc.select("span.mention").asScala.toList.map{ e =>
      val id = e.attr("data-id")
      if(id != null && id.trim != "") id.trim else ""
    } filter(_ != "")
  }

}