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

package uk.gov.hmrc.gform.email

import play.api.libs.json.{ Json, OFormat }

case class EmailTemplate(
  to: Seq[String],
  emailParams: EmailContent, //content that goes in the email to be put into template
  private val templateId: String = "newMessageAlert", //the template ID that the content will be put into
  private val force: Boolean = false
)

object EmailTemplate {
  implicit val format: OFormat[EmailTemplate] = Json.format[EmailTemplate]
}

case class EmailContent(content: Option[String]) //must be option as the service json schema requires empty {} for email content
object EmailContent {
  implicit val format: OFormat[EmailContent] = Json.format[EmailContent]
}
