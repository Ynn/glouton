package nny.glouton.mapper

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import nny.glouton.Mesure
import nny.glouton.MesureTypeMapper
import nny.glouton.asyncReadXml
import java.net.URL
import kotlin.system.measureNanoTime

open class ObixIntMapper : MesureTypeMapper<Int> {
    override fun from(url: URL, block: (AsyncResult<Mesure<Int>>) -> Unit) {
        url.asyncReadXml(ObixInt::class.java) {
            try {
                if (it.succeeded()) {
                    block(Future.succeededFuture(it.result()))
                } else {
                    block(Future.failedFuture(it.cause()))
                }
            } catch (t: Throwable) {
                block(Future.failedFuture(t))
            }
        }
    }
}

open class ObixRealMapper : MesureTypeMapper<Double> {
    override fun from(url: URL, block: (AsyncResult<Mesure<Double>>) -> Unit) {
        url.asyncReadXml(ObixReal::class.java) {
            try {
                if (it.succeeded()) {
                    block(Future.succeededFuture(it.result()))
                } else {
                    block(Future.failedFuture(it.cause()))
                }
            } catch (t: Throwable) {
                block(Future.failedFuture(t))
            }
        }
    }
}


data class ObixInt(@JacksonXmlProperty(localName = "val") val value: Int? = null) : Mesure<Int>() {
    override fun value(): Int? = value
}

data class ObixReal(@JacksonXmlProperty(localName = "val") val value: Double? = null) : Mesure<Double>() {
    override fun value(): Double? = value
}
