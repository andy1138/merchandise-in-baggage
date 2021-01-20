/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.merchandiseinbaggage.service

import play.api.Logger
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.libs.json.Json.toJson
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.merchandiseinbaggage.model.api.Declaration
import uk.gov.hmrc.merchandiseinbaggage.model.audit.RefundableDeclaration
import uk.gov.hmrc.play.audit.http.connector.AuditResult.{Disabled, Failure, Success}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait Auditor {
  val auditConnector: AuditConnector
  val messagesApi: MessagesApi

  val messagesEN: Messages = MessagesImpl(Lang("en"), messagesApi)

  private val logger = Logger(this.getClass)

  def auditDeclarationComplete(declaration: Declaration)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AuditResult] =
    auditConnector
      .sendExtendedEvent(
        ExtendedDataEvent(auditSource = "merchandise-in-baggage", auditType = "DeclarationComplete", detail = toJson(declaration)))
      .recover {
        case NonFatal(e) => Failure(e.getMessage)
      }
      .map { status =>
        status match {
          case Success =>
            logger.info(s"Successful audit of declaration with id [${declaration.declarationId}]")
          case Disabled =>
            logger.warn(s"Audit of declaration with id [${declaration.declarationId}] returned Disabled")
          case Failure(message, _) =>
            logger.error(s"Audit of declaration with id [${declaration.declarationId}] returned Failure with message [$message]")
        }

        status
      }

  def auditRefundableDeclaration(declaration: Declaration)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val refundableDeclarations: Option[Seq[RefundableDeclaration]] =
      declaration.maybeTotalCalculationResult.map { calc =>
        calc.paymentCalculations.paymentCalculations.map { payment =>
          RefundableDeclaration(
            declaration.mibReference,
            declaration.nameOfPersonCarryingTheGoods.toString,
            declaration.eori.toString,
            payment.goods.categoryQuantityOfGoods.category,
            payment.calculationResult.gbpAmount.formattedInPounds,
            payment.calculationResult.duty.formattedInPounds,
            payment.calculationResult.vat.formattedInPounds,
            s"${payment.goods.goodsVatRate.value}%",
            payment.calculationResult.taxDue.formattedInPounds,
            payment.goods.categoryQuantityOfGoods.quantity,
            messagesEN(payment.goods.countryOfPurchase.countryName),
            payment.goods.purchaseDetails.amount,
            payment.goods.purchaseDetails.currency.code,
            payment.calculationResult.conversionRatePeriod.fold("1.00")(_.rate.toString)
          )
        }
      }

    refundableDeclarations.fold(Seq(Future.successful(()))) { declarations =>
      declarations.map { refund =>
        auditConnector
          .sendExtendedEvent(
            ExtendedDataEvent(auditSource = "merchandise-in-baggage", auditType = "RefundableDeclaration", detail = toJson(refund))
          )
          .recover {
            case NonFatal(e) => Failure(e.getMessage)
          }
          .map {
            case Success =>
              logger.info(s"Successful audit of declaration with id [${declaration.declarationId}]")
            case Disabled =>
              logger.warn(s"Audit of declaration with id [${declaration.declarationId}] returned Disabled")
            case Failure(message, _) =>
              logger.error(s"Audit of declaration with id [${declaration.declarationId}] returned Failure with message [$message]")
          }
      }
    }

    Future.successful(())
  }
}
