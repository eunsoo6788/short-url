package toy.two.shorturl.management

import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class CreateShortLinkRequestSizeFilter(
    private val maxBodyBytes: Long,
    private val maxDrainBodyBytes: Long = maxBodyBytes * 2,
) : OncePerRequestFilter() {
    init {
        require(maxBodyBytes > 0) { "maxBodyBytes must be greater than 0" }
        require(maxDrainBodyBytes >= 0) { "maxDrainBodyBytes must be greater than or equal to 0" }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != HttpMethod.POST.name() || request.requestURI != CREATE_SHORT_LINK_PATH

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.contentLengthLong > maxBodyBytes) {
            val shouldDrain = maxDrainBodyBytes > 0 && request.contentLengthLong <= maxDrainBodyBytes
            val closeConnection = !shouldDrain || !drain(request, request.contentLengthLong)
            reject(response, closeConnection)
            return
        }

        try {
            val guardedRequest = if (request.contentLengthLong < 0) {
                SizeLimitedHttpServletRequest(request, maxBodyBytes)
            } else {
                request
            }
            filterChain.doFilter(guardedRequest, response)
        } catch (ex: RequestBodyTooLargeException) {
            if (response.isCommitted) {
                throw ex
            }
            reject(response, closeConnection = true)
        }
    }

    private fun drain(request: HttpServletRequest, contentLength: Long): Boolean =
        runCatching {
            val buffer = ByteArray(DRAIN_BUFFER_BYTES)
            var remaining = contentLength
            val inputStream = request.inputStream
            while (remaining > 0) {
                val bytesToRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = inputStream.read(buffer, 0, bytesToRead)
                if (read < 0) {
                    return@runCatching true
                }
                remaining -= read
            }
            true
        }.getOrDefault(false)

    private fun reject(response: HttpServletResponse, closeConnection: Boolean) {
        val body = PAYLOAD_TOO_LARGE_BODY.toByteArray(StandardCharsets.UTF_8)
        response.resetBuffer()
        response.status = HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        if (closeConnection) {
            response.setHeader("Connection", "close")
        }
        response.setContentLength(body.size)
        response.outputStream.write(body)
        response.flushBuffer()
    }

    companion object {
        private const val CREATE_SHORT_LINK_PATH = "/api/v1/short-links"
        private const val DRAIN_BUFFER_BYTES = 8192
        private const val PAYLOAD_TOO_LARGE_BODY =
            """{"code":"PAYLOAD_TOO_LARGE","message":"Create short link request body is too large"}"""
    }
}

class RequestBodyTooLargeException(maxBodyBytes: Long) :
    IOException("Request body exceeds $maxBodyBytes bytes")

private class SizeLimitedHttpServletRequest(
    request: HttpServletRequest,
    private val maxBodyBytes: Long,
) : HttpServletRequestWrapper(request) {
    override fun getInputStream(): ServletInputStream =
        SizeLimitedServletInputStream(request.inputStream, maxBodyBytes)

    override fun getReader(): BufferedReader =
        BufferedReader(InputStreamReader(inputStream, charset()))

    private fun charset(): Charset =
        characterEncoding
            ?.let { Charset.forName(it) }
            ?: StandardCharsets.UTF_8
}

private class SizeLimitedServletInputStream(
    private val delegate: ServletInputStream,
    private val maxBodyBytes: Long,
) : ServletInputStream() {
    private var bytesRead = 0L

    override fun read(): Int {
        val value = delegate.read()
        if (value >= 0) {
            count(1)
        }
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = delegate.read(buffer, offset, length)
        if (count > 0) {
            count(count.toLong())
        }
        return count
    }

    override fun isFinished(): Boolean =
        delegate.isFinished

    override fun isReady(): Boolean =
        delegate.isReady

    override fun setReadListener(readListener: ReadListener) {
        delegate.setReadListener(readListener)
    }

    private fun count(count: Long) {
        bytesRead += count
        if (bytesRead > maxBodyBytes) {
            throw RequestBodyTooLargeException(maxBodyBytes)
        }
    }
}
