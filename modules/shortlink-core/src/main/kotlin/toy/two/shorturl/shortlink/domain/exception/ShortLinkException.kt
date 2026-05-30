package toy.two.shorturl.shortlink.domain.exception

sealed class ShortLinkException(message: String) : RuntimeException(message)

class InvalidOriginalUrlException(message: String) : ShortLinkException(message)

class InvalidShortCodeException(message: String) : ShortLinkException(message)

class ShortCodeAlreadyExistsException(message: String) : ShortLinkException(message)

class ShortCodeGenerationFailedException(message: String) : ShortLinkException(message)

class ShortLinkNotFoundException(message: String) : ShortLinkException(message)

class ExpiredShortLinkException(message: String) : ShortLinkException(message)
