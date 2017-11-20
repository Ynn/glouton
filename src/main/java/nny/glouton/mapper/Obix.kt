package nny.glouton.mapper

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import nny.glouton.MeasureType
import nny.glouton.asyncReadXml
import java.net.URL




data class ObixInt(@JacksonXmlProperty(localName = "val") val value: Int? = null) {
    fun value(): Int? = value
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

open class ObixIntMapper : MeasureType<Int> {
    override fun value(url: URL, block: (AsyncResult<Int>) -> Unit) {
        url.asyncReadXml(ObixInt::class.java) {
            try {
                if (it.succeeded()) {
                    block(Future.succeededFuture(it.result().value))
                } else {
                    block(Future.failedFuture(it.cause()))
                }
            } catch (t: Throwable) {
                block(Future.failedFuture(t))
            }
        }
    }
}

open class ObixRealMapper : MeasureType<Double> {
    override fun value(url: URL, block: (AsyncResult<Double>) -> Unit) {
        url.asyncReadXml(ObixReal::class.java) {
            try {
                if (it.succeeded()) {
                    block(Future.succeededFuture(it.result().value))
                } else {
                    block(Future.failedFuture(it.cause()))
                }
            } catch (t: Throwable) {
                block(Future.failedFuture(t))
            }
        }
    }
}


data class ObixReal(@JacksonXmlProperty(localName = "val") val value: Double? = null) {
     fun value(): Double? = value
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