package app.limits.auth

import android.net.Uri
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CodexLoopbackServer private constructor(
    val port: Int,
    private val serverSocket: ServerSocket,
) : Closeable {
    val redirectUri: String = "http://127.0.0.1:$port/auth/callback"

    suspend fun awaitCallback(expectedState: String): Callback = withContext(Dispatchers.IO) {
        serverSocket.soTimeout = 15 * 60 * 1000

        while (!serverSocket.isClosed) {
            val socket = serverSocket.accept()
            socket.use { client ->
                val reader = client.getInputStream().bufferedReader()
                val requestLine = reader.readLine().orEmpty()
                val target = requestLine.split(' ').getOrNull(1).orEmpty()

                if (!target.startsWith("/auth/callback")) {
                    respond(client, 404, "Not found")
                    return@use
                }

                val uri = Uri.parse("http://127.0.0.1:$port$target")
                val code = uri.getQueryParameter("code")
                val state = uri.getQueryParameter("state")
                val oauthError = uri.getQueryParameter("error")

                when {
                    oauthError != null -> {
                        respond(
                            client,
                            400,
                            "Open Limits could not complete Codex sign-in. You can return to the app.",
                        )
                        error("Codex OAuth error: $oauthError")
                    }
                    code.isNullOrBlank() -> {
                        respond(client, 400, "Missing authorization code. Return to Open Limits.")
                        error("Codex OAuth callback omitted authorization code")
                    }
                    state != expectedState -> {
                        respond(client, 400, "OAuth state mismatch. Return to Open Limits and retry.")
                        error("Codex OAuth state did not match")
                    }
                    else -> {
                        respond(
                            client,
                            200,
                            "Codex is connected to Open Limits. You can close this tab and return to the app.",
                        )
                        return@withContext Callback(code = code, state = state)
                    }
                }
            }
        }

        error("Codex browser sign-in was cancelled")
    }

    override fun close() {
        runCatching { serverSocket.close() }
    }

    data class Callback(
        val code: String,
        val state: String,
    )

    companion object {
        fun bind(): CodexLoopbackServer {
            val address = InetAddress.getByName("127.0.0.1")
            var lastError: Throwable? = null

            for (port in listOf(1455, 1457)) {
                try {
                    val socket = ServerSocket()
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(address, port))
                    return CodexLoopbackServer(port, socket)
                } catch (error: Throwable) {
                    lastError = error
                }
            }

            throw IllegalStateException(
                "Could not open Codex OAuth callback ports 1455 or 1457",
                lastError,
            )
        }

        private fun respond(
            socket: java.net.Socket,
            status: Int,
            message: String,
        ) {
            val html = """
                <!doctype html>
                <html>
                  <head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                  <body style="font-family:sans-serif;padding:24px">
                    <h2>Open Limits</h2>
                    <p>${escapeHtml(message)}</p>
                  </body>
                </html>
            """.trimIndent()
            val body = html.toByteArray()
            val reason = when (status) {
                200 -> "OK"
                400 -> "Bad Request"
                else -> "Not Found"
            }

            val output = socket.getOutputStream()
            val headers = buildString {
                append("HTTP/1.1 $status $reason\r\n")
                append("Content-Type: text/html; charset=utf-8\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray()
            output.write(headers)
            output.write(body)
            output.flush()
        }

        private fun escapeHtml(value: String): String =
            value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
    }
}
