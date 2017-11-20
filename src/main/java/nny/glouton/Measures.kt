package nny.glouton

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import groovy.util.Eval
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import mu.KotlinLogging
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger


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

data class Measure(val type: String, val url: URL, val transform: String? = null) {
    lateinit var name:String

    private val logger = KotlinLogging.logger {}

    val mapper: MeasureType<*> by lazy {
        //retrieve the associated class (manages alias)
        Class.forName(Config.server.types[type]?:type).newInstance() as MeasureType<*>;
    }

    /**
     * Retrieve the value and return it by calling the block function when the value is ready :
     */
    fun value(handler: (AsyncResult<out Any?>) -> Unit) {
        mapper.value(url){
            if (it.succeeded()) {
                var value = it.result()
                //handle and transform the value :
                if(value != null) {
                    try {
                        if(transform!=null){
                            value = Eval.x( value, transform)
                        }
                        handler(Future.succeededFuture(value))
                    }catch (e:Exception){
                        handler(Future.failedFuture(e))
                    }
                }else{
                    handler(Future.failedFuture(it.cause()))
                }
            }else{
                handler(Future.failedFuture(it.cause()))
            }
        }
    }

    /**
     * Retrieve the value and retry when failed until max has not been reached
     */
    fun valueRetry(maxRetry: AtomicInteger = 1.atom(), handler: (AsyncResult<Any?>) -> Unit): Unit = value {
        if (maxRetry.decrementAndGet() >= 0) {
            val value = if (it.succeeded()) {
                it.result()
            } else {
                logger.warn { "failed to read : cause  ${it.cause()}" }
                null
            }
            if (value == null) {
                logger.info { "retry $maxRetry" }
                //we need to try again :
                valueRetry(maxRetry, handler)
            } else {
                //the value can be given to the handler :
                handler(Future.succeededFuture(value))
            }
        } else {
            handler(Future.failedFuture("reach max retry $url"));
        }
    }

}


interface MeasureType<T> {
    fun value(url: URL, block: (AsyncResult<T>) -> Unit)
}



