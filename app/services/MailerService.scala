package services

import javax.inject.Inject

import models.App
import play.api.Configuration
import play.api.libs.mailer.{Email, MailerClient}

class MailerService @Inject() (config: Configuration, mailerClient: MailerClient) {
  private val sender = config.getOptional[String]("play.mailer.user").getOrElse("playscala@163.com")

  def sendEmail(userName: String, userEmail: String, htmlContent: String) = {
    val cid = "1234"
    val email = Email(
      s"请激活您的${App.siteSetting.name}账户！",
      s"${App.siteSetting.name} <${sender}>",
      Seq(s"${userName} <${userEmail}>"),
      //bodyText = Some("A text message"),
      bodyHtml = Some(htmlContent)
    )
    mailerClient.send(email)
  }
}
