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

package uk.gov.hmrc.gform.models

import cats.data.NonEmptyList
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import play.twirl.api.Html
import uk.gov.hmrc.gform.fileupload.Envelope
import uk.gov.hmrc.gform.gformbackend.model.FormTemplate
import uk.gov.hmrc.gform.models.components._
import uk.gov.hmrc.gform.models.helpers.DateHelperFunctions.withOffset
import uk.gov.hmrc.gform.models.helpers.Fields
import uk.gov.hmrc.gform.models.helpers.Javascript.fieldJavascript
import uk.gov.hmrc.gform.service.{ PrepopService, RepeatingComponentService }
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PageShader(
    curr: Int,
    fieldData: Map[FieldId, Seq[String]],
    formTemplate: FormTemplate,
    section: Section,
    f: Option[FieldValue => Option[FormFieldValidationResult]],
    repeatService: RepeatingComponentService,
    envelope: Envelope
)(implicit authContext: AuthContext, hc: HeaderCarrier) {

  def render(): Future[PageForRender] = {
    val snippetsSeq = section.fields.map(fv => htmlFor(fv, 0, mutable.LinkedHashMap.empty[FieldValue, Future[Html]]))
    val snippetsSeq2 = section.fields.foldLeft(mutable.LinkedHashMap.empty[FieldValue, Future[Html]]) {
      case (acc: mutable.LinkedHashMap[FieldValue, Future[Html]], curr: FieldValue) => acc += (curr -> htmlFor(curr, 0, acc))
    }.values.toList
    val snippets = Future.sequence(snippetsSeq2)
    val javasctipt = fieldJavascript(formTemplate.sections.flatMap(_.atomicFields(repeatService)))
    snippets.map(snippets => PageForRender(curr, section.title, hiddenSnippets, snippets, javasctipt))
  }

  private def htmlFor(orgFieldValue: FieldValue, instance: Int, sofar: mutable.LinkedHashMap[FieldValue, Future[Html]]): Future[Html] = {
    val fieldValue = adjustIdForRepeatingGroups(orgFieldValue, instance)
    fieldValue.`type` match {
      case g @ Group(fvs, orientation, _, _, _, _) => htmlForGroup(g, fieldValue, fvs, orientation, sofar)
      case Date(_, offset, dateValue) => htmlForDate(fieldValue, offset, dateValue)
      case Address(international) => htmlForAddress(fieldValue, international, instance)
      case t @ Text(expr, _) => htmlForText(fieldValue, t, expr)
      case Choice(choice, options, orientation, selections, optionalHelpText) => htmlForChoice0(fieldValue, choice, options, orientation, selections, optionalHelpText, sofar)
      case FileUpload() => htmlForFileUpload(fieldValue)
      case InformationMessage(infoType, infoText) => htmlForInformationMessage(fieldValue, infoType, infoText)
    }
  }

  private def htmlForInformationMessage(fieldValue: FieldValue, infoType: InfoType, infoText: String) = {
    val flavour = new GFMFlavourDescriptor
    val parsedTree = new MarkdownParser(flavour).buildMarkdownTreeFromString(infoText)
    val parsedMarkdownText = new HtmlGenerator(infoText, parsedTree, flavour, false).generateHtml
    Future.successful(uk.gov.hmrc.gform.views.html.field_template_info(fieldValue, infoType, Html(parsedMarkdownText)))
  }

  private def htmlForFileUpload(fieldValue: FieldValue) = {
    Future.successful(uk.gov.hmrc.gform.views.html.field_template_file_upload(fieldValue, validate(fieldValue)))
  }

  private def htmlForChoice(fieldValue: FieldValue, choice: ChoiceType, options: NonEmptyList[String], orientation: Orientation, selections: List[Int], optionalHelpText: Option[List[String]]) = {
    val prepopValues = fieldData.get(fieldValue.id) match {
      case None => selections.map(_.toString).toSet
      case Some(_) => Set.empty[String] // Don't prepop something we already submitted
    }

    val snippet =
      choice match {
        case Radio | YesNo => uk.gov.hmrc.gform.views.html.choice("radio", fieldValue, options, orientation, prepopValues, validate(fieldValue), optionalHelpText)
        case Checkbox => uk.gov.hmrc.gform.views.html.choice("checkbox", fieldValue, options, orientation, prepopValues, validate(fieldValue), optionalHelpText)
        case Inline => uk.gov.hmrc.gform.views.html.choiceInline(fieldValue, options, prepopValues, validate(fieldValue), optionalHelpText)
      }

    Future.successful(snippet)
  }

  private def htmlForChoice0(fieldValue: FieldValue, choice: ChoiceType, options: NonEmptyList[String], orientation: Orientation, selections: List[Int], optionalHelpText: Option[List[String]], sofar: mutable.LinkedHashMap[FieldValue, Future[Html]]) = {

    val fvsdependentOnThisChoice: List[FieldValue] = sofar.keys.filter {
      case (fv) => fv.shortName.getOrElse("-").init == fieldValue.id.value
    }.toList

    if (fvsdependentOnThisChoice.isEmpty)
      htmlForChoice(fieldValue, choice, options, orientation, selections, optionalHelpText)
    else {
      val eventualHtml = sofar.get(fvsdependentOnThisChoice.head).get

      sofar.remove(fvsdependentOnThisChoice.head)
      htmlForCollapsable(fieldValue, options, orientation, selections, optionalHelpText, eventualHtml)
    }

  }

  def htmlForCollapsable(fieldValue: FieldValue, options: NonEmptyList[String], orientation: Orientation, selections: List[Int], optionalHelpText: Option[List[String]], collapsable: Future[Html]) = {
    val prepopValues = fieldData.get(fieldValue.id) match {
      case None => selections.map(_.toString).toSet
      case Some(_) => Set.empty[String] // Don't prepop something we already submitted
    }

    collapsable.map(html => uk.gov.hmrc.gform.views.html.collapsable("checkbox", fieldValue, options, orientation, prepopValues, f.getOrElse(okF)(fieldValue), html))
  }

  private def htmlForText(fieldValue: FieldValue, t: Text, expr: Expr) = {
    val prepopValueF = fieldData.get(fieldValue.id) match {
      case None => PrepopService.prepopData(expr, formTemplate.formTypeId)
      case _ => Future.successful("") // Don't prepop something we already submitted
    }
    prepopValueF.map(prepopValue => uk.gov.hmrc.gform.views.html.field_template_text(fieldValue, t, prepopValue, validate(fieldValue)))
  }

  private def htmlForAddress(fieldValue: FieldValue, international: Boolean, instance: Int) = {
    Future.successful(uk.gov.hmrc.gform.views.html.field_template_address(international, fieldValue, validate(fieldValue), instance))
  }

  private def htmlForDate(fieldValue: FieldValue, offset: Offset, dateValue: Option[DateValue]) = {
    val prepopValues = dateValue.map(DateExpr.fromDateValue).map(withOffset(offset, _))
    Future.successful(uk.gov.hmrc.gform.views.html.field_template_date(fieldValue, validate(fieldValue), prepopValues))
  }

  private def htmlForGroup(groupField: Group, fieldValue: FieldValue, fvs: List[FieldValue], orientation: Orientation, sofar: mutable.LinkedHashMap[FieldValue, Future[Html]]) = {

    def fireHtmlGeneration(count: Int, sofar: mutable.LinkedHashMap[FieldValue, Future[Html]]) = (0 until count).flatMap { count =>
      if (count == 0) {
        fvs.map(fv => htmlFor(fv, count, sofar))
      } else {
        Future.successful(Html(s"""<div><legend class="h3-heading">${groupField.repeatLabel.getOrElse("")} ${count}</legend>""")) +:
          fvs.map(fv => htmlFor(fv, count, sofar)) :+
          Future.successful(Html("</div>"))
      }
    }.toList

    for {
      (count, limitReached) <- repeatService.getCountAndTestIfLimitReached(fieldValue, groupField)
      lhtml <- Future.sequence(fireHtmlGeneration(count, sofar))
    } yield uk.gov.hmrc.gform.views.html.group(fieldValue, groupField, lhtml, orientation, limitReached)
  }

  private def adjustIdForRepeatingGroups(fieldValue: FieldValue, instance: Int) = {
    if (instance == 0) {
      fieldValue
    } else {
      fieldValue.copy(id = repeatService.buildRepeatingId(fieldValue, instance))
    }
  }

  private lazy val hiddenTemplateFields = formTemplate.sections.filterNot(_ == section).flatMap(_.atomicFields(repeatService))
  private lazy val hiddenSnippets = Fields.toFormField(fieldData, hiddenTemplateFields, repeatService).map(formField => uk.gov.hmrc.gform.views.html.hidden_field(formField))
  private lazy val okF: FieldValue => Option[FormFieldValidationResult] = Fields.okValues(fieldData, section.atomicFields(repeatService), repeatService, envelope)
  private def validate(fieldValue: FieldValue): Option[FormFieldValidationResult] = f.getOrElse(okF)(fieldValue)

}
