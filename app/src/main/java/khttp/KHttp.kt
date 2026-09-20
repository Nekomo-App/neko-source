package khttp

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/*
 * Minimal drop-in replacement for the io.karn:khttp library (dead jcenter dep).
 * Implements the small API surface this app uses: get/post/put/delete/head/patch
 * with headers/data/json/params/timeout/cookies/stream/allowRedirects,
 * and a Response exposing text/content/url/statusCode/headers/cookies.
 */

object structures {
    object cookie {
        class CookieJar : LinkedHashMap<String, String> {
            constructor() : super()
            constructor(map: Map<String, String>) : super(map)
        }
    }
}

private typealias CookieJar = khttp.structures.cookie.CookieJar

class Response(
    val statusCode: Int,
    val url: String,
    val headers: Map<String, String>,
    val cookies: CookieJar,
    val content: ByteArray,
    val connection: HttpURLConnection?,
) {
    val text: String
        get() = content.toString(Charsets.UTF_8)

    val jsonObject: org.json.JSONObject
        get() = org.json.JSONObject(text)
}

private fun buildQuery(params: Map<String, String>?): String {
    if (params.isNullOrEmpty()) return ""
    return params.entries.joinToString("&", prefix = "?") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }
}

private fun request(
    method: String,
    url: String,
    params: Map<String, String>? = null,
    headers: Map<String, String>? = null,
    data: Any? = null,
    json: Any? = null,
    timeout: Double = 30.0,
    allowRedirects: Boolean = true,
    stream: Boolean = false,
    cookies: Map<String, String>? = null,
): Response {
    val fullUrl = if (url.contains("?") || params == null) url else url + buildQuery(params)

    var conn = URL(fullUrl).openConnection() as HttpURLConnection
    var currentUrl = fullUrl
    var redirectsLeft = 8

    while (true) {
        if (method == "PATCH") {
            // HttpURLConnection doesn't support PATCH
            conn.requestMethod = "POST"
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else {
            conn.requestMethod = method
        }
        conn.connectTimeout = (timeout * 1000).toInt()
        conn.readTimeout = (timeout * 1000).toInt()
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept-Encoding", "gzip")
        conn.setRequestProperty("User-Agent", "khttp/1.0.0")
        conn.setRequestProperty("Accept", "*/*")

        headers?.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (!cookies.isNullOrEmpty()) {
            conn.setRequestProperty(
                "Cookie",
                cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }

        val bodyBytes: ByteArray? = when {
            json != null -> {
                conn.setRequestProperty("Content-Type", "application/json")
                (if (json is String) json else mapper.writeValueAsString(json))
                    .toByteArray(Charsets.UTF_8)
            }
            data is Map<*, *> -> {
                conn.setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded"
                )
                data.entries.joinToString("&") {
                    "${URLEncoder.encode(it.key.toString(), "UTF-8")}=${
                        URLEncoder.encode(it.value.toString(), "UTF-8")
                    }"
                }.toByteArray(Charsets.UTF_8)
            }
            data is ByteArray -> data
            data != null -> data.toString().toByteArray(Charsets.UTF_8)
            else -> null
        }

        if (bodyBytes != null && method != "GET" && method != "HEAD") {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(bodyBytes.size)
            conn.outputStream.use { os: OutputStream -> os.write(bodyBytes) }
        }

        val code = conn.responseCode

        if (allowRedirects && code in intArrayOf(
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307, 308
            ) && redirectsLeft-- > 0
        ) {
            val location = conn.getHeaderField("Location")
            if (location != null) {
                currentUrl = URL(URL(currentUrl), location).toString()
                conn.disconnect()
                conn = URL(currentUrl).openConnection() as HttpURLConnection
                continue
            }
        }

        val responseCookies = CookieJar()
        conn.headerFields["Set-Cookie"]?.forEach { raw ->
            val first = raw.split(";")[0]
            val idx = first.indexOf('=')
            if (idx > 0) responseCookies[first.substring(0, idx)] = first.substring(idx + 1)
        }

        val content = if (stream || method == "HEAD") {
            ByteArray(0)
        } else {
            try {
                val input = if (code >= 400) conn.errorStream else conn.inputStream
                val bytes = input?.let { inp ->
                    val buf = ByteArrayOutputStream()
                    inp.copyTo(buf)
                    inp.close()
                    buf.toByteArray()
                } ?: ByteArray(0)
                if ("gzip".equals(conn.contentEncoding, true)) {
                    try {
                        GZIPInputStream(bytes.inputStream()).readBytes()
                    } catch (e: Exception) {
                        bytes
                    }
                } else bytes
            } catch (e: Exception) {
                ByteArray(0)
            }
        }

        val headerMap = mutableMapOf<String, String>()
        conn.headerFields?.forEach { (k, v) ->
            if (k != null && v != null) headerMap[k] = v.joinToString(", ")
        }

        val finalUrl = conn.url?.toString() ?: currentUrl
        val resp = Response(code, finalUrl, headerMap, responseCookies, content, conn)
        if (!stream) conn.disconnect()
        return resp
    }
}

private val mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()

fun get(
    url: String,
    params: Map<String, String>? = null,
    headers: Map<String, String>? = null,
    data: Any? = null,
    json: Any? = null,
    timeout: Double = 30.0,
    allowRedirects: Boolean = true,
    stream: Boolean = false,
    cookies: Map<String, String>? = null,
) = request("GET", url, params, headers, data, json, timeout, allowRedirects, stream, cookies)

fun post(
    url: String,
    params: Map<String, String>? = null,
    headers: Map<String, String>? = null,
    data: Any? = null,
    json: Any? = null,
    timeout: Double = 30.0,
    allowRedirects: Boolean = true,
    stream: Boolean = false,
    cookies: Map<String, String>? = null,
) = request("POST", url, params, headers, data, json, timeout, allowRedirects, stream, cookies)

fun put(
    url: String,
    params: Map<String, String>? = null,
    headers: Map<String, String>? = null,
    data: Any? = null,
    json: Any? = null,
    timeout: Double = 30.0,
    allowRedirects: Boolean = true,
    stream: Boolean = false,
    cookies: Map<String, String>? = null,
) = request("PUT", url, params, headers, data, json, timeout, allowRedirects, stream, cookies)

fun delete(
    url: String,
    params: Map<String, String>? = null,
    headers: Map<String, String>? = null,
    data: Any? = null,
    json: Any? = null,
    timeout: Double = 30.0,
    allowRedirects: Boolean = true,
    stream: Boolean = false,
    cookies: Map<String, String>? = null,
) = request("DELETE", url, params, headers, data, json, timeout, allowRedirects, stream, cookies)

fun head(
    url: String,
    params: Map<String, String>? = null,
    headers: Map<String, String>? = null,
    timeout: Double = 30.0,
    allowRedirects: Boolean = true,
    cookies: Map<String, String>? = null,
) = request("HEAD", url, params, headers, null, null, timeout, allowRedirects, false, cookies)

fun patch(
    url: String,
    params: Map<String, String>? = null,
    headers: Map<String, String>? = null,
    data: Any? = null,
    json: Any? = null,
    timeout: Double = 30.0,
    allowRedirects: Boolean = true,
    stream: Boolean = false,
    cookies: Map<String, String>? = null,
) = request("PATCH", url, params, headers, data, json, timeout, allowRedirects, stream, cookies)
