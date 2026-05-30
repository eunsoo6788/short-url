package toy.two.shorturl.management

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import toy.two.shorturl.shortlink.application.CreateShortLinkCommand
import toy.two.shorturl.shortlink.application.ShortLinkCreator
import toy.two.shorturl.shortlink.application.ShortLinkReader
import toy.two.shorturl.shortlink.domain.ShortLink
import java.time.Instant

@RestController
@RequestMapping("/api/v1/short-links")
class ShortLinkManagementController(
    private val creator: ShortLinkCreator,
    private val reader: ShortLinkReader,
    @Value("\${short-url.public-base-url:http://localhost:8081}")
    private val publicBaseUrl: String,
) {
    @PostMapping
    fun create(@RequestBody request: CreateShortLinkRequest): ResponseEntity<ShortLinkResponse> {
        val shortLink = creator.create(
            CreateShortLinkCommand(
                originalUrl = request.originalUrl,
                customCode = request.customCode,
                expiresAt = request.expiresAt,
            ),
        )

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(shortLink.toResponse())
    }

    @GetMapping("/{code}")
    fun get(@PathVariable code: String): ShortLinkResponse =
        reader.get(code).toResponse()

    @GetMapping
    fun findRecent(@RequestParam(defaultValue = "50") limit: Int): List<ShortLinkResponse> =
        reader.findRecent(limit).map { it.toResponse() }

    private fun ShortLink.toResponse(): ShortLinkResponse {
        val normalizedBaseUrl = publicBaseUrl.trimEnd('/')

        return ShortLinkResponse(
            code = code.value,
            originalUrl = originalUrl.value,
            shortUrl = "$normalizedBaseUrl/${code.value}",
            createdAt = createdAt,
            expiresAt = expiresAt,
            active = active,
        )
    }
}

data class CreateShortLinkRequest(
    val originalUrl: String = "",
    val customCode: String? = null,
    val expiresAt: Instant? = null,
)

data class ShortLinkResponse(
    val code: String,
    val originalUrl: String,
    val shortUrl: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val active: Boolean,
)
