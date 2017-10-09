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

package uk.gov.hmrc.gform.keystore

import uk.gov.hmrc.gform.config.ConfigModule
import uk.gov.hmrc.gform.wshttp.WSHttpModule

class KeystoreModule(configModule: ConfigModule, wSHttpModule: WSHttpModule) {

  val sessionCacheConnector: SessionCacheConnector = new SessionCacheConnector(
    configModule.appConfig.appName,
    configModule.serviceConfig.baseUrl("cachable.session-cache"),
    configModule.serviceConfig.getConfString("cachable.session-cache.domain", ""),
    wSHttpModule.auditableWSHttp
  )

  val repeatingComponentService: RepeatingComponentService = new RepeatingComponentService(sessionCacheConnector, configModule)
}