package models

case class Link(title: String, url: String, target: String)

case class SiteSetting(name: String, url: String, indexTitle: String, logo: String, links: List[Link], favicon: String, seoKeyword: String, seoDescription: String)

case class DocSetting(_id: String, defaultCatalogId: String, defaultCatalogName: String)