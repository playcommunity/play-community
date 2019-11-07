package services

import javax.inject.Inject
import play.api.Configuration
import play.api.libs.mailer.{Email, MailerClient}

class MailerService @Inject() (config: Configuration, mailerClient: MailerClient) {
  private val sender = config.getOptional[String]("play.mailer.user").getOrElse("playscala@163.com")

  def sendEmail(userName: String, userEmail: String,subject:String, htmlContent: String) = {
    val email = Email(
      subject,
      s"${app.Global.siteSetting.name} <${sender}>",
      Seq(s"${userName} <${userEmail}>"),
      //bodyText = Some("A text message"),
      bodyHtml = Some(htmlContent)
    )
    mailerClient.send(email)
  }
}
