package com.example.wowagoodsproject.component

/**
 * 이모지·그림 기호를 뺀다. 장르·시리즈 이름은 글자만 받는다.
 * 빼는 것: 보조 평면 글자(대부분의 이모지), 기타 기호(☀ ♥ 등), 이모지 변형 선택자(U+FE0F 등), 이모지 이음 글자(U+200D),
 * 피부색·키캡 조합 문자. 한글·영문·숫자·일반 문장 부호는 그대로 둔다.
 */
fun String.withoutEmoji(): String {
    val out = StringBuilder(length)
    var i = 0
    while (i < length) {
        val cp = codePointAt(i)
        val n = Character.charCount(cp)
        val drop = cp > 0xFFFF ||
            Character.getType(cp) == Character.OTHER_SYMBOL.toInt() ||
            cp in 0xFE00..0xFE0F || cp == 0x200D || cp == 0x20E3
        if (!drop) out.appendCodePoint(cp)
        i += n
    }
    return out.toString()
}
