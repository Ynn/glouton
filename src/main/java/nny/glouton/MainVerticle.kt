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


@Suppress("unused")
public class MainVerticle : AbstractVerticle() {
    private val logger = KotlinLogging.logger {}

    companion object {
        var webclient : WebClient? = null;

    }

    data class TestData(val name: String, val age: Int)

    override fun start(startFuture: Future<Void>) {
        println(Config)
        Json.mapper.registerModule(KotlinModule())
        vertx.deployVerticle(MQTTVerticle::class.java.canonicalName)
        vertx.deployVerticle(SqliteVerticle::class.java.canonicalName)
        vertx.deployVerticle(InfluxVerticle::class.java.canonicalName)
        webclient = WebClient.create(vertx)


        val executor = Executors.newScheduledThreadPool(20);

        for (site in Config.sites.values) {
            for (mesure in site.measures) {
                fun readValue(maxRetry: AtomicInteger = AtomicInteger(5), handler: (AsyncResult<Any?>) -> Unit): Unit = mesure.value.value {
                    if (maxRetry.decrementAndGet() > 0) {
                        val value = if (it.succeeded()) {
                            it.result()?.value()
                        } else {
                            logger.warn { "failed to read /${site.name}/${mesure.key} : cause  ${it.cause()}" }
                            null
                        }
                        if (value == null) {
                            logger.info { "reschedule /${site.name}/${mesure.key} $maxRetry" }
                            readValue(maxRetry, handler)
                        } else {
                            val event = UpdateEvent(site.name, mesureName = mesure.key, mesureValue = value.toString())
                            vertx.eventBus().publish(EVENT_TYPES.UPDATE_VALUE.toString(), Json.encode(event))
                            handler(Future.succeededFuture(value));
                        }
                    } else {
                        handler(Future.failedFuture("[maxRetry = $maxRetry] failed to read /${site.name}/${mesure.key} : cause  ${it.cause()}"));
                    }
                }
                logger.info { "Schedule read periodic for ${site.name} : ${site.period}s" }
                fun readPeriodic(): Unit = mesure.value.valueAsync(maxRetry = 5.atom()) { res ->
                    if (res.succeeded()) {
                        val value = res.result();
                        val event = UpdateEvent(site.name, mesureName = mesure.key, mesureValue = value.toString())
                        for(planning in site.plannings()){
                            val now = planning.getEventNow()
                            if(now!=null){
                                event.tag(planning.name,now);
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


        val router = createRouter()
        logger.info { "Start server on port : ${Config.server.port}" }
        vertx.createHttpServer()
                .requestHandler { router.accept(it) }
                .listen(config().getInteger("http.port", Config.server.port)) { result ->
                    if (result.succeeded()) {
                        startFuture.complete()
                    } else {
                        startFuture.fail(result.cause())
                    }
                }
    }

    private fun createRouter() = Router.router(vertx).apply {
        get("/").handler(handlerRoot)
        get("/config").handler(handlerConfig)
        get("/history/:name/:value").handler(handlerCSV)
        get("/now/:name/:value").handler(handlerNow)
    }

    //
    // Handlers

    val handlerRoot = Handler<RoutingContext> { req ->
        req.response().end("Welcome!")
    }

    val handlerConfig = Handler<RoutingContext> { req ->
        req.response().end(Json.encodePrettily(Config))
    }

    val handlerCSV = Handler<RoutingContext> { req ->
        try {
            val siteName = req.pathParam("name");
            val valueName = req.pathParam("value");
            val fileName = Config.sites[req.pathParam("name")]!!.dataDir + "/" + valueName + ".csv";
            req.response().end(File(fileName).readText())
        } catch (e: Exception) {
            req.response().setStatusCode(404).end("not found")
        }

    }

    val handlerNow = Handler<RoutingContext> { req ->
        try {
            val siteName = req.pathParam("name");
            val valueName = req.pathParam("value");
            println("Seek for " + valueName)
            if (Config.sites.containsKey(siteName) && Config.sites[siteName]?.measures?.containsKey(valueName) ?: false) {
                val value = Config.sites[siteName]?.measures!![valueName]?.value {
                    println("Callback")
                    if (it.succeeded()) {
                        println("success")
                        req.response().end(it.result().value().toString())
                    } else {
                        println("failed")
                        req.response().setStatusCode(404).end("not found")
                    }
                }
            } else {
                req.response().setStatusCode(404).end("not found")
            }
        } catch (e: Exception) {
            req.response().setStatusCode(404).end("not found")
        }
    }

}


//val vertx = Vertx.vertx()
//val client = WebClient.create(vertx)

fun main(args: Array<String>) {
    val vertx = Vertx.vertx()
    vertx.deployVerticle(MainVerticle::class.java.canonicalName)
}