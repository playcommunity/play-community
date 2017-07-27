package services

import javax.inject.Inject

import models.IPLocation
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

class IPHelper @Inject() (ws: WSClient) (implicit ec: ExecutionContext) {

  def getLocation(ip: String): Future[Option[IPLocation]] = {
    ws.url(s"http://freeapi.ipip.net/${ip}").get().map{ resp =>
      resp.json.as[List[String]] match {
        case Nil => None
        case List(country) => Some(IPLocation(country, "", ""))
        case List(country, p) => Some(IPLocation(country, p, ""))
        case List(country, p, c, _*) => Some(IPLocation(country, p, c))
      }
    }.recover{ case t: Throwable => None }
  }
}
