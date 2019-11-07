package utils

import models.{Resource, VoteStat}


/**
  * Created by joymufeng on 2017/7/6.
  */
object AppUtil {

  def getCollectionName(resType: String): String = {
    resType match {
      case Resource.Resource => "common-resource"
      case Resource.Doc => "common-resource"
      case Resource.Article => "common-resource"
      case Resource.QA => "common-resource"
      case Resource.Exam => "common-resource"
      case Resource.Tweet => "common-tweet"
      case Resource.Corporation => "common-corporation"
    }
  }

  def prettyResource(resType: String): String = {
    resType match {
      case Resource.Doc => "文档"
      case Resource.Article => "分享"
      case Resource.QA => "问答"
      case Resource.Exam => "题库"
      case Resource.Tweet => "说说"
      case Resource.Corporation => "公司"
      case _ => ""
    }
  }

  def prettyAction(action: String): String = {
    action match {
      case "create" => "创建"
      case "update" => "更新"
      case "reply" => "回复"
      case "remove" => "删除"
      case "vote" => "点赞"
      case "unvote" => "取消点赞"
      case "collect" => "收藏"
      case "accept" => "采纳回复"
      case _ => "其它"
    }
  }

  def toggleVote(voteStat: VoteStat, uid: Int): VoteStat = {
    val bitmap = BitmapUtil.fromBase64String(voteStat.bitmap)
    // 投票
    if (!bitmap.contains(uid)) {
      bitmap.add(uid)
      VoteStat(voteStat.count + 1, BitmapUtil.toBase64String(bitmap))
    } else {
      bitmap.remove(uid)
      VoteStat(voteStat.count - 1, BitmapUtil.toBase64String(bitmap))
    }
  }

  // 解析页码
  def parsePage(page: Int): Int = {
    if(page < 1){ 1 } else { page }
  }

}
