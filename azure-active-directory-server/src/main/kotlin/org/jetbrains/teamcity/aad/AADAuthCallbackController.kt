/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.teamcity.aad

import jetbrains.buildServer.RootUrlHolder
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.RedirectView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class AADAuthCallbackController(
        private val webManager: WebControllerManager,
        private val rootUrlHolder: RootUrlHolder
) : BaseController() {
    init {
        webManager.registerController(AADConstants.DEDICATED_AUTH_PATH, this)
    }

    override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
        val newRedirect = if (request.getParameter("state").isNotEmpty()) request.getParameter("state") else rootUrlHolder.rootUrl
        return ModelAndView(RedirectView(newRedirect))
    }
}
