/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.merchandiseinbaggage.model.api

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.merchandiseinbaggage.util.Obfuscator.obfuscate


case class Email(email: String, confirmation: String) {
  lazy val obfuscated: Email = Email(obfuscate(email), obfuscate(confirmation))
}

object Email {
  implicit val format: OFormat[Email] = Json.format[Email]
}
