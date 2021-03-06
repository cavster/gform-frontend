/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.gform.sharedmodel.formtemplate

import julienrf.json.derived
import play.api.libs.json._

sealed trait Expr
final case class Add(field1: Expr, field2: Expr) extends Expr
final case class Multiply(field1: Expr, field2: Expr) extends Expr
final case class Subtraction(field1: Expr, field2: Expr) extends Expr
final case class FormCtx(value: String) extends Expr {
  def toFieldId = FormComponentId(this.value)
}
final case class Sum(field1: Expr) extends Expr
final case class AuthCtx(value: AuthInfo) extends Expr
final case class EeittCtx(value: Eeitt) extends Expr
final case class UserCtx(value: UserField) extends Expr
final case class Constant(value: String) extends Expr

object FormCtx {
  implicit val format: OFormat[FormCtx] = derived.oformat[FormCtx]
}

object Expr {
  implicit val format: OFormat[Expr] = derived.oformat
}

sealed trait Operation
final case object Addition extends Operation
final case object Multiplication extends Operation

sealed trait Eeitt
final case object BusinessUser extends Eeitt
final case object Agent extends Eeitt
final case object UserId extends Eeitt

object Eeitt {
  implicit val format: OFormat[Eeitt] = derived.oformat
}

sealed trait UserField
final case object AffinityGroup extends UserField

object UserField {
  implicit val format: OFormat[UserField] = derived.oformat
}

sealed trait AuthInfo
final case object GG extends AuthInfo
final case object PayeNino extends AuthInfo
final case object SaUtr extends AuthInfo
final case object CtUtr extends AuthInfo
final case object EtmpRegistrationNumber extends AuthInfo

object AuthInfo {
  implicit val format: OFormat[AuthInfo] = derived.oformat
}

