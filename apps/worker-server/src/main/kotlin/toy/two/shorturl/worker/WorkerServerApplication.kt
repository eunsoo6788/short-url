package toy.two.shorturl.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication(scanBasePackages = ["toy.two.shorturl"])
class WorkerServerApplication

fun main(args: Array<String>) {
    runApplication<WorkerServerApplication>(*args)
}
