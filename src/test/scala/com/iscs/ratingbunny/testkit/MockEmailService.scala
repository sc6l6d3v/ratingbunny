package com.iscs.ratingbunny.testkit

import cats.Applicative
import cats.data.NonEmptyList
import cats.syntax.all.*
import com.iscs.mail.{EmailAttachment, EmailService}
import emil.Mail

class MockEmailService[F[_]](
    val fromAddress: String = "test@example.com"
)(using F: Applicative[F])
    extends EmailService[F]:

  // Track sent emails for verification in tests
  private var sentEmails: List[SentEmail] = List.empty

  case class SentEmail(
      to: List[String],
      subject: String,
      textBody: String,
      htmlBody: String,
      attachments: List[EmailAttachment[F]] = List.empty
  )

  def getSentEmails: List[SentEmail] = sentEmails
  def clearSentEmails(): Unit        = sentEmails = List.empty

  override def sendEmail(
      to: String,
      subject: String,
      textBody: String,
      htmlBody: String,
      attachments: List[EmailAttachment[F]] = List.empty
  ): F[NonEmptyList[String]] =
    sentEmails = sentEmails :+ SentEmail(List(to), subject, textBody, htmlBody, attachments)
    F.pure(NonEmptyList.of(s"mock-message-id-$to-${System.currentTimeMillis()}"))

  override def sendEmailToMultiple(
      recipients: List[String],
      subject: String,
      textBody: String,
      htmlBody: String,
      attachments: List[EmailAttachment[F]] = List.empty
  ): F[List[NonEmptyList[String]]] =
    sentEmails = sentEmails :+ SentEmail(recipients, subject, textBody, htmlBody, attachments)
    recipients
      .traverse(recipient => F.pure(NonEmptyList.of(s"mock-message-id-$recipient-${System.currentTimeMillis()}")))
      .map(_.toList)

  override def sendEmail(to: String, subject: String, textBody: String, htmlBody: String): F[NonEmptyList[String]] =
    sendEmail(to, subject, textBody, htmlBody, List.empty)

  override def sendEmailToMultiple(
      recipients: List[String],
      subject: String,
      textBody: String,
      htmlBody: String
  ): F[List[NonEmptyList[String]]] =
    sendEmailToMultiple(recipients, subject, textBody, htmlBody, List.empty)

  override def send(mail: Mail[F]): F[NonEmptyList[String]] =
    // For this mock, we'll just return a mock message ID
    // In a real test scenario, you might want to extract info from the Mail object
    F.pure(NonEmptyList.of(s"mock-message-id-${System.currentTimeMillis()}"))
