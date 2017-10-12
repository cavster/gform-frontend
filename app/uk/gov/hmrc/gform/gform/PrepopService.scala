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

package uk.gov.hmrc.gform.gform

import play.api.Logger
import uk.gov.hmrc.auth.core.retrieve.GGCredId
import uk.gov.hmrc.gform.auth.models.Retrievals
import uk.gov.hmrc.gform.auth.models.Retrievals._
import uk.gov.hmrc.gform.connectors.EeittConnector
import uk.gov.hmrc.gform.models.userdetails.GroupId
import uk.gov.hmrc.gform.sharedmodel.formtemplate.{FormTemplateId, _}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import cats.data._
import cats.implicits._
import uk.gov.hmrc.gform.keystore.RepeatingComponentService
import uk.gov.hmrc.gform.sharedmodel.form.RepeatingGroup
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

class AuthContextPrepop {
  def values(value: AuthInfo, retrievals: Retrievals): String = value match {
    case GG => getGGCredId(retrievals)
    case PayeNino => getTaxIdValue(None, "NINO", retrievals)
    case SaUtr => getTaxIdValue(Some("IR-SA"), "UTR", retrievals)
    case CtUtr => getTaxIdValue(Some("IR-CT"), "UTR", retrievals)
    case EtmpRegistrationNumber => getTaxIdValue(Some("HMRC-OBTDS-ORG"), "EtmpRegistrationNumber", retrievals)
  }

  private def getGGCredId(retrievals: Retrievals) = retrievals.authProviderId match {
    case GGCredId(credId) => credId
    case _ => ""
  }
}

class PrepopService(
    eeittConnector: EeittConnector,
    authContextPrepop: AuthContextPrepop,
    repeatingComponentService: RepeatingComponentService
) {

  def prepopData(expr: Expr, formTemplate: FormTemplate, retrievals: Retrievals, data: Map[FormComponentId, Seq[String]], section: BaseSection)(implicit hc: HeaderCarrier): Future[String] = {
    def toInt(str: String): BigDecimal =
      Try(BigDecimal(str)) match {
        case Success(x) => x
        case Failure(_) => BigDecimal(0)
      }

    expr match {
      case AuthCtx(value) => Future.successful(authContextPrepop.values(value, retrievals))
      case Constant(value) => Future.successful(value)
      case EeittCtx(eeitt) => eeittPrepop(eeitt, retrievals, formTemplate._id)
      case Add(field1, field2) =>
        val value = for {
          y <- prepopData(field1, formTemplate, retrievals, data, section)
          z <- prepopData(field2, formTemplate, retrievals, data, section)
        } yield toInt(y) + toInt(z)
        value.map(_.toString)
      case Sum(FormCtx(field)) =>
        val atomicFields = repeatingComponentService.atomicFields(section)
        val cacheMap: Future[CacheMap] = repeatingComponentService.getAllRepeatingGroups
        val repeatingSections: Future[List[List[List[FormComponent]]]] = Future.sequence(atomicFields.map(fv => (fv.id, fv.`type`)).collect {
          case (fieldId, group: Group) => cacheMap.map(_.getEntry[RepeatingGroup](fieldId.value).map(_.list).getOrElse(Nil))
        })
        val listOfValues = Group.getGroup(repeatingSections, FormComponentId(field)).map( z =>
          for {
          id <- z
          x = data.get(id).map(_.head).getOrElse("")
        } yield toInt(x))
        listOfValues.map(_.sum.toString)
      case id: FormCtx => data.get(id.toFieldId).map(_.head).getOrElse("").pure[Future]
      case _ => Future.successful("")
    }
  }

  private def eeittPrepop(eeitt: Eeitt, retrievals: Retrievals, formTemplate: FormTemplate) = {
    val prepop = {
      val regimeId = formTemplate.authConfig match {
        case EEITTAuthConfig(_, rId) => rId
        case _ => RegimeId("")
      }
      for {
        prepopData <- eeitt match {
          case BusinessUser => eeittConnector.prepopulationBusinessUser(GroupId(retrievals.userDetails.groupIdentifier), regimeId).map(_.registrationNumber)
          case Agent => eeittConnector.prepopulationAgent(GroupId(retrievals.userDetails.groupIdentifier)).map(_.arn)
        }
      } yield prepopData
    }
    prepop.recover {
      case NonFatal(error) =>
        Logger.error(s"error when getting known facts from eeitt: " + error.getMessage)
        "" // let's return empty string
    }
  }

  private def addFunctionality() = {

  }

}
