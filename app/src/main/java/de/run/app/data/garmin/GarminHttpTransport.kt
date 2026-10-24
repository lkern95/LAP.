package de.run.app.data.garmin

import java.io.ByteArrayOutputStream
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal data class GarminHttpResponse(val statusCode: Int, val body: ByteArray)

internal interface GarminConnectHttpTransport {
    fun execute(
        urlText: String,
        method: String,
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap(),
        maximumBytes: Int = 64 * 1024 * 1024
    ): GarminHttpResponse
}

internal class GarminHttpTransport : GarminConnectHttpTransport {
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)

    override fun execute(
        urlText: String,
        method: String,
        body: ByteArray?,
        headers: Map<String, String>,
        maximumBytes: Int
    ): GarminHttpResponse {
        val uri = URI(urlText)
        require(uri.scheme == "https" && uri.host in ALLOWED_HOSTS)
        val connection = URL(urlText).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            headers.forEach(connection::setRequestProperty)
            cookies.get(uri, emptyMap()).forEach { (name, values) ->
                values.forEach { connection.addRequestProperty(name, it) }
            }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            cookies.put(uri, connection.headerFields)
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBounded(maximumBytes) } ?: ByteArray(0)
            return GarminHttpResponse(status, bytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readBounded(maximumBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            total += count
            require(total <= maximumBytes)
            output.write(buffer, 0, count)
        }
    }

    private companion object {
        val ALLOWED_HOSTS = setOf("sso.garmin.com", "diauth.garmin.com", "connectapi.garmin.com")
        const val CONNECT_TIMEOUT_MILLIS = 20_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val BUFFER_BYTES = 8 * 1024
        const val MAX_RESPONSE_BYTES = 64 * 1024 * 1024
    }
}
