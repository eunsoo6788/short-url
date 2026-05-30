package toy.two.shorturl.shortlink.application

import org.junit.jupiter.api.Test
import toy.two.shorturl.shortlink.domain.ShortCode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TsidShortCodeGeneratorTest {
    @Test
    fun `TSID 문자열은 ShortCode 규칙을 통과한다`() {
        val code = TsidShortCodeGenerator().generate()

        assertEquals(code, ShortCode.from(code.value))
        assertTrue(code.value.length in 4..32)
    }

    @Test
    fun `연속 생성한 TSID code는 중복되지 않는다`() {
        val generator = TsidShortCodeGenerator()

        val codes = List(1_000) { generator.generate().value }

        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `TSID code는 시간 정렬 가능한 문자열이다`() {
        val generator = TsidShortCodeGenerator()

        val first = generator.generate().value
        Thread.sleep(2)
        val second = generator.generate().value

        assertTrue(first < second)
    }
}
