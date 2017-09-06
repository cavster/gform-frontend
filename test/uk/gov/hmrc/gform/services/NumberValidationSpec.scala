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

package uk.gov.hmrc.gform.services

import cats.data.Validated
import cats.data.Validated.Valid
import cats.scalatest.EitherMatchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar.mock
import org.scalatest.{FlatSpec, Matchers}
import uk.gov.hmrc.gform.Spec
import uk.gov.hmrc.gform.fileupload.FileUploadService
import uk.gov.hmrc.gform.models.ValidationUtil.ValidatedType
import uk.gov.hmrc.gform.sharedmodel.ExampleData
import uk.gov.hmrc.gform.sharedmodel.form.{EnvelopeId, FormField}
import uk.gov.hmrc.gform.sharedmodel.formtemplate._
import uk.gov.hmrc.gform.validation.ComponentsValidator
import uk.gov.hmrc.play.http.HeaderCarrier
import cats.implicits._

class NumberValidationSpec extends Spec {


  "Number format" should "accepts whole numbers" in new Test {
    override val value = "123"
    validate(`fieldValue - number`, rawDataFromBrowser).futureValue shouldBe ().valid
  }


//
//  "Number format" should "accept stirling pound and commas within numbers" in {
//    val result = validate(`fieldValue - number`, rawDataFromBrowser)
//
//    result.toEither should beRight(())
//  }
//
//  "Number format" should "return invalid for non-numeric" in {
//    val data = Map(
//      `fieldId - number` -> Seq("THX1138")
//    )
//
//    val result = validate(`fieldValue - number`, data)
//
//    result.toEither should beLeft(Map(`fieldId - number` -> Set("must be a number")))
//  }
//
//  "Number format" should "accepts decimal fractions" in {
//    val data = Map(
//      `fieldId - number` -> Seq("123.4")
//    )
//
//    val result = validate(`fieldValue - number`, data)
//
//    result.toEither should beRight(())
//  }
//


  "PositiveWholeNumber format" should "return invalid for non-numeric" in new Test {
    override val value = "123.4"
    val textConstraint = PositiveNumber(maxFractionalDigits = 0)
    val number = Text(textConstraint, Constant(""))
    override def `fieldValue - number` = super.`fieldValue - number`.copy(`type` = number)
    val expected = Map(`fieldValue - number`.id -> Set("must be a whole number")).invalid[Unit]
    validate(`fieldValue - number`, rawDataFromBrowser).futureValue shouldBe expected withClue "we don't support dots in number formats"
  }
//
//  "PositiveNumber format" should "accepts whole numbers" in {
//    val textConstraint = PositiveNumber()
//    val number = Text(textConstraint, Constant(""))
//
//    val fieldValue = FieldValue(FieldId("n"), number,
//      "sample label", None, None, true, false, false, None)
//
//    val data = Map(
//      FieldId("n") -> Seq("123")
//    )
//
//    val result = validate(fieldValue, data)
//
//    result.toEither should beRight(())
//  }
//
//  "Number format" should "accepts negative numbers" in {
//    val data = Map(
//      `fieldId - number` -> Seq("-789")
//    )
//
//    val result = validate(`fieldValue - number`, data)
//
//    result.toEither should beRight(())
//  }
//
//  "PositiveNumber format" should "return invalid for negative" in {
//    val textConstraint = PositiveNumber()
//    val number = Text(textConstraint, Constant(""))
//
//    val fieldValue = FieldValue(FieldId("n"), number,
//      "sample label", None, None, true, false, false, None)
//
//    val data = Map(
//      FieldId("n") -> Seq("-789")
//    )
//
//    val result = validate(fieldValue, data)
//
//    result.toEither should beLeft(Map(fieldValue.id -> Set("must be a positive number")))
//  }
//
//  "Number format" should "return invalid for too many digits" in {
//    val data = Map(
//      `fieldId - number` -> Seq("1234567890123456789.87654321")
//    )
//
//    val result = validate(`fieldValue - number`, data)
//
//    result.toEither should beLeft(Map(`fieldId - number` -> Set("number must be at most 11 whole digits and decimal fraction must be at most 2 digits")))
//  }
//
//  "Number format" should "return invalid for too many whole digits" in {
//    val data = Map(
//      `fieldId - number` -> Seq("1234567890123456789.87")
//    )
//
//    val result = validate(`fieldValue - number`, data)
//
//    result.toEither should beLeft(Map(`fieldId - number` -> Set("number must be at most 11 whole digits")))
//  }
//
//  "Number(maxFractionalDigits = 0) format" should "return invalid for too many whole digits" in {
//    val data = Map(
//      `fieldId - number` -> Seq("1234567890123456789")
//    )
//
//    val result = validate(`fieldValue - number`, data)
//
//    result.toEither should beLeft(Map(`fieldId - number` -> Set("must be at most 11 digits")))
//  }
//
//  "Number format" should "return invalid for too many fractional digits" in {
//    val data = Map(
//      `fieldId - number` -> Seq("9.87654321")
//    )
//
//    val result = validate(`fieldValue - number`, data)
//
//    result.toEither should beLeft(Map(`fieldId - number` -> Set("decimal fraction must be at most 2 digits")))
//  }
//
//  "Number(2,1) format" should "return invalid for too many digits" in {
//    val textConstraint = Number(2, 1)
//    val number = Text(textConstraint, Constant(""))
//
//    val fieldValue = FieldValue(FieldId("n"), number,
//      "sample label", None, None, true, false, false, None)
//
//    val data = Map(
//      FieldId("n") -> Seq("123.21")
//    )
//
//    val result = validate(fieldValue, data)
//
//    result.toEither should beLeft(Map(fieldValue.id -> Set("number must be at most 2 whole digits and decimal fraction must be at most 1 digits")))
//  }
//
//  "Number(2,1) format" should "return supplied error message" in {
//    val textConstraint = Number(2, 1)
//    val number = Text(textConstraint, Constant(""))
//
//    val fieldValue = FieldValue(FieldId("n"), number,
//      "sample label", None, None, true, false, false, Some("New error message"))
//
//    val data = Map(
//      FieldId("n") -> Seq("123.21")
//    )
//
//    val result = validate(fieldValue, data)
//
//    result.toEither should beLeft(Map(fieldValue.id -> Set("New error message")))
//  }
//
//  def validate(fieldValue: FieldValue, data: Map[FieldId, Seq[String]]): ValidatedType =
//    new ComponentsValidator(data, mock[FileUploadService], EnvelopeId("whatever")).validate(fieldValue).futureValue


  trait Test extends ExampleData {
    def value: String

    override def `formField - number` = FormField(`fieldId - number`, value)

    override def `fieldValue - number` = FieldValue(
      `fieldId - number`,
      Text(Number(), Constant("")),
      "sample label", None, None, true, false, false, None
    )

    override def data = Map(
      `fieldId - number` -> `formField - number`
    )

    def validate(fieldValue: FieldValue, data: Map[FieldId, Seq[String]]) =
      new ComponentsValidator(data, mock[FileUploadService], EnvelopeId("whatever")).validate(fieldValue)

    implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  }

}