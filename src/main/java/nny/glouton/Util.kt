package nny.glouton

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger


inline fun repeatWhile(cond: () -> Boolean, maxTry: Int, action: (Int) -> Unit) {
    var i = 0
    while (i++ < maxTry && cond())
        action(maxTry)
}

fun Int.atom() = AtomicInteger(this)

fun URL.openStreamFollowRedirect() : InputStream {
    var location : String
    var conn : HttpURLConnection
    var resourceUrl = this
    var base: URL
    var next: URL
    var url = resourceUrl
    loop@while (true) {
        conn = url.openConnection() as HttpURLConnection
        conn.setConnectTimeout(15000)
        conn.setReadTimeout(15000)
        conn.setInstanceFollowRedirects(false)
        when (conn.getResponseCode()) {
            HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP -> {
                location = conn.getHeaderField("Location")
                location = URLDecoder.decode(location, "UTF-8")
                base = url
                next = URL(base, location)  // Deal with relative URLs
                url = next
                continue@loop;
            }
        }
        break
    }
    return conn.inputStream
}
