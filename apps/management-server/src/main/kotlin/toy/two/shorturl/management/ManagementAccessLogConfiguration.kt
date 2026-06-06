package toy.two.shorturl.management

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import tools.jackson.databind.ObjectMapper
import toy.two.shorturl.common.logging.ApiAccessLogJsonWriter
import java.nio.file.Path

@Configuration
@ConditionalOnProperty(prefix = "short-url.access-log", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ManagementAccessLogConfiguration {
    @Bean
    fun apiAccessLogJsonWriter(
        objectMapper: ObjectMapper,
        @Value("\${short-url.access-log.path}") path: String,
    ): ApiAccessLogJsonWriter =
        ApiAccessLogJsonWriter(objectMapper, Path.of(path))

    @Bean
    fun mvcApiAccessLoggingFilter(
        apiAccessLogJsonWriter: ApiAccessLogJsonWriter,
        @Value("\${spring.application.name}") applicationName: String,
        @Value("\${short-url.access-log.include-query-string:true}") includeQueryString: Boolean,
    ): FilterRegistrationBean<MvcApiAccessLoggingFilter> =
        FilterRegistrationBean(
            MvcApiAccessLoggingFilter(applicationName, apiAccessLogJsonWriter, includeQueryString),
        ).apply {
            addUrlPatterns("/*")
            order = Ordered.HIGHEST_PRECEDENCE
        }
}
