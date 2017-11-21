package nny.glouton

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import mu.KotlinLogging

class RESTVerticle: AbstractVerticle() {
     private val logger = KotlinLogging.logger {}

    override fun start(startFuture: Future<Void>) {
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
        get("/api").handler(handlerApi)
        //get("/config").handler(handlerConfig)
        get("/api/history/:name").handler(handlerCSV)
        get("/api/history/:name/file.csv").handler(handlerCSV)
        get("/api/history/:name/:value").handler(handlerCSV)
        get("/api/history/:name/:value/file.csv").handler(handlerCSV)
        get("/api/now/:name").handler(handlerNowApi)
        get("/api/now/:name/:value").handler(handlerNow)
    }

    //
    // Handlers

    val handlerRoot = Handler<RoutingContext> { req ->
        req.response().putHeader("content-type", "application/json").end(JsonObject().put("api", "${req.normalisedPath()}/api").encodePrettily())
    }

    val handlerApi = Handler<RoutingContext> { req ->
        req.response().putHeader("content-type", "application/json").end(
                JsonObject().put("history", "${req.normalisedPath()}/history").
                        put("now", "${req.normalisedPath()}/now")
                        .encodePrettily())
    }


    val handlerConfig = Handler<RoutingContext> { req ->
        req.response().end(Json.encodePrettily(Config))
    }

    val handlerCSV = Handler<RoutingContext> { req ->
        try {
            val siteName = req.pathParam("name");
            val valueName = req.pathParam("value");
            val event = HistoryRequest(siteName = siteName, mesureName = valueName)
            vertx.eventBus().send<String>(EVENT_TYPES.HISTORY_REQUEST.toString(), Json.encode(event)){
                if(it.succeeded()) {
                    req.response().end(it.result().body())
                }else{
                    req.response().end(it.cause().message)
                }
            }
        } catch (e: Exception) {
            req.response().setStatusCode(404).end("not found")
        }

    }

    val handlerNowApi = Handler<RoutingContext> { req ->
        val siteName = req.pathParam("name");

        req.response().end(Config.sites[siteName]?.measures?.map { """${it.name}: "${req.currentRoute().path}/${it.name}" """}?.joinToString (","))
    }

    val handlerNow = Handler<RoutingContext> { req ->
        try {
            val siteName = req.pathParam("name");
            val valueName = req.pathParam("value");


            val measure = Config.sites[siteName]?.measures?.find { it.name==valueName }
            println("Seek for " + valueName)
            if (measure!=null) {
                val value = measure?.value {
                    println("Callback")
                    if (it.succeeded()) {
                        println("success")
                        req.response().end(it.result().toString())
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
