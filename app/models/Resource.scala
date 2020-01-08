package models

import java.time.Instant

import cn.playscala.mongo.annotations.Entity
import models._
import org.bson.types.ObjectId
import play.api.libs.json.Json
import utils.{BitmapUtil, BoardUtil, DateTimeUtil, RequestHelper}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// 抽象[问答/分享/文档/题库]
@Entity("common-resource")
case class Resource (
  _id: String,  //资源唯一标识，格式为：用户ID+时间序列
  title: String, //资源标题
  keywords: String = "", // 关键字
  content: Option[String], //内容
  editorType: String = "quill", //富文本编辑器类型
  author: Author, //资源作者
  replyStat: ReplyStat = ReplyStat(0, Nil, None, None), //回复统计
  viewStat: ViewStat = ViewStat(0, ""), //查看统计
  voteStat: VoteStat = VoteStat(0, ""), //投票统计
  collectStat: CollectStat = CollectStat(0, ""), //收藏统计
  createTime: Instant = Instant.now, //创建时间
  updateTime: Instant = Instant.now, //更新时间
  top: Boolean = false, // 置顶
  recommended: Boolean = false, // 精华
  closed: Boolean = false, // 是否关闭
  visible: Boolean = true, // 是否发布
  resType: String, // 资源类型
  categoryPath: String = "/", //所属分类路径
  categoryName: String = "", //所属分类名称
  doc: Option[DocInfo] = None, //额外文档信息(resType == Resource.Doc)
  exam: Option[ExamInfo] = None //额外试题信息(resType == Resource.Exam)
){

  def getBoard(): Option[String] = BoardUtil.getBoardPath(categoryPath)

  /**
   * 检查已查看用户列表是否包含指定用户。
   * @param uid 用户Id
   * @return true/false
   */
  def hasViewer(uid: Int): Boolean = {
    uid match {
      case -1 => false
      case other =>
        val viewBitmap = BitmapUtil.fromBase64String(viewStat.bitmap)
        viewBitmap.contains(uid)
    }
  }

  /**
    * 新增已查看用户。
    * @param uid 用户Id
    * @return true/false
    */
  def addViewer(uid: Int): Future[Boolean] = {
    uid match {
      case -1 => Future.successful(false)
      case other =>
        val viewBitmap = BitmapUtil.fromBase64String(viewStat.bitmap)
        viewBitmap.add(uid)
        DomainRegistry.mongo.updateOne[Resource](
          Json.obj("_id" -> _id),
          Json.obj(
            "$set" -> Json.obj("viewStat.bitmap" -> BitmapUtil.toBase64String(viewBitmap)),
            "$inc" -> Json.obj("viewStat.count" -> 1)
          )
        ).map(_.getModifiedCount == 1)
    }
  }

  /**
   * 增加访问计数。
   * @return true/false
   */
  def incViewCount(count: Int): Future[Boolean] = {
    DomainRegistry.mongo.updateOne[Resource](
      Json.obj("_id" -> _id),
      Json.obj(
        "$inc" -> Json.obj("viewStat.count" -> count)
      )
    ).map(_.getModifiedCount == 1)
  }

  /**
    * 检查已收藏用户列表是否包含指定用户。
    * @param uid 用户Id
    * @return true/false
    */
  def hasCollector(uid: Int): Boolean = {
    uid match {
      case -1 => false
      case other =>
        val viewBitmap = BitmapUtil.fromBase64String(collectStat.bitmap)
        viewBitmap.contains(uid)
    }
  }

  /**
    * 新增收藏用户。
    * @param uid 用户Id
    * @return true/false
    */
  def addCollector(uid: Int): Future[Boolean] = {
    uid match {
      case -1 => Future.successful(false)
      case other =>
        val viewBitmap = BitmapUtil.fromBase64String(collectStat.bitmap)
        viewBitmap.add(uid)
        DomainRegistry.mongo.updateOne[Resource](
          Json.obj("_id" -> _id),
          Json.obj(
            "$set" -> Json.obj("collectStat.bitmap" -> BitmapUtil.toBase64String(viewBitmap)),
            "$inc" -> Json.obj("collectStat.count" -> 1)
          )
        ).map(_.getModifiedCount == 1)

    }
  }

  /**
    * 移除收藏用户。
    * @param uid 用户Id
    * @return true/false
    */
  def removeCollector(uid: Int): Future[Boolean] = {
    uid match {
      case -1 => Future.successful(false)
      case other =>
        val viewBitmap = BitmapUtil.fromBase64String(collectStat.bitmap)
        viewBitmap.remove(uid)
        DomainRegistry.mongo.updateOne[Resource](
          Json.obj("_id" -> _id),
          Json.obj(
            "$set" -> Json.obj("collectStat.bitmap" -> BitmapUtil.toBase64String(viewBitmap)),
            "$inc" -> Json.obj("collectStat.count" -> -1)
          )
        ).map(_.getModifiedCount == 1)
    }
  }

  /**
   * 设置最佳答案
   */
  def setBestReply(reply: Reply): Future[Boolean] = {
    DomainRegistry.mongo.updateOne[Resource](
      Json.obj("_id" -> _id),
      Json.obj("$set" -> Json.obj("replyStat.bestReply" -> reply))
    ).map(_.getModifiedCount == 1)
  }

}


object Resource {
  val Resource = "resource"
  val QA = "qa"
  val Doc = "doc"
  val Article = "article"
  val Exam = "exam"
  val Tweet = "tweet"
  val Corporation = "corporation"
}

//额外文档信息
case class DocInfo(
  title: String, catalogId: String
)

//额外试题信息
case class ExamInfo(
  options: List[String], //试题选项
  answer: String, //试题答案
  answers: List[ExamAnswer], //用户提交答案
  explain: String //试题答案解析
)

//用户提交答案
case class ExamAnswer(
  uid: String, //答题用户标识
  option: String, //提交答案
  createTime: Instant = Instant.now() //答题时间
)