package toy.two.shorturl.management

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CreateShortLinkRequestSizeFilterTest {
    @Test
    fun `drain 한도가 0이면 큰 create 요청은 즉시 연결을 닫고 413으로 거절한다`() {
        val filter = CreateShortLinkRequestSizeFilter(maxBodyBytes = 16, maxDrainBodyBytes = 0)
        val request = MockHttpServletRequest("POST", "/api/v1/short-links").apply {
            setContent(ByteArray(32) { 'a'.code.toByte() })
        }
        val response = MockHttpServletResponse()
        var chainInvoked = false

        filter.doFilter(request, response) { _, _ ->
            chainInvoked = true
        }

        assertEquals(413, response.status)
        assertEquals("close", response.getHeader("Connection"))
        assertContains(response.contentAsString, "PAYLOAD_TOO_LARGE")
        assertFalse(chainInvoked)
    }

    @Test
    fun `create 요청 본문이 제한보다 조금 크면 drain 후 413으로 거절한다`() {
        val filter = CreateShortLinkRequestSizeFilter(maxBodyBytes = 16, maxDrainBodyBytes = 32)
        val request = MockHttpServletRequest("POST", "/api/v1/short-links").apply {
            setContent(ByteArray(32) { 'a'.code.toByte() })
        }
        val response = MockHttpServletResponse()
        var chainInvoked = false

        filter.doFilter(request, response) { _, _ ->
            chainInvoked = true
        }

        assertEquals(413, response.status)
        assertEquals(null, response.getHeader("Connection"))
        assertContains(response.contentAsString, "PAYLOAD_TOO_LARGE")
        assertFalse(chainInvoked)
    }

    @Test
    fun `create 요청 본문이 drain 한도보다 크면 연결을 닫고 413으로 거절한다`() {
        val filter = CreateShortLinkRequestSizeFilter(maxBodyBytes = 16, maxDrainBodyBytes = 32)
        val request = MockHttpServletRequest("POST", "/api/v1/short-links").apply {
            setContent(ByteArray(64) { 'a'.code.toByte() })
        }
        val response = MockHttpServletResponse()
        var chainInvoked = false

        filter.doFilter(request, response) { _, _ ->
            chainInvoked = true
        }

        assertEquals(413, response.status)
        assertEquals("close", response.getHeader("Connection"))
        assertContains(response.contentAsString, "PAYLOAD_TOO_LARGE")
        assertFalse(chainInvoked)
    }

    @Test
    fun `본문 길이를 모르는 create 요청도 읽는 중 제한을 넘으면 413으로 거절한다`() {
        val filter = CreateShortLinkRequestSizeFilter(maxBodyBytes = 16)
        val request = UnknownLengthRequest("POST", "/api/v1/short-links").apply {
            setContent(ByteArray(32) { 'a'.code.toByte() })
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response) { servletRequest, _ ->
            val inputStream = servletRequest.inputStream
            while (inputStream.read() >= 0) {
                // Drain the body to exercise the streaming guard.
            }
        }

        assertEquals(413, response.status)
        assertEquals("close", response.getHeader("Connection"))
        assertContains(response.contentAsString, "PAYLOAD_TOO_LARGE")
    }

    @Test
    fun `create 외 요청은 그대로 통과시킨다`() {
        val filter = CreateShortLinkRequestSizeFilter(maxBodyBytes = 16)
        val request = MockHttpServletRequest("GET", "/api/v1/short-links").apply {
            setContent(ByteArray(32) { 'a'.code.toByte() })
        }
        val response = MockHttpServletResponse()
        var chainInvoked = false

        filter.doFilter(request, response) { _, _ ->
            chainInvoked = true
        }

        assertEquals(200, response.status)
        assertEquals(true, chainInvoked)
    }

    private class UnknownLengthRequest(
        method: String,
        requestUri: String,
    ) : MockHttpServletRequest(method, requestUri) {
        override fun getContentLength(): Int = -1

        override fun getContentLengthLong(): Long = -1
    }
}
