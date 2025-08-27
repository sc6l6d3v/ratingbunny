package com.iscs.ratingbunny.domains

enum LoginError derives CanEqual:
  case UserNotFound, BadPassword, Inactive

object LoginError:
  def fromString(s: String): Option[LoginError] = s match
    case "user_not_found" => Some(LoginError.UserNotFound)
    case "bad_password"   => Some(LoginError.BadPassword)
    case "inactive"       => Some(LoginError.Inactive)
    case _                => None
