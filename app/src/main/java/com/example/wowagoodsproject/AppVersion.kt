package com.example.wowagoodsproject

/**
 * [latest] 가 [current] 보다 높은 버전인지. "2.10" 은 "2.9" 보다 높고, "2.5" 와 "2.5.0" 은 같다.
 * 앞의 v 는 떼고 본다. 숫자로 읽을 수 없는 버전이면 예전처럼 다르기만 하면 새 버전으로 본다.
 */
fun isNewerVersion(latest: String, current: String): Boolean {
    fun parts(version: String) = version.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() }
    val l = parts(latest)
    val c = parts(current)
    if (l.any { it == null } || c.any { it == null }) return latest.trim() != current.trim()
    for (i in 0 until maxOf(l.size, c.size)) {
        val a = l.getOrNull(i) ?: 0
        val b = c.getOrNull(i) ?: 0
        if (a != b) return a > b
    }
    return false
}
