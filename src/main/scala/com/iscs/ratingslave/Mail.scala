package com.iscs.ratingslave

import cats.effect._
import com.typesafe.scalalogging.Logger
import cats.data.NonEmptyList
import emil._, emil.builder._
import emil.javamail._

object Mail extends IOApp {
  private val L = Logger[this.type]
  private val fromAddr = "henry.katz@iscs-i.com"
  private val emailAddr = "freemarket2020@gmx.com"
  private val userId = "freemarket2020@gmx.com"
  private val emailPwd = "Tswdpdttm1!@#"
  private val smtpUrl = "smtp://mail.gmx.com:587"

  private val myemail = JavaMailEmil[IO]()
  private val smtpConf = MailConfig(smtpUrl, userId, emailPwd, SSLType.StartTLS)

  private def setupEmail(from: String,
                         to: String,
                         subject: String,
                         text: String,
                         htmlText: String): IO[NonEmptyList[String]] = {
    val mail: Mail[IO] = MailBuilder.build(
      From(from),
      To(to),
      Subject(subject),
      CustomHeader(Header("User-Agent", "my-email-client")),
      TextBody(text),
      HtmlBody(htmlText),
      //  AttachUrl[IO](getClass.getResource("/files/Test.pdf")).
      //    withFilename("test.pdf").
      //    withMimeType(MimeType.pdf)
    )

    myemail(smtpConf).send(mail)
}
  override def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO.delay(L.info("sending email"))
    sentResults <- setupEmail(fromAddr, emailAddr,
      "test email",
      "Hello!\n\nThis is a mail.", "<h1>Hello!</h1>\n<p>This <b>is</b> a mail.</p>")
    _ <- IO.delay(L.info(s"email send with results: $sentResults"))

  } yield ExitCode.Success
}
