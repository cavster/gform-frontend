/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.bforms.models

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue, Json, OFormat, _}
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.data.format.Formats.DateFormatter
import uk.gov.hmrc.bforms.models.ValueClassFormatLocalDate.ValueClassFormatBigDecimal

/**
  * Created by daniel-connelly on 05/01/17.
  */
case class LandFillTaxDetailsPersistence(ID : String = RandomStringUtils.random(4),
                                         firstName : FirstName = new FirstName(""),
                                         lastName : LastName = new LastName(""),
                                         telephoneNumber: TelephoneNumber = new TelephoneNumber(""),
                                         status: Status = new Status(""),
                                         nameOfBusiness: NameOfBusiness = new NameOfBusiness(""),
                                         accountingPeriodStartDate: LocalDate = LocalDate.now,
                                         accountingPeriodEndDate: LocalDate = LocalDate.now,
                                         taxDueForThisPeriod: TaxDueForThisPeriod = new TaxDueForThisPeriod(""),
                                         underDeclarationsFromPreviousPeriod: UnderDeclarationsFromPreviousPeriod = new UnderDeclarationsFromPreviousPeriod(""),
                                         overDeclarationsForThisPeriod: OverDeclarationsForThisPeriod = new OverDeclarationsForThisPeriod(""),
                                         taxCreditClaimedForEnvironment: TaxCreditClaimedForEnvironment = new TaxCreditClaimedForEnvironment(0),
                                         badDebtReliefClaimed: BadDebtReliefClaimed = new BadDebtReliefClaimed(""),
                                         otherCredits: OtherCredits = new OtherCredits(""),
                                         standardRateWaste: StandardRateWaste = new StandardRateWaste(""),
                                         lowerRateWaste: LowerRateWaste = new LowerRateWaste(""),
                                         exemptWaste: ExemptWaste = new ExemptWaste(""),
                                         environmentalBody1: Seq[EnvironmentalBody] =  Seq(EnvironmentalBody("default" , 0)),
                                         emailAddress: EmailAddress = new EmailAddress(Some("")),
                                         confirmEmailAddress: ConfirmEmailAddress = new ConfirmEmailAddress(Some("")),
                                         datePersisted : LocalDate = LocalDate.now
                                        ){
}

case class EnvironmentalBodyPersistence(bodyName:BodyName, amount:Amount)

class FirstName(val value:String) extends AnyVal
class LastName(val value:String) extends AnyVal
class TelephoneNumber(val value:String) extends AnyVal
class Status (val value:String) extends AnyVal
class NameOfBusiness(val value:String) extends AnyVal
class AccountingPeriodStartDate(val value:LocalDate) extends AnyVal
class AccountingPeriodEndDate(val value:LocalDate) extends AnyVal
class TaxDueForThisPeriod(val value:String) extends AnyVal
class UnderDeclarationsFromPreviousPeriod(val value:String) extends AnyVal
class OverDeclarationsForThisPeriod(val value:String) extends AnyVal
class TaxCreditClaimedForEnvironment(val value:BigDecimal) extends AnyVal
class BadDebtReliefClaimed(val value:String) extends AnyVal
class OtherCredits(val value:String) extends AnyVal
class StandardRateWaste(val value:String) extends AnyVal
class LowerRateWaste(val value:String) extends AnyVal
class ExemptWaste(val value:String) extends AnyVal
class EmailAddress(val value:Option[String]) extends AnyVal
class ConfirmEmailAddress(val value:Option[String]) extends AnyVal
class BodyName(val value:String) extends AnyVal
class Amount(val value:BigDecimal) extends AnyVal

object BodyName {
  def apply(value:String) = new BodyName(value)

  implicit val format: Format[BodyName] = ValueClassFormat.format(BodyName.apply)(_.value)
}

object Amount {
  def apply(value:BigDecimal) = new Amount(value)

  implicit val format : Format[Amount] = ValueClassFormatBigDecimal.format(Amount.apply)(_.value)
}

object EnvironmentalBodyPersistence{
  implicit val formats : Format[EnvironmentalBodyPersistence] = Json.format[EnvironmentalBodyPersistence]
}


object FirstName {
  def apply(value: String) = new FirstName(value)

  implicit val format : Format[FirstName] = ValueClassFormat.format(FirstName.apply)(_.value)
}

object LastName {
  def apply(value:String) = new LastName(value)

  implicit val format : Format[LastName] = ValueClassFormat.format(LastName.apply)(_.value)

}

object TelephoneNumber {
  def apply(value: String) = new TelephoneNumber(value)

  implicit val format : Format[TelephoneNumber] = ValueClassFormat.format(TelephoneNumber.apply)(_.value)
}

object Status {
  def apply(value: String) = new Status(value)

  implicit val format : Format[Status] = ValueClassFormat.format(Status.apply)(_.value)
}

object NameOfBusiness {
  def apply(value: String) = new NameOfBusiness(value)

  implicit val format : Format[NameOfBusiness] = ValueClassFormat.format(NameOfBusiness.apply)(_.value)
}

//object AccountingPeriodStartDate {
//  def apply(value: LocalDate) = new AccountingPeriodStartDate(value)
//
//  implicit val format : Format[AccountingPeriodStartDate] = ValueClassFormatLocalDate.format(AccountingPeriodStartDate.apply)(_.value)
//}
//
//object AccountingPeriodEndDate {
//  def apply(value: LocalDate) = new AccountingPeriodEndDate(value)
//
//  implicit val format : Format[AccountingPeriodEndDate] =  ValueClassFormatLocalDate.format(AccountingPeriodEndDate.apply)(_.value)
//}

object TaxDueForThisPeriod {
  def apply(value: String) = new TaxDueForThisPeriod(value)

  implicit val format : Format[TaxDueForThisPeriod] = ValueClassFormat.format(TaxDueForThisPeriod.apply)(_.value)
}

object UnderDeclarationsFromPreviousPeriod {
  def apply(value: String) = new UnderDeclarationsFromPreviousPeriod(value)

  implicit val format : Format[UnderDeclarationsFromPreviousPeriod] = ValueClassFormat.format(UnderDeclarationsFromPreviousPeriod.apply)(_.value)
}

object OverDeclarationsForThisPeriod {
  def apply(value: String) = new OverDeclarationsForThisPeriod(value)

  implicit val format : Format[OverDeclarationsForThisPeriod] = ValueClassFormat.format(OverDeclarationsForThisPeriod.apply)(_.value)
}

object TaxCreditClaimedForEnvironment {
  def apply(value: BigDecimal) = new TaxCreditClaimedForEnvironment(value)

  implicit val format : Format[TaxCreditClaimedForEnvironment] = ValueClassFormatBigDecimal.format(TaxCreditClaimedForEnvironment.apply)(_.value)
}

object BadDebtReliefClaimed {
  def apply(value: String) = new BadDebtReliefClaimed(value)

  implicit val format : Format[BadDebtReliefClaimed] = ValueClassFormat.format(BadDebtReliefClaimed.apply)(_.value)
}

object OtherCredits {
  def apply(value: String) = new OtherCredits(value)

  implicit val format : Format[OtherCredits] = ValueClassFormat.format(OtherCredits.apply)(_.value)
}

object StandardRateWaste {
  def apply(value: String) = new StandardRateWaste(value)

  implicit val format : Format[StandardRateWaste] = ValueClassFormat.format(StandardRateWaste.apply)(_.value)
}

object LowerRateWaste {
  def apply(value: String) = new LowerRateWaste(value)

  implicit val format : Format[LowerRateWaste] = ValueClassFormat.format(LowerRateWaste.apply)(_.value)
}

object ExemptWaste {
  def apply(value: String) = new ExemptWaste(value)

  implicit val format : Format[ExemptWaste] = ValueClassFormat.format(ExemptWaste.apply)(_.value)
}




object EmailAddress {
  def apply(value: String) = new EmailAddress(Some(value))

  implicit val format : Format[EmailAddress] = ValueClassFormat.format(EmailAddress.apply)(_.value.getOrElse("None"))
}

object ConfirmEmailAddress {
  def apply(value: String) = new ConfirmEmailAddress(Some(value))

  implicit val format : Format[ConfirmEmailAddress] = ValueClassFormat.format(ConfirmEmailAddress.apply)(_.value.getOrElse("None"))
}

object ValueClassFormat {
  def format[A: Format](fromStringToA: String => A)(fromAToString: A => String) = {
    new Format[A] {
      def reads(json: JsValue): JsResult[A] = {
        json match {
          case JsString(str) => JsSuccess(fromStringToA(str))
          case unknown => JsError(s"JsString value expected, got: $unknown")
        }
      }
      def writes(a: A): JsValue = JsString(fromAToString(a))
    }
  }
}

object ValueClassFormatLocalDate {
  def format[A: Format](fromDateToA: LocalDate => A)(fromAToDate: A => LocalDate) = {
    new Format[LocalDate] {
      override def reads(json: JsValue): JsResult[LocalDate] = json.validate[String].map(LocalDate.parse)

      override def writes(a: LocalDate): JsValue = Json.toJson(a.toString)
    }
  }

  object ValueClassFormatBigDecimal {
    def format[A: Format](fromBigDecimalToA: BigDecimal => A)(fromAToBigDecimal: A => BigDecimal) = {
      new Format[A] {
        def reads(json: JsValue): JsResult[A] = {
          json match {
            case JsNumber(num) => JsSuccess(fromBigDecimalToA(num))
            case unknown => JsError(s"JsNumber value expected, got: $unknown")
          }
        }

        def writes(a: A): JsValue = JsNumber(fromAToBigDecimal(a))
      }
    }
  }

}

