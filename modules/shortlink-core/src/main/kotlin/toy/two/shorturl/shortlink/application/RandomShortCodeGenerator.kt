package toy.two.shorturl.shortlink.application

import toy.two.shorturl.shortlink.application.port.ShortCodeGenerator
import toy.two.shorturl.shortlink.domain.ShortCode
import java.security.SecureRandom

class RandomShortCodeGenerator(
    private val length: Int = 7,
    private val random: SecureRandom = SecureRandom(),
) : ShortCodeGenerator {
    private val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    override fun generate(): ShortCode {
        val value = buildString(length) {
            repeat(length) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }

        return ShortCode.from(value)
    }
}
