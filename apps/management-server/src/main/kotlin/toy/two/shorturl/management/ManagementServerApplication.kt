package toy.two.shorturl.management

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["toy.two.shorturl"])
class ManagementServerApplication

fun main(args: Array<String>) {
    runApplication<ManagementServerApplication>(*args)
}
