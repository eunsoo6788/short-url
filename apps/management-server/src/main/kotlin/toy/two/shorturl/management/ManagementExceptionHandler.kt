package toy.two.shorturl.management

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import toy.two.shorturl.common.ErrorResponse
import toy.two.shorturl.shortlink.domain.exception.ExpiredShortLinkException
import toy.two.shorturl.shortlink.domain.exception.InvalidOriginalUrlException
import toy.two.shorturl.shortlink.domain.exception.InvalidShortCodeException
import toy.two.shorturl.shortlink.domain.exception.ShortCodeAlreadyExistsException
import toy.two.shorturl.shortlink.domain.exception.ShortCodeGenerationFailedException
import toy.two.shorturl.shortlink.domain.exception.ShortLinkNotFoundException

@RestControllerAdvice
class ManagementExceptionHandler {
    @ExceptionHandler(InvalidOriginalUrlException::class, InvalidShortCodeException::class)
    fun handleBadRequest(exception: RuntimeException): ResponseEntity<ErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableMessage(exception: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        if (exception.hasCause<RequestBodyTooLargeException>()) {
            return error(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "PAYLOAD_TOO_LARGE",
                RuntimeException("Create short link request body is too large"),
            )
        }

        return error(
            HttpStatus.BAD_REQUEST,
            "BAD_REQUEST",
            RuntimeException("Malformed request body"),
        )
    }

    @ExceptionHandler(ShortCodeAlreadyExistsException::class)
    fun handleConflict(exception: ShortCodeAlreadyExistsException): ResponseEntity<ErrorResponse> =
        error(HttpStatus.CONFLICT, "SHORT_CODE_ALREADY_EXISTS", exception)

    @ExceptionHandler(ShortLinkNotFoundException::class)
    fun handleNotFound(exception: ShortLinkNotFoundException): ResponseEntity<ErrorResponse> =
        error(HttpStatus.NOT_FOUND, "SHORT_LINK_NOT_FOUND", exception)

    @ExceptionHandler(ExpiredShortLinkException::class)
    fun handleExpired(exception: ExpiredShortLinkException): ResponseEntity<ErrorResponse> =
        error(HttpStatus.GONE, "SHORT_LINK_EXPIRED", exception)

    @ExceptionHandler(ShortCodeGenerationFailedException::class)
    fun handleGenerationFailed(exception: ShortCodeGenerationFailedException): ResponseEntity<ErrorResponse> =
        error(HttpStatus.SERVICE_UNAVAILABLE, "SHORT_CODE_GENERATION_FAILED", exception)

    private fun error(
        status: HttpStatus,
        code: String,
        exception: RuntimeException,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status)
            .body(
                ErrorResponse(
                    code = code,
                    message = exception.message ?: status.reasonPhrase,
                ),
            )
}

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) {
            return true
        }
        current = current.cause
    }
    return false
}
