package app.limits.network

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class Http(private val client: OkHttpClient = OkHttpClient()) {
    data class Response(val code: Int, val body: String)

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): Response = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.get().build())
    }

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): Response = withContext(Dispatchers.IO) {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        execute(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.post(body).build())
    }

    suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): Response = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        execute(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.post(body).build())
    }

    private fun execute(request: Request): Response {
        client.newCall(request).execute().use { response ->
            return Response(response.code, response.body.string())
        }
    }
}

class HttpException(message: String) : IOException(message)
