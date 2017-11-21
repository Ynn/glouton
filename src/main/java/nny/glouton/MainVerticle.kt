package nny.glouton

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.vertx.core.*
import io.vertx.core.json.Json
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import io.vertx.core.json.JsonObject




@Suppress("unused")
public class MainVerticle : AbstractVerticle() {
    private val logger = KotlinLogging.logger {}

    companion object {
        var webclient: WebClient? = null;

    }

    override fun start(startFuture: Future<Void>) {
        println(Config)
        Json.mapper.registerModule(KotlinModule())
        vertx.deployVerticle(MQTTVerticle::class.java.canonicalName)
        vertx.deployVerticle(SqliteVerticle::class.java.canonicalName)
        vertx.deployVerticle(InfluxVerticle::class.java.canonicalName)
        vertx.deployVerticle(RESTVerticle::class.java.canonicalName)
        webclient = WebClient.create(vertx)


        val executor = Executors.newScheduledThreadPool(20);

        for (site in Config.sites.values) {
            for (measure in site.measures) {
                fun readValue(maxRetry: AtomicInteger = AtomicInteger(5), handler: (AsyncResult<Any?>) -> Unit): Unit = measure.value {
                    if (maxRetry.decrementAndGet() > 0) {
                        val value = if (it.succeeded()) {
                            it.result()
                        } else {
                            logger.warn { "failed to read /${site.name}/${measure.name} : cause  ${it.cause()}" }
                            null
                        }
                        if (value == null) {
                            logger.info { "reschedule /${site.name}/${measure.name} $maxRetry" }
                            readValue(maxRetry, handler)
                        } else {
                            val event = UpdateEvent(site.name, mesureName = measure.name, mesureValue = value.toString())
                            vertx.eventBus().publish(EVENT_TYPES.UPDATE_VALUE.toString(), Json.encode(event))
                            handler(Future.succeededFuture(value));
                        }
                    } else {
                        handler(Future.failedFuture("[maxRetry = $maxRetry] failed to read /${site.name}/${measure.name} : cause  ${it.cause()}"));
                    }
                }
                logger.info { "Schedule read periodic for ${site.name} : ${site.period}s" }
                fun readPeriodic(): Unit = measure.valueRetry(maxRetry = 5.atom()) { res ->
                    if (res.succeeded()) {
                        val value = res.result();
                        val event = UpdateEvent(site.name, mesureName = measure.name, mesureValue = value.toString())
                        for (planning in site.plannings) {
                            val now = planning.getEventNow()
                            if (now != null) {
                                event.tag(planning.name, now);
                            }
                        }
                        vertx.eventBus().publish(EVENT_TYPES.UPDATE_VALUE.toString(), Json.encode(event))
                    }
                    vertx.setTimer(TimeUnit.MILLISECONDS.convert(site.period, TimeUnit.SECONDS)) {
                        readPeriodic();
                    }
                }
                readPeriodic()
            }
        }
    }
}
//val vertx = Vertx.vertx()
//val client = WebClient.create(vertx)

fun main(args: Array<String>) {
    val vertx = Vertx.vertx()
    vertx.deployVerticle(MainVerticle::class.java.canonicalName)
}