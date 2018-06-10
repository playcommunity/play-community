package controllers.demo

import javax.inject._
import akka.stream.{Materializer, OverflowStrategy, ThrottleMode}
import akka.stream.scaladsl.{Keep, Sink, Source}
import cn.playscala.mongo.Mongo
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._

@Singleton
class ThrottleDemoController @Inject()(cc: ControllerComponents, mongo: Mongo)(implicit ec: ExecutionContext, materializer: Materializer) extends AbstractController(cc) {
  case class ThrottledRequest(promise: Promise[Boolean], time: Long)

  // 接收请求并限速
  val sourceQueue =
    Source
      .queue[ThrottledRequest](100, OverflowStrategy.backpressure).throttle(1, 1.second, 1, ThrottleMode.shaping)
      .toMat(Sink.foreach{ r =>
        // 处理未超时请求
        if (System.currentTimeMillis() - r.time <= 15000) {
          r.promise.success(false)
        } else {
          r.promise.success(true)
        }
      })(Keep.left).run()

  // 只有通过限速器(sourceQueue)的请求才会被执行
  def throttle[A](action: Action[A]) = Action.async(action.parser) { request =>
    val promise = Promise[Boolean]()
    sourceQueue.offer(ThrottledRequest(promise, System.currentTimeMillis()))
    promise.future.flatMap{ isTimeout =>
      if (!isTimeout) {
        action(request)
      } else {
        Future.successful(Forbidden("Timeout."))
      }
    }
  }

  def throttledAction = throttle { Action { implicit request: Request[AnyContent] =>
    Ok("Finish.")
  }}

}
