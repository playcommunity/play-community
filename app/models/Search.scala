package models

/**
  * Created by joymufeng on 2017/7/15.
  */
//case class IndexedDocument(id: String, plate: String, title: String, content: String, author: String, authorId: String, authorHeadImg: String, createTime: Long, viewCount: Int, replyCount: Int, voteCount: Int, highlight: Option[String])

case class IndexedDocument(id: String, plate: String, title: String, content: String, author: String, authorId: String, createTime: Long, hlTitle: Option[String], hlContent: Option[String], bookInfo: Option[BookInfo])

case class BookInfo(bookId: String, bookName: String, author: String, parentCatalog: String, catalog: String, pageFrom: Int, pageTo: Int)