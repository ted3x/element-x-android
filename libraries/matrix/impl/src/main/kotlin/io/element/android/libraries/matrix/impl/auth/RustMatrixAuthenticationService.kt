/*
 * Copyright (c) 2023 New Vector Ltd
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

package io.element.android.libraries.matrix.impl.auth

import com.squareup.anvil.annotations.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.mapFailure
import io.element.android.libraries.di.AppScope
import io.element.android.libraries.di.SingleIn
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.auth.MatrixHomeServerDetails
import io.element.android.libraries.matrix.api.auth.OidcDetails
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.impl.RustMatrixClientFactory
import io.element.android.libraries.matrix.impl.exception.mapClientException
import io.element.android.libraries.matrix.impl.mapper.toSessionData
import io.element.android.libraries.network.useragent.UserAgentProvider
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.OidcAuthenticationData
import org.matrix.rustcomponents.sdk.use
import java.io.File
import javax.inject.Inject
import org.matrix.rustcomponents.sdk.AuthenticationService as RustAuthenticationService

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RustMatrixAuthenticationService @Inject constructor(
    baseDirectory: File,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val sessionStore: SessionStore,
    userAgentProvider: UserAgentProvider,
    private val rustMatrixClientFactory: RustMatrixClientFactory,
) : MatrixAuthenticationService {

    private val authService: RustAuthenticationService = RustAuthenticationService(
        basePath = baseDirectory.absolutePath,
        passphrase = null,
        userAgent = userAgentProvider.provide(),
        oidcConfiguration = oidcConfiguration,
        customSlidingSyncProxy = null,
    )
    private var currentHomeserver = MutableStateFlow<MatrixHomeServerDetails?>(null)

    override fun isLoggedIn(): Flow<Boolean> {
        return sessionStore.isLoggedIn()
    }

    override suspend fun getLatestSessionId(): SessionId? = withContext(coroutineDispatchers.io) {
        sessionStore.getLatestSession()?.userId?.let { SessionId(it) }
    }

    override suspend fun restoreSession(sessionId: SessionId): Result<MatrixClient> = withContext(coroutineDispatchers.io) {
        runCatching {
            val sessionData = sessionStore.getSession(sessionId.value)
            if (sessionData != null) {
                rustMatrixClientFactory.create(sessionData)
            } else {
                error("No session to restore with id $sessionId")
            }
        }.mapFailure { failure ->
            failure.mapClientException()
        }
    }

    override fun getHomeserverDetails(): StateFlow<MatrixHomeServerDetails?> = currentHomeserver

    override suspend fun setHomeserver(homeserver: String): Result<Unit> =
        withContext(coroutineDispatchers.io) {
            runCatching {
                authService.configureHomeserver(homeserver)
                val homeServerDetails = authService.homeserverDetails()?.map()
                if (homeServerDetails != null) {
                    currentHomeserver.value = homeServerDetails.copy(url = homeserver)
                }
            }.mapFailure { failure ->
                failure.mapAuthenticationException()
            }
        }

    override suspend fun login(username: String, password: String): Result<SessionId> =
        withContext(coroutineDispatchers.io) {
            runCatching {
                val client = authService.login(username, password, "Element X Android", null)
                val sessionData = client.use { it.session().toSessionData() }
                sessionStore.storeData(sessionData)
                SessionId(sessionData.userId)
            }.mapFailure { failure ->
                failure.mapAuthenticationException()
            }
        }

    private var pendingOidcAuthenticationData: OidcAuthenticationData? = null

    override suspend fun getOidcUrl(): Result<OidcDetails> {
        return withContext(coroutineDispatchers.io) {
            runCatching {
                val oidcAuthenticationData = authService.urlForOidcLogin()
                val url = oidcAuthenticationData.loginUrl()
                pendingOidcAuthenticationData = oidcAuthenticationData
                OidcDetails(url)
            }.mapFailure { failure ->
                failure.mapAuthenticationException()
            }
        }
    }

    override suspend fun cancelOidcLogin(): Result<Unit> {
        return withContext(coroutineDispatchers.io) {
            runCatching {
                pendingOidcAuthenticationData?.close()
                pendingOidcAuthenticationData = null
            }.mapFailure { failure ->
                failure.mapAuthenticationException()
            }
        }
    }

    /**
     * callbackUrl should be the uriRedirect from OidcClientMetadata (with all the parameters).
     */
    override suspend fun loginWithOidc(callbackUrl: String): Result<SessionId> {
        return withContext(coroutineDispatchers.io) {
            runCatching {
                val urlForOidcLogin = pendingOidcAuthenticationData ?: error("You need to call `getOidcUrl()` first")
                val client = authService.loginWithOidcCallback(urlForOidcLogin, callbackUrl)
                val sessionData = client.use { it.session().toSessionData() }
                pendingOidcAuthenticationData?.close()
                pendingOidcAuthenticationData = null
                sessionStore.storeData(sessionData)
                SessionId(sessionData.userId)
            }.mapFailure { failure ->
                failure.mapAuthenticationException()
            }
        }
    }
}
