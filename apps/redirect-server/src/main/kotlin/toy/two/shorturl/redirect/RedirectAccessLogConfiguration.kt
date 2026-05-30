package toy.two.shorturl.redirect

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import toy.two.shorturl.common.logging.ApiAccessLogJsonWriter
import java.nio.file.Path

@Configuration
@ConditionalOnProperty(prefix = "short-url.access-log", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class RedirectAccessLogConfiguration {
    @Bean
    fun apiAccessLogJsonWriter(
        objectMapper: ObjectMapper,
        @Value("\${short-url.access-log.path}") path: String,
    ): ApiAccessLogJsonWriter =
        ApiAccessLogJsonWriter(objectMapper, Path.of(path))

    @Bean
    fun webFluxApiAccessLoggingFilter(
        apiAccessLogJsonWriter: ApiAccessLogJsonWriter,
        @Value("\${spring.application.name}") applicationName: String,
        @Value("\${short-url.access-log.include-query-string:true}") includeQueryString: Boolean,
    ): WebFluxApiAccessLoggingFilter =
        WebFluxApiAccessLoggingFilter(applicationName, apiAccessLogJsonWriter, includeQueryString)
}
