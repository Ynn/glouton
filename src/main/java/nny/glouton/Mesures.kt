package nny.glouton

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import mu.KotlinLogging
import java.net.URL
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureNanoTime


val mapperXML = XmlMapper().registerModule(KotlinModule())

fun URL.asyncToString(handler: (AsyncResult<String>) -> Unit) {
    try {
        val port = port.takeIf { port > 0 } ?: 80
        MainVerticle.webclient!!.get(port, this.host, this.path).send { ar ->
            if (ar.succeeded()) {
                val code = ar.result().statusCode()
                if (code == 200) {
                    // Obtain response
                    val response = ar.result()
                    handler(Future.succeededFuture(response.body().toString()))
                } else {
                    handler(Future.failedFuture("unsuccessful : ${code} - ${ar.result().statusMessage() ?: ""} "))

                }
            } else {
                handler(Future.failedFuture(ar.cause()))
            }
        }
    } catch (t: Throwable) {
        handler(Future.failedFuture(t))
    }
}


fun <T : Any> URL.asyncReadXml(clazz: Class<T>, handler: (AsyncResult<T>) -> Unit) {
    this.asyncToString {
        try {
            if (it.succeeded()) {
                handler(Future.succeededFuture(mapperXML.readValue(it.result(), clazz)))
            } else {
                handler(Future.failedFuture(it.cause()))
            }
        } catch (t: Throwable) {
            handler(Future.failedFuture(t))
        }
    }
}

data class MesureConfig(val type: String, val url: URL) {

    private val logger = KotlinLogging.logger {}

    val mapper: MesureTypeMapper<*> by lazy {
        Class.forName(type).newInstance() as MesureTypeMapper<*>;
    }

    fun value(block: (AsyncResult<out Mesure<out Any?>>) -> Unit) {
        mapper.from(url, block)
    }

    fun valueAsync(maxRetry: AtomicInteger = 1.atom(), handler: (AsyncResult<Any?>) -> Unit): Unit = value {
        if (maxRetry.decrementAndGet() >= 0) {
            val value = if (it.succeeded()) {
                it.result()?.value()
            } else {
                logger.warn { "failed to read : cause  ${it.cause()}" }
                null
            }
            if (value == null) {
                logger.info { "retry $maxRetry" }
                valueAsync(maxRetry, handler)
            } else {
                handler(Future.succeededFuture(value))
            }
        } else {
            handler(Future.failedFuture("reach max retry"));
        }
    }

}


interface MesureTypeMapper<T> {
    fun from(url: URL, block: (AsyncResult<Mesure<T>>) -> Unit)
}

abstract class Mesure<R> {
    abstract fun value(): R?

    var unknownFields: MutableMap<String, String> = HashMap()

    // Capture all other fields that Jackson do not match other members
    @JsonAnyGetter
    fun otherFields(): Map<String, String> {
        return unknownFields
    }

    @JsonAnySetter
    fun setOtherField(name: String, value: String) {
        unknownFields.put(name, value)
    }
}



