package toy.two.shorturl.shortlink.domain

import toy.two.shorturl.shortlink.domain.exception.InvalidShortCodeException

@JvmInline
value class ShortCode private constructor(val value: String) {
    companion object {
        private val pattern = Regex("^[A-Za-z0-9_-]{4,32}$")

        fun from(value: String): ShortCode {
            val normalized = value.trim()

            if (!pattern.matches(normalized)) {
                throw InvalidShortCodeException("짧은 코드는 4~32자의 영문, 숫자, _, - 만 사용할 수 있습니다.")
            }

            return ShortCode(normalized)
        }
    }
}
