package services

import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.mongo.MongoLeaderRepository
import javax.inject.{Inject, Singleton}
import models.{Leader, Site, SiteUpdateType}
import org.jsoup.Jsoup
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsArray, JsValue}
import play.api.libs.ws.{WSClient, XML}
import utils.DateTimeUtil
import play.api.libs.json.Json._

import scala.concurrent.Future
import scala.util.{Random, Try}
import scala.concurrent.duration._

@Singleton
class LeaderService @Inject()(leaderRepo: MongoLeaderRepository, ws: WSClient, actorSystem: ActorSystem) {

  /**
    * 读取博客更新时间
    */
  def crawlLeaders() {
    leaderRepo.findActiveList().map{ _.filter(_.site.nonEmpty).foreach{ leader =>
      val time = Random.nextInt(100)
      Logger.info(s"Crawl ${leader.name} after ${time} s.")
      actorSystem.scheduler.scheduleOnce(time seconds){
        crawlLeader(leader)
      }
    }}.recover{ case t: Throwable =>
      Logger.info(s"Read leaders error: ${t.getMessage}", t)
    }
  }

  def crawlLeader(leader: Leader): Future[Boolean] = {
    Logger.info(s"Crawl leader ${leader.name}")
    getUpdateTime(leader.site.get) map {
      case Some(updateTime) =>
        leaderRepo.update(leader._id, obj("$set" -> obj("site.updateTime" -> updateTime.asInstanceOf[Instant], "updateTime" -> updateTime.asInstanceOf[Instant])))
        Logger.info(s"Upate ${leader.name}: ${updateTime.toString}")
        true
      case None =>
        Logger.error(s"Site ${leader.name} has no update time.")
        false
    }
  }

  def getUpdateTime(site: Site): Future[Option[Instant]] = {
    site.crawlType match {
      case SiteUpdateType.TIME_TAG_SELECTOR => getUpdateTimeByTimeTagSelector(site)
      case SiteUpdateType.SELECTOR => getUpdateTimeBySelector(site)
      case SiteUpdateType.RSS => getUpdateTimeByRss(site)
      case SiteUpdateType.ATOM => getUpdateTimeByAtom(site)
      case SiteUpdateType.CNBLOGS => getUpdateTimeByCNBlogsRss(site)
      case SiteUpdateType.ZHIHU => getUpdateTimeByZhiHu(site)
      case SiteUpdateType.SCALA => getUpdateTimeByScala(site)
      case SiteUpdateType.SCALACOOL => getUpdateTimeByScalaCool(site)
      case SiteUpdateType.JUEJIN => getUpdateTimeByJueJin(site)
      case _ => Future.successful(None)
    }
  }

  def getUpdateTimeBySelector(site: Site): Future[Option[Instant]] = {
    ws.url(site.crawlUrl).withFollowRedirects(true).execute("GET").map{ resp =>
      resp.status match {
        case 200 =>
          val elements = Jsoup.parse(resp.body).select(site.crawlSelector)
          if(elements != null && elements.size() > 0){
            site.selectorType match {
              case "date" => DateTimeUtil.dateStrToInstant(elements.get(0).text().trim)
              case "datetime" => DateTimeUtil.dateTimeStrToInstant(elements.get(0).text().trim)
            }
          } else {
            None
          }
        case o =>
          Logger.error(s"Scan Site ${site.url} Error: ${o}")
          None
      }
    }.recover{ case t: Throwable =>
      Logger.error("Parse Selector Error: " + t.getMessage, t)
      None
    }
  }

  def getUpdateTimeByTimeTagSelector(site: Site): Future[Option[Instant]] = {
    ws.url(site.crawlUrl).withFollowRedirects(true).execute("GET").map{ resp =>
      resp.status match {
        case 200 =>
          val elements = Jsoup.parse(resp.body).select(site.crawlSelector)
          //println(site.crawlUrl + " elements: " + elements.size())
          //println(elements.get(0).outerHtml())
          if(elements != null && elements.size() > 0){
            site.selectorType match {
              case "date" => DateTimeUtil.dateStrToInstant(elements.get(0).attr("datetime").trim)
              case "datetime" => DateTimeUtil.dateTimeStrToInstant(elements.get(0).attr("datetime").trim)
            }
          } else {
            None
          }
        case o =>
          Logger.error(s"Scan Site ${site.url} Error: ${o}")
          None
      }
    }.recover{ case t: Throwable =>
      Logger.error("Parse Selector Error: " + t.getMessage, t)
      None
    }
  }

  def getUpdateTimeByZhiHu(site: Site): Future[Option[Instant]] = {
    ws.url(site.crawlUrl).withFollowRedirects(true).execute("GET").map{ resp =>
      resp.status match {
        case 200 =>
          val elements = Jsoup.parse(resp.body).select("meta[itemprop=\"datePublished\"]")
          //println(site.crawlUrl + " elements: " + elements.size())
          //println(elements.get(0).outerHtml())
          if(elements != null && elements.size() > 0){
            DateTimeUtil.dateTimeStrToInstant(elements.get(0).attr("content").trim)
          } else {
            None
          }
        case o =>
          Logger.error(s"Scan Site ${site.url} Error: ${o}")
          None
      }
    }.recover{ case t: Throwable =>
      Logger.error("Parse Selector Error: " + t.getMessage, t)
      None
    }
  }

  def getUpdateTimeByRss(site: Site): Future[Option[Instant]] = {
    ws.url(site.crawlUrl).withFollowRedirects(true).execute("GET").map{ resp =>
      resp.status match {
        case 200 =>
          val doc = XML.parser.loadString(resp.body)
          val datetimeStr = ((doc \ "channel" \ "item" \ "pubDate")(0)).text
          //println("pubDate: " + datetimeStr)
          DateTimeUtil.dateTimeStrToInstant(datetimeStr)
        case o =>
          Logger.error(s"Scan Site ${site.url} Error: ${o}")
          None
      }
    }.recover{ case t: Throwable =>
      Logger.error("Parse Rss Error: " + t.getMessage, t)
      None
    }
  }

  def getUpdateTimeByAtom(site: Site): Future[Option[Instant]] = {
    ws.url(site.crawlUrl).withFollowRedirects(true).execute("GET").map{ resp =>
      resp.status match {
        case 200 =>
          val doc = XML.parser.loadString(resp.body)
          val datetimeStr = (doc \ "entry" \ "published").headOption.getOrElse((doc \ "entry" \ "updated").head).text
          //println("pubDate: " + datetimeStr)
          DateTimeUtil.dateTimeStrToInstant(datetimeStr)
        case o =>
          Logger.error(s"Scan Site ${site.url} Error: ${o}")
          None
      }
    }.recover{ case t: Throwable =>
      Logger.error("Parse Atom Error: " + t.getMessage, t)
      None
    }
  }

  def getUpdateTimeByCNBlogsRss(site: Site): Future[Option[Instant]] = {
    ws.url(site.crawlUrl).withFollowRedirects(true).execute("GET").map{ resp =>
      resp.status match {
        case 200 =>
          //println("cnblogs: " + resp.body.drop(1))
          val doc = XML.parser.loadString(resp.body.drop(1))
          val datetimeStr = ((doc \ "updated")(0)).text
          //println("cnblogs: " + datetimeStr)
          DateTimeUtil.dateTimeStrToInstant(datetimeStr)
        case o =>
          Logger.error(s"Scan Site ${site.url} Error: ${o}")
          None
      }
    }.recover{ case t: Throwable =>
      Logger.error("Parse CNBlogs Error: " + t.getMessage, t)
      None
    }
  }

  def getUpdateTimeByScala(site: Site): Future[Option[Instant]] = {
    ws.url(site.crawlUrl).withFollowRedirects(true).execute("GET").map{ resp =>
      resp.status match {
        case 200 =>
          val elements = Jsoup.parse(resp.body).select(site.crawlSelector)
          if(elements != null && elements.size() > 0){
            var arr = elements.get(0).text().trim.split("\\s+")
            var month = arr(2).take(3)
            month = month.head.toUpper + month.tail.toLowerCase
            val dateStr = arr(1) + " " + month + " " + arr(3)
            println(dateStr)

            DateTimeUtil.dateStrToInstant(dateStr)
          } else {
            None
          }
        case o =>
          Logger.error(s"Scan Site ${site.url} Error: ${o}")
          None
      }
    }.recover{ case t: Throwable =>
      Logger.error("Parse Selector Error: " + t.getMessage, t)
      None
    }
  }

  def getUpdateTimeByScalaCool(site: Site): Future[Option[Instant]] = {
    ws.url(site.crawlUrl).withFollowRedirects(true).execute("GET").map{ resp =>
      resp.status match {
        case 200 =>
          val elements = Jsoup.parse(resp.body).select("time.post-time")
          if(elements != null && elements.size() > 0){
            //val dateStr = DateTimeUtil.toString(Instant.now(), "yyyy") + "-" + elements.get(0).text().trim.replace("月", "-").replace("日", "")
            val dateStr =  "2019-" + elements.get(0).text().trim.replace("月", "-").replace("日", "")
            DateTimeUtil.dateStrToInstant(dateStr)
          } else {
            None
          }
        case o =>
          Logger.error(s"Scan Site ${site.url} Error: ${o}")
          None
      }
    }.recover{ case t: Throwable =>
      Logger.error("Parse Selector Error: " + t.getMessage, t)
      None
    }
  }

  def getUpdateTimeByJueJin(site: Site): Future[Option[Instant]] = {
    ws.url(site.crawlUrl).withFollowRedirects(true).execute("GET").map{ resp =>
      resp.status match {
        case 200 =>
          val elements = Jsoup.parse(resp.body).select("span.date")
          if(elements != null && elements.size() > 0){
            val dateStr = elements.get(0).text().trim
            var secs = -1
            if(dateStr.endsWith("刚刚")){
              secs = 59
            } else if(dateStr.endsWith("秒前") || dateStr.endsWith("秒钟前")){
              secs = dateStr.replace("秒钟前", "").replace("秒前", "").trim.toInt
            } else if(dateStr.endsWith("分钟前")){
              val minutes = dateStr.replace("分钟前", "").trim.toInt
              secs = minutes * 60
            } else if(dateStr.endsWith("小时前")){
              val hours = dateStr.replace("小时前", "").trim.toInt
              secs = hours * 3600
            } else if(dateStr.endsWith("天前")){
              val days = dateStr.replace("天前", "").trim.toInt
              secs = days * 86400
            } else if(dateStr.endsWith("月前")){
              val months = dateStr.replace("月前", "").trim.toInt
              secs = months * 30 * 86400
            } else if(dateStr.endsWith("年前")){
              val years = dateStr.replace("年前", "").trim.toInt
              secs = years * 365 * 86400
            }

            if(secs > 0) {
              Some(Instant.now().minusSeconds(secs))
            } else {
              None
            }
          } else {
            None
          }
        case o =>
          Logger.error(s"Scan Site ${site.url} Error: ${o}")
          None
      }
    }.recover{ case t: Throwable =>
      Logger.error("Parse Selector Error: " + t.getMessage, t)
      None
    }
  }
}
