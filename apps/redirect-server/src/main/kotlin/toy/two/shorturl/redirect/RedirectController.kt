package toy.two.shorturl.redirect

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import toy.two.shorturl.shortlink.application.RedirectResolver
import toy.two.shorturl.shortlink.domain.exception.ExpiredShortLinkException
import toy.two.shorturl.shortlink.domain.exception.InvalidShortCodeException
import toy.two.shorturl.shortlink.domain.exception.ShortLinkNotFoundException
import java.net.URI

@RestController
class RedirectController(
    private val redirectResolver: RedirectResolver,
) {
    @GetMapping("/{code}")
    fun redirect(@PathVariable code: String): Mono<ResponseEntity<Void>> =
        Mono.fromCallable { redirectResolver.resolve(code) }
            .subscribeOn(Schedulers.boundedElastic())
            .map<ResponseEntity<Void>> { resolution ->
                ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(URI.create(resolution.originalUrl))
                    .build()
            }
            .onErrorResume(ShortLinkNotFoundException::class.java) {
                Mono.just(ResponseEntity.notFound().build())
            }
            .onErrorResume(ExpiredShortLinkException::class.java) {
                Mono.just(ResponseEntity.status(HttpStatus.GONE).build())
            }
            .onErrorResume(InvalidShortCodeException::class.java) {
                Mono.just(ResponseEntity.badRequest().build())
            }
}
