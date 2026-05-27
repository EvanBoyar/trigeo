package com.trigeo.app.map

import java.io.File
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tiny localhost HTTP server that serves files out of a directory.
 *
 * MapLibre's offline downloader and main map renderer both go through the
 * native HTTP fetcher, which only understands http:// and https:// URLs.
 * `asset://` and `file://` are rejected with "Unable to parse resourceUrl",
 * so the style JSON has to be reachable over HTTP. This server binds to
 * loopback only (127.0.0.1) on a chosen port and answers GETs for the
 * MapLibre style files.
 *
 * The server runs on a daemon thread. It is best-effort: if the device
 * kills our process, it goes with us.
 */
class LocalStyleServer(private val stylesDir: File) {

    @Volatile var port: Int = 0
        private set

    @Volatile private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)

    fun start(preferredPort: Int = 27800): Boolean {
        if (running.get()) return true
        // Bind to the IPv4 loopback explicitly; getLoopbackAddress() can
        // return [::1] on some Android builds and MapLibre's HTTP client
        // hits 127.0.0.1, not the IPv6 loopback.
        val loopback = Inet4Address.getByName("127.0.0.1")
        val socket = runCatching { ServerSocket(preferredPort, 16, loopback) }
            .getOrElse { ServerSocket(0, 16, loopback) }
        port = socket.localPort
        serverSocket = socket
        running.set(true)
        Thread({ serveLoop(socket) }, "TrigeoStyleServer").apply {
            isDaemon = true
            start()
        }
        return true
    }

    private fun serveLoop(socket: ServerSocket) {
        while (running.get()) {
            val client = runCatching { socket.accept() }.getOrNull() ?: return
            Thread {
                runCatching { handle(client) }
                runCatching { client.close() }
            }.apply { isDaemon = true }.start()
        }
    }

    private fun handle(client: Socket) {
        val input = client.getInputStream().bufferedReader()
        val firstLine = input.readLine() ?: return
        // Drain remaining headers
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
        }
        val parts = firstLine.split(' ')
        if (parts.size < 2 || parts[0] != "GET") {
            writeStatus(client, 400, "Bad Request")
            return
        }
        val path = parts[1].substringBefore('?').trimStart('/')
        if (!safePath(path)) {
            writeStatus(client, 400, "Bad path")
            return
        }
        val file = File(stylesDir, path)
        if (!file.exists() || !file.isFile) {
            writeStatus(client, 404, "Not Found")
            return
        }
        val bytes = file.readBytes()
        val out = client.getOutputStream()
        out.write(
            (
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
                ).toByteArray()
        )
        out.write(bytes)
        out.flush()
    }

    private fun safePath(path: String): Boolean =
        path.matches(Regex("^[a-zA-Z0-9._-]+$"))

    private fun writeStatus(client: Socket, code: Int, msg: String) {
        client.getOutputStream().write(
            (
                "HTTP/1.1 $code $msg\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
                ).toByteArray()
        )
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}
