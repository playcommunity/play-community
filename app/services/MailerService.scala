package services

import javax.inject.Inject

import play.api.libs.mailer.{Email, MailerClient}

class MailerService @Inject() (mailerClient: MailerClient) {
  def sendEmail(userName: String, userEmail: String, htmlContent: String) = {
    val cid = "1234"
    val email = Email(
      "请激活您的PlayScala社区账户！",
      "PlayScala中国社区 <playscalatest@163.com>",
      Seq(s"${userName} <${userEmail}>"),
      //bodyText = Some("A text message"),
      bodyHtml = Some(htmlContent)
    )
    mailerClient.send(email)
  }
}
