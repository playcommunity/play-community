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
          val datetimeStr = ((doc \ "entry" \ "updated")(0)).text
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
}
