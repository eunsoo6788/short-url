package toy.two.shorturl.shortlink.application

import com.github.f4b6a3.tsid.TsidCreator
import toy.two.shorturl.shortlink.application.port.ShortCodeGenerator
import toy.two.shorturl.shortlink.domain.ShortCode

class TsidShortCodeGenerator : ShortCodeGenerator {
    override fun generate(): ShortCode =
        ShortCode.from(TsidCreator.getTsid().toString())
}
