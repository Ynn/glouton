package nny.glouton

import java.util.concurrent.atomic.AtomicInteger


inline fun repeatWhile(cond: () -> Boolean, maxTry: Int, action: (Int) -> Unit) {
    var i = 0
    while (i++ < maxTry && cond())
        action(maxTry)
}

fun Int.atom() = AtomicInteger(this)