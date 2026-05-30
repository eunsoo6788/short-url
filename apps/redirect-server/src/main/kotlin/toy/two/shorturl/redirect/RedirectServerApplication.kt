package toy.two.shorturl.redirect

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["toy.two.shorturl"])
class RedirectServerApplication

fun main(args: Array<String>) {
    runApplication<RedirectServerApplication>(*args)
}
