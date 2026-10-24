package de.run.app.data.garmin

import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64

internal sealed interface GarminAuthResult {
    data class Authenticated(val session: GarminSession) : GarminAuthResult
    data object NeedsMfa : GarminAuthResult
    data object InvalidCredentials : GarminAuthResult
    data object AccessBlocked : GarminAuthResult
    data object Failed : GarminAuthResult
}

internal class GarminConnectAuthClient(private val transport: GarminHttpTransport) {
    fun login(email: String, password: CharArray): GarminAuthResult {
        val clearPassword = String(password)
        password.fill('\u0000')
        val body = JSONObject()
            .put("username", email)
            .put("password", clearPassword)
            .put("rememberMe", false)
            .toString()
            .toByteArray(Charsets.UTF_8)
        return try {
            val response = transport.execute(
                loginUrl(SSO_LOGIN_PATH),
                "POST",
                body,
                jsonHeaders()
            )
            authResponse(response)
        } finally {
            body.fill(0)
        }
    }

    fun verifyMfa(code: CharArray): GarminAuthResult {
        val value = String(code)
        code.fill('\u0000')
        val body = JSONObject().put("mfaVerificationCode", value).toString().toByteArray()
        return try {
            val response = transport.execute(
                loginUrl(MFA_PATH),
                "POST",
                body,
                jsonHeaders()
            )
            authResponse(response)
        } finally {
            body.fill(0)
        }
    }

    fun refresh(session: GarminSession): GarminSession? {
        val clientId = session.clientId
        val form = formBody(
            "grant_type" to "refresh_token",
            "refresh_token" to session.refreshToken,
            "client_id" to clientId
        )
        val response = transport.execute(
            TOKEN_URL,
            "POST",
            form,
            mapOf(
                "Authorization" to basicAuth(clientId),
                "Content-Type" to "application/x-www-form-urlencoded",
                "Accept" to "application/json"
            )
        )
        return if (response.statusCode in 200..299) tokenFrom(response.body, clientId) else null
    }

    fun refreshIfNeeded(session: GarminSession): GarminSession? =
        if (session.expiresAtMillis - System.currentTimeMillis() <= REFRESH_EARLY_MILLIS) refresh(session) else session

    private fun authResponse(response: GarminHttpResponse): GarminAuthResult {
        if (response.statusCode == HTTP_FORBIDDEN || response.statusCode == HTTP_TOO_MANY_REQUESTS) {
            return GarminAuthResult.AccessBlocked
        }
        if (response.statusCode == HTTP_UNAUTHORIZED) return GarminAuthResult.InvalidCredentials
        if (response.statusCode !in 200..299) return GarminAuthResult.Failed
        val json = runCatching { JSONObject(String(response.body, Charsets.UTF_8)) }.getOrNull()
            ?: return GarminAuthResult.Failed
        return when (json.optJSONObject("responseStatus")?.optString("type")) {
            "MFA_REQUIRED" -> GarminAuthResult.NeedsMfa
            "INVALID_USERNAME_PASSWORD" -> GarminAuthResult.InvalidCredentials
            "SUCCESSFUL" -> {
                val ticket = json.optString("serviceTicketId").takeIf(String::isNotBlank)
                    ?: return GarminAuthResult.Failed
                exchangeTicket(ticket)
            }
            else -> GarminAuthResult.Failed
        }
    }

    private fun exchangeTicket(ticket: String): GarminAuthResult {
        val form = formBody(
            "grant_type" to SERVICE_TICKET_GRANT,
            "service_ticket" to ticket,
            "service_url" to SERVICE_URL,
            "client_id" to DI_CLIENT_ID
        )
        val response = transport.execute(
            TOKEN_URL,
            "POST",
            form,
            mapOf(
                "Authorization" to basicAuth(DI_CLIENT_ID),
                "Content-Type" to "application/x-www-form-urlencoded",
                "Accept" to "application/json"
            )
        )
        if (response.statusCode == HTTP_FORBIDDEN || response.statusCode == HTTP_TOO_MANY_REQUESTS) {
            return GarminAuthResult.AccessBlocked
        }
        val session = if (response.statusCode in 200..299) tokenFrom(response.body, DI_CLIENT_ID) else null
        return session?.let(GarminAuthResult::Authenticated) ?: GarminAuthResult.Failed
    }

    private fun tokenFrom(bytes: ByteArray, clientId: String): GarminSession? = runCatching {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        val access = json.getString("access_token")
        val refresh = json.getString("refresh_token")
        val lifetime = json.getLong("expires_in").coerceAtLeast(1L)
        GarminSession(access, refresh, clientId, System.currentTimeMillis() + lifetime * 1_000L)
    }.getOrNull()

    private fun loginUrl(path: String): String =
        "$SSO_BASE$path?clientId=$SSO_CLIENT_ID&locale=en-US&service=${encode(SERVICE_URL)}"

    private fun jsonHeaders() = mapOf(
        "Accept" to "application/json, text/plain, */*",
        "Content-Type" to "application/json",
        "Origin" to SSO_BASE,
        "User-Agent" to "LAP/${BuildMetadata.VERSION} (Android)"
    )

    private fun basicAuth(clientId: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$clientId:".toByteArray())

    private fun formBody(vararg values: Pair<String, String>): ByteArray =
        values.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }.toByteArray()

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private object BuildMetadata {
        const val VERSION = "0.3.0"
    }

    private companion object {
        const val SSO_BASE = "https://sso.garmin.com"
        const val SSO_LOGIN_PATH = "/mobile/api/login"
        const val MFA_PATH = "/mobile/api/mfa/verifyCode"
        const val SSO_CLIENT_ID = "GCM_ANDROID_DARK"
        const val SERVICE_URL = "https://mobile.integration.garmin.com/gcm/android"
        const val TOKEN_URL = "https://diauth.garmin.com/di-oauth2-service/oauth/token"
        const val DI_CLIENT_ID = "GARMIN_CONNECT_MOBILE_ANDROID_DI_2025Q2"
        const val SERVICE_TICKET_GRANT = "https://connectapi.garmin.com/di-oauth2-service/oauth/grant/service_ticket"
        const val REFRESH_EARLY_MILLIS = 120_000L
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
