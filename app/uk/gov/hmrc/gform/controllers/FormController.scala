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

package uk.gov.hmrc.gform.controllers

import javax.inject.Inject

import cats._
import cats.instances.all._
import cats.syntax.all._
import play.api.data.Forms.mapping
import play.api.libs.json.Json
import play.api.mvc.Results.Redirect
import play.api.mvc.{ Action, AnyContent, Request, Result }
import uk.gov.hmrc.gform.auth._
import uk.gov.hmrc.gform.auth.models._
import uk.gov.hmrc.gform.config.ConfigModule
import uk.gov.hmrc.gform.controllers.helpers.FormDataHelpers.processResponseDataFromBody
import uk.gov.hmrc.gform.fileupload.FileUploadModule
import uk.gov.hmrc.gform.gformbackend.GformBackendModule
import uk.gov.hmrc.gform.gformbackend.model._
import uk.gov.hmrc.gform.models.{ Page, UserId }
import uk.gov.hmrc.gform.models.components.FieldId
import uk.gov.hmrc.gform.service.{ DeleteService, RepeatingComponentService, RetrieveService }
import uk.gov.hmrc.gform.models.ValidationUtil.ValidatedType
import uk.gov.hmrc.gform.models._
import uk.gov.hmrc.gform.models.components.{ FieldId, FieldValue }
import uk.gov.hmrc.gform.prepop.PrepopModule
import uk.gov.hmrc.gform.service.{ RepeatingComponentService, SaveService }
import uk.gov.hmrc.gform.validation.ValidationModule
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FormController @Inject() (
    controllersModule: ControllersModule,
    gformBackendModule: GformBackendModule,
    configModule: ConfigModule,
    repeatService: RepeatingComponentService,
    fileUploadModule: FileUploadModule,
    authModule: AuthModule,
    validationModule: ValidationModule,
    prePopModule: PrepopModule
) extends FrontendController {

  import AuthenticatedRequest._
  import controllersModule.i18nSupport._

  def newForm(formTypeId: FormTypeId) = authentication.async { implicit c =>
    val userDetailsF = authorisationService.getUserDetail

    for {// format: OFF
      formTemplate <- gformConnector.getFormTemplate(formTypeId)
      userDetails <- userDetailsF
      authorised <- authorisationService.doAuthorise(formTemplate, userDetails)
      result <- parseResponse(authorised, formTypeId, userDetails)
      //(form, wasFormFound)     <- getOrStartForm(formTypeId, userId)
      // format: ON
    } yield result
  }

  private def redirectToEeitt(formTypeId: FormTypeId): Future[Result] =
    Future.successful(Redirect(s"${configModule.serviceConfig.baseUrl("eeitt-frontend")}/eeitt-auth/enrollment-verification?callbackUrl=${configModule.appConfig.`gform-frontend-base-url`}/submissions/new-form/$formTypeId"))

  private def parseResponse(authenticated: AuthResult, formTypeId: FormTypeId, userDetails: UserDetails)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    authenticated match {
      case Authenticated => result(formTypeId, userDetails.userId)
      case UnAuthenticated => Future.successful(Unauthorized)
      case NeedsAuthenticated => redirectToEeitt(formTypeId)
    }
  }

  //true - it got the form, false - new form was created
  private def getOrStartForm(formTypeId: FormTypeId, userId: UserId)(implicit hc: HeaderCarrier): Future[(Form, Boolean)] = {
    val formId = FormId(userId, formTypeId)
    for {
      maybeForm <- gformConnector.maybeForm(formId)
      form <- maybeForm.map(Future.successful(_)).getOrElse(gformConnector.newForm(formTypeId, userId))
    } yield (form, maybeForm.isDefined)
  }

  private def result(formTypeId: FormTypeId, userId: UserId)(implicit hc: HeaderCarrier, request: Request[_]) = {
    for {
      (form, wasFormFound) <- getOrStartForm(formTypeId, userId)
    } yield {
      if (wasFormFound) {
        Ok(uk.gov.hmrc.gform.views.html.hardcoded.pages.continue_form_page(formTypeId, form._id))
      } else {
        Redirect(routes.FormController.form(form._id, SectionNumber.firstSection))
      }
    }
  }

  def form(formId: FormId, sectionNumber: SectionNumber) = authentication.async { implicit c =>

    for {// format: OFF
      form <- gformConnector.getForm(formId)
      fieldData = getFormData(form)
      formTemplateF = gformConnector.getFormTemplate(form.formData.formTypeId)
      envelopeF = fileUploadService.getEnvelope(form.envelopeId)
      formTemplate <- formTemplateF
      envelope <- envelopeF
      response <- Page(formId, sectionNumber, formTemplate, repeatService, envelope, form.envelopeId, prepopService).renderPage(fieldData, formId, None)
      // format: ON
    } yield response
  }

  def fileUploadPage(formId: FormId, sectionNumber: SectionNumber, fId: String) = authentication.async { implicit c =>
    val fileId = FileId(fId)

    val `redirect-success-url` = appConfig.`gform-frontend-base-url` + routes.FormController.form(formId, sectionNumber)
    val `redirect-error-url` = appConfig.`gform-frontend-base-url` + routes.FormController.form(formId, sectionNumber)

    def actionUrl(envelopeId: EnvelopeId) = s"/file-upload/upload/envelopes/${envelopeId.value}/files/${fileId.value}?redirect-success-url=${`redirect-success-url`}&redirect-error-url=${`redirect-error-url`}"

    for {
      form <- gformConnector.getForm(formId)
      formTemplate <- gformConnector.getFormTemplate(form.formData.formTypeId)
    } yield Ok(
      uk.gov.hmrc.gform.views.html.file_upload_page(formId, sectionNumber, fileId, formTemplate, actionUrl(form.envelopeId))
    )
  }

  private def getFormData(form: Form): Map[FieldId, List[String]] = form.formData.fields.map(fd => fd.id -> List(fd.value)).toMap

  val choice = play.api.data.Form(play.api.data.Forms.single(
    "decision" -> play.api.data.Forms.nonEmptyText
  ))

  def decision(formTypeId: FormTypeId, formId: FormId): Action[AnyContent] = authentication.async { implicit c =>
    choice.bindFromRequest.fold(
      _ => Future.successful(BadRequest(uk.gov.hmrc.gform.views.html.hardcoded.pages.continue_form_page(formTypeId, formId))), {
        case "continue" => Future.successful(Redirect(routes.FormController.form(formId, firstSection /*TODO: once we store section number we could continumer from specific section*/ )))
        case "delete" => Future.successful(Ok(uk.gov.hmrc.gform.views.html.hardcoded.pages.confirm_delete(formTypeId, formId)))
        case _ => Future.successful(Redirect(routes.FormController.newForm(formTypeId)))
      }
    )
  }

  def delete(formTypeId: FormTypeId, formId: FormId): Action[AnyContent] = authentication.async { implicit c =>
    gformConnector.deleteForm(formId).map { x =>
      Redirect(routes.FormController.newForm(formTypeId))
    }
  }

  def updateFormData(formId: FormId, sectionNumber: SectionNumber) = authentication.async { implicit c =>

    val formF = gformConnector.getForm(formId)
    val envelopeIdF = formF.map(_.envelopeId)
    val envelopeF = for {
      envelopeId <- envelopeIdF
      envelope <- fileUploadService.getEnvelope(envelopeId)
    } yield envelope

    val formTemplateF = for {
      form <- formF
      formTemplate <- gformConnector.getFormTemplate(form.formData.formTypeId)
    } yield formTemplate

    val pageF = for {
      form <- formF
      envelope <- envelopeF
      formTemplate <- formTemplateF
    } yield Page(formId, sectionNumber, formTemplate, repeatService, envelope, form.envelopeId, prepopService)

    val userIdF = authConnector.getUserDetails[UserId](authContext)

    val allAtomicFieldsF: Future[List[FieldValue]] = for {
      page <- pageF
    } yield page.allAtomicFields

    val atomicFieldsF = for {// format: OFF
      formTemplate <- formTemplateF
      sections     <- repeatService.getAllSections(formTemplate)
      section      = sections(sectionNumber.value)
      // format: ON
    } yield section.atomicFields(repeatService)

    processResponseDataFromBody(request) { (data: Map[FieldId, Seq[String]]) =>

      val validationF: Future[ValidatedType] = for {   // format: OFF
        atomicFields  <- atomicFieldsF
        form          <- formF
        envelopeId     = form.envelopeId
        validatedData <- Future.sequence(
          atomicFields.map(fv => validationService.validateComponents(fv, data, envelopeId))
        )
        // format: ON
      } yield Monoid[ValidatedType].combineAll(validatedData)

      val finalResultF: Future[Either[List[FormFieldValidationResult], List[FormFieldValidationResult]]] =
        for {// format: OFF
          validation    <- validationF
          envelopeId    <- envelopeIdF
          envelope      <- fileUploadService.getEnvelope(envelopeId)
          atomicFields  <- allAtomicFieldsF
          // format: ON
        } yield ValidationUtil.evaluateValidationResult(atomicFields, validation, data, envelope)

      //End processSaveAndContinue

      val optNextPageF = for {// format: OFF
        envelope      <- envelopeF
        envelopeId    <- envelopeIdF
        formTemplate  <- formTemplateF
        sections      <- repeatService.getAllSections(formTemplate)
        booleanExprs   = sections.map(_.includeIf.getOrElse(IncludeIf(IsTrue)).expr)
        optSectionIdx  = BooleanExpr.nextTrueIdxOpt(sectionNumber.value, booleanExprs, data).map(SectionNumber(_))
        optNextPage    = optSectionIdx.map(sectionNumber => Page(formId, sectionNumber, formTemplate, repeatService, envelope, envelopeId, prepopService))
        // format: ON
      } yield optNextPage

      val actionE: Future[Either[String, FormAction]] = optNextPageF.map(optNextPage => FormAction.determineAction(data, optNextPage))

      actionE.flatMap {
        case Right(action) =>
          action match {
            case SaveAndContinue(nextPageToRender) => processSaveAndContinue(finalResultF, formId, sectionNumber, data)(nextPageToRender.renderPage(data, formId, None))
            case SaveAndExit => processSaveAndExit(finalResultF, formId)
            case SaveAndSummary => processSaveAndContinue(finalResultF, formId, sectionNumber, data)(Future.successful(Redirect(routes.SummaryGen.summaryById(formId))))
            case AddGroup(groupId) => GroupAdd(groupId, formId, sectionNumber)(data)
            case RemoveGroup(groupId) => GroupRemove(groupId, formId, sectionNumber)(data)
          }
        case Left(error) => Future.successful(BadRequest(error))
      }
    }
  }

  private def GroupAdd(groupId: String, formId: FormId, sectionNumber: SectionNumber)(data: Map[FieldId, Seq[String]])(implicit hc: HeaderCarrier, authContext: AuthContext, request: Request[AnyContent]) = {
    for {
      _ <- repeatService.appendNewGroup(groupId)
      page <- pageF(formId, sectionNumber)
      result <- page.renderPage(data, formId, None)
    } yield result
  }

  private def GroupRemove(groupId: String, formId: FormId, sectionNumber: SectionNumber)(data: Map[FieldId, Seq[String]])(implicit hc: HeaderCarrier, authContext: AuthContext, request: Request[AnyContent]) = {
    for {
      updatedData <- repeatService.removeGroup(groupId, data)
      page <- pageF(formId, sectionNumber)
      result <- page.renderPage(updatedData, formId, None)
    } yield result
  }

  private def processSaveAndContinue(finalResult: Future[Either[List[FormFieldValidationResult], List[FormFieldValidationResult]]], formId: FormId, sectionNumber: SectionNumber, data: Map[FieldId, Seq[String]])(continue: Future[Result])(implicit hc: HeaderCarrier, authContext: AuthContext, request: Request[AnyContent]): Future[Result] = {

    def process(userId: UserId, form: Form)(continue: Future[Result])(implicit hc: HeaderCarrier) = finalResult.flatMap {
      case Left(listFormValidation) =>
        val map: Map[FieldValue, FormFieldValidationResult] = listFormValidation.map { (validResult: FormFieldValidationResult) =>
          extractedFieldValue(validResult) -> validResult
        }.toMap

        pageF(formId, sectionNumber).flatMap(page => page.renderPage(data, formId, Some(map.get)))

      case Right(listFormValidation) =>

        val formFieldIds = listFormValidation.map(_.toFormField)
        val formFields = formFieldIds.sequenceU.map(_.flatten).toList.flatten

        val formData = FormData(userId, form.formData.formTypeId, "UTF-8", formFields)

        SaveService.updateFormData(formId, formData, false).flatMap {
          case SaveResult(_, Some(error)) => Future.successful(BadRequest(error))
          case _ =>

            continue
        }
    }

    for {
      userId <- userIdF(authContext)
      form <- formF(formId)
      result <- process(userId, form)(continue)
    } yield result
  }

  private def processSaveAndExit(finalResult: Future[Either[List[FormFieldValidationResult], List[FormFieldValidationResult]]], formId: FormId)(implicit authContext: AuthContext, hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    def process(userId: UserId, form: Form, envelopeId: EnvelopeId) = {
      val formFieldsList: Future[List[FormFieldValidationResult]] = finalResult.map {
        case Left(formFieldResultList) => formFieldResultList
        case Right(formFieldResultList) => formFieldResultList
      }

      val formFieldIds: Future[List[List[FormField]]] = formFieldsList.map(_.map(_.toFormFieldTolerant))
      val formFields: Future[List[FormField]] = formFieldIds.map(_.flatten)

      val formData = formFields.map(formFields => FormData(userId, form.formData.formTypeId, "UTF-8", formFields))

      formData.flatMap(formData =>
        SaveService.updateFormData(formId, formData, tolerant = true).map(response => Ok(uk.gov.hmrc.gform.views.html.hardcoded.pages.save_acknowledgement(formId, formData.formTypeId))))
    }

    for {
      userId <- userIdF(authContext)
      form <- formF(formId)
      envelopeId <- envelopeIdF(formF(formId))
      result <- process(userId, form, envelopeId)
    } yield result
  }

  private def userIdF(authContext: AuthContext)(implicit hc: HeaderCarrier): Future[UserId] =
    authConnector.getUserDetails[UserId](authContext)

  private def formF(formId: FormId)(implicit hc: HeaderCarrier): Future[Form] =
    gformConnector.getForm(formId)

  private def envelopeIdF(formF: Future[Form])(implicit hc: HeaderCarrier): Future[EnvelopeId] =
    formF.map(_.envelopeId)

  private def envelopeF(formId: FormId)(implicit hc: HeaderCarrier) = for {
    envelopeId <- envelopeIdF(formF(formId))
    envelope <- fileUploadService.getEnvelope(envelopeId)
  } yield envelope

  private def formTemplateF(formId: FormId)(implicit hc: HeaderCarrier): Future[FormTemplate] = for {
    form <- formF(formId)
    formTemplate <- gformConnector.getFormTemplate(form.formData.formTypeId)
  } yield formTemplate

  private def pageF(formId: FormId, sectionNumber: SectionNumber)(implicit hc: HeaderCarrier) = for {
    form <- formF(formId)
    envelope <- envelopeF(formId)
    formTemplate <- formTemplateF(formId)
  } yield Page(formId, sectionNumber, formTemplate, repeatService, envelope, form.envelopeId, prepopService)

  private def extractedFieldValue(validResult: FormFieldValidationResult): FieldValue = validResult match {
    case FieldOk(fv, _) => fv
    case FieldError(fv, _, _) => fv
    case ComponentField(fv, _) => fv
    case FieldGlobalOk(fv, _) => fv
    case FieldGlobalError(fv, _, _) => fv
  }

  private lazy val prepopService = prePopModule.prepopService
  private lazy val authentication = controllersModule.authenticatedRequestActions
  private lazy val gformConnector = gformBackendModule.gformConnector
  private lazy val firstSection = SectionNumber(0)
  private lazy val fileUploadService = fileUploadModule.fileUploadService
  private lazy val authConnector = authModule.authConnector
  private lazy val validationService = validationModule.validationService
  private lazy val appConfig = configModule.appConfig
  private lazy val authorisationService = authModule.authorisationService

}
