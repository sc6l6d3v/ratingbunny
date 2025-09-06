package com.iscs.ratingbunny

import com.iscs.ratingbunny.model.Requests.ReqParams
import mongo4cats.bson.ObjectId

import java.time.Instant

package object domains:

  final private case class PathRec(path: String)

  trait AutoRecBase

  final case class AutoNameRec(firstName: String, lastName: Option[String]) extends AutoRecBase

  final case class AutoTitleRec(primaryTitle: Option[String]) extends AutoRecBase

  trait TitleRecBase:
    val _id: String
    val averageRating: Option[Double]
    val numVotes: Option[Int]
    val titleType: String
    val primaryTitle: String
    val originalTitle: String
    val isAdult: Int
    val startYear: Int
    val endYear: Int
    val runtimeMinutes: Option[Int]
    val genresList: Option[List[String]]

  final case class TitleRec(
      _id: String,
      averageRating: Option[Double],
      numVotes: Option[Int],
      titleType: String,
      primaryTitle: String,
      originalTitle: String,
      isAdult: Int,
      startYear: Int,
      endYear: Int,
      runtimeMinutes: Option[Int],
      genresList: Option[List[String]]
  ) extends TitleRecBase

  final case class TitleRecPath(
      _id: String,
      averageRating: Option[Double],
      numVotes: Option[Int],
      titleType: String,
      primaryTitle: String,
      originalTitle: String,
      isAdult: Int,
      startYear: Int,
      endYear: Int,
      runtimeMinutes: Option[Int],
      genresList: Option[List[String]],
      posterPath: Option[String]
  ) extends TitleRecBase

  final case class UserHistory(
      _id: String,
      userId: String,
      createdAt: Instant,
      params: ReqParams,
      sig: String,
      hits: Int
  )

  /** -- incoming payload */
  final case class SignupRequest(
      email: String,
      password: String,
      displayName: Option[String],
      plan: Plan
  )

  /** --- persisted docs --- */
  final case class UserDoc(
      _id: ObjectId = new ObjectId(),
      email: String,
      passwordHash: String,
      userid: String,
      plan: Plan,
      status: SubscriptionStatus,
      displayName: Option[String],
      prefs: List[String] = Nil,
      createdAt: Instant = Instant.now(),
      emailVerified: Boolean = false,
      verificationTokenHash: Option[String] = None,
      verificationExpires: Option[Instant] = None // consider cron to clean up expired tokens
  )

  final case class UserProfileDoc(
      userid: String,
      avatarUrl: Option[String] = None,
      favGenres: List[String] = Nil,
      locale: Option[String] = Some("en_US")
  )

  // ---------- request / response ----------
  final case class LoginRequest(email: String, password: String)
  final case class LoginOK(userid: String, tokens: TokenPair)
  final case class SignupOK(userid: String)

  /** Public view of a user, omitting sensitive fields */
  final case class UserInfo(userid: String, email: String, plan: String, displayName: Option[String])

  // ===== Requests / responses ===================================================
  final case class RegisterReq(email: String, password: String)
  final case class LoginReq(email: String, password: String)
  final case class TokenPair(access: String, refresh: String)
