package models

import java.time.Instant

import cn.playscala.mongo.annotations.Entity
import org.bson.types.ObjectId
import play.api.libs.json.Json
import play.api.libs.json.Json.obj
import utils.DateTimeUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Entity("common-user")
case class User(
  _id: String,
  role: String,
  login: String,
  password: String,
  setting: UserSetting,
  stat: UserStat,
  score: Int,
  enabled: Boolean,
  from: String,
  ip: String,
  ipLocation: Option[IPLocation],
  channels: List[Channel],
  activeCode: Option[String],
  salt: Option[String] = None,//新加密的盐，存放16字节字符串，每个用户盐值单独生成，由argon2使用
  argon2Hash: Option[String] = None //存放Argon2密码摘要，存在则说明已经将校验升级到argon2
){

  /**
   *  获取管理版块列表
   */
  def getOwnedBoards(): Future[List[Board]] = {
    DomainRegistry.boardRepo.findBoardsByOwnerId(_id)
  }

  /**
   * 增加文章数量
   */
  def incArticleCount(count: Int): Future[Boolean] = {
    DomainRegistry.mongo
      .updateOne[User](Json.obj("_id" -> _id), Json.obj("$inc" -> Json.obj("stat.resCount" -> count, "stat.articleCount" -> count)))
      .map{ r => if(r.getModifiedCount == 1) true else false }
  }

  /**
    * 增加Score
    */
  def incScore(score: Int): Future[Boolean] = {
    DomainRegistry.mongo
      .updateOne[User](Json.obj("_id" -> _id), Json.obj("$inc" -> Json.obj("score" -> score)))
      .map{ r => if(r.getModifiedCount == 1) true else false }
  }

  /**
   * 收藏资源
   */
  def collectResource(resource: Resource): Future[Boolean] = {
    DomainRegistry.mongo
      .insertOne[StatCollect](StatCollect(ObjectId.get.toHexString, _id, resource.resType, resource._id, resource.author, resource.title, resource.createTime, DateTimeUtil.now()))
      .map{ _ => true }
  }

  /**
    * 取消收藏
    */
  def uncollectResource(resource: Resource): Future[Boolean] = {
    DomainRegistry.mongo
      .deleteMany[StatCollect](Json.obj("uid" -> _id, "resId" -> resource._id, "resType" -> resource.resType))
      .map{ _ => true }
  }

  /**
    * 激活用户
    */
  def setToActive(): Future[Boolean] = {
    DomainRegistry.mongo
      .updateOne[User](obj("_id" -> _id), obj("$unset" -> Json.obj("activeCode" -> 1)))
      .map{ _ => true }
  }

}

case class Channel(id: String, name: String, url: String)

object UserStat { val DEFAULT = UserStat(0, 0, 0, 0, 0, 0, 0, 0, 0, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now, DateTimeUtil.now()) }
case class UserStat(resCount: Int, docCount: Int, articleCount: Int, qaCount: Int, fileCount: Int, replyCount: Int, commentCount: Int, voteCount: Int, votedCount: Int, createTime: Instant, updateTime: Instant, lastLoginTime: Instant, lastReplyTime: Instant)

case class IPLocation(country: String, province: String, city: String)

case class UserSetting(name: String, pinyin:String, gender: String, introduction: String, headImg: String, city: String)

// 登录类型
object LoginType extends Enumeration {
  type LoginType = Value

  val PASSWORD  = Value(0, "password") //用户密码登录
  val GITHUB = Value(1, "github")
  val QQ = Value(2, "qq")
  val WEIXIN = Value(3, "weixin")

  implicit def enumToString = (loginType: LoginType.Value) => loginType.toString
}