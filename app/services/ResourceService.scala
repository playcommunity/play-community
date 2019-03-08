package services

import java.net.URL
import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import cn.playscala.mongo.Mongo
import com.mongodb.client.model.FindOneAndUpdateOptions
import javax.inject.{Inject, Singleton}
import org.jsoup.Jsoup
import play.api.Logger
import play.api.libs.json.Json
import scala.concurrent.{Future, Promise}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

@Singleton
class ResourceService @Inject()(mongo: Mongo, system: ActorSystem) {

  /**
    * 将输入的HTML内容中图片资源本地化。
    * 处理每个文件时设置6秒超时时间。
    * @param html
    * @return 图片资源本地化后的html内容。
    */
  def process(html: String): Future[String] = {
    val doc = Jsoup.parse(html)
    val fList =
      doc.select("img").asScala.toList.filter(e => e.attr("src").startsWith("http")).map{ e =>
        val p = Promise[String]()
        system.scheduler.scheduleOnce(6 seconds){
          if (!p.isCompleted) {
            p.failure(new TimeoutException("Read image failed after 6 seconds."))
          }
        }
        try {
          val imgUrl = e.attr("src")
          val imgStream = new URL(imgUrl).openStream()
          Logger.info(s"Process img ${imgUrl}")
          mongo.gridFSBucket.uploadFromInputStream("", imgStream).map { fid =>
            e.attr("src", s"/resource/${fid}")
            p.success(fid)
          }
        } catch { case t: Throwable =>
          Logger.error(s"ResourceService.process Error: ${t.getMessage}", t)
          p.failure(t)
        }

        p.future
      }

    Future.sequence(fList).map{ _ =>
      doc.outerHtml();
    }
  }
}