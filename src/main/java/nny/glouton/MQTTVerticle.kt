package nny.glouton

import io.netty.handler.codec.mqtt.MqttQoS
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.Json
import io.vertx.kotlin.mqtt.MqttClientOptions
import io.vertx.mqtt.MqttClient
import mu.KotlinLogging

data class MQTTConfig(val host: String = "localhost", val port: Int, val user: String = "", val pass: String = "")


class MQTTVerticle : io.vertx.core.AbstractVerticle() {
    private val logger = KotlinLogging.logger {}

    override fun start() {

        var options = MqttClientOptions().apply {
            keepAliveTimeSeconds = 2
            username = Config.server.mqtt.user
            password = Config.server.mqtt.pass
        }
        var consumer: MessageConsumer<String>

        var lastPong = System.currentTimeMillis()
        var client = MqttClient.create(vertx, options).pingResponseHandler {
            logger.trace { "PONG" }
            lastPong = System.currentTimeMillis()
        }
        client.closeHandler { println("MQTT connection closed") }

        var waitingTimeBeforeRetry = 2000L


        // connect to a server
        fun tryConnect(): Unit {
            logger.trace { "TRY CONNECT" }
            client.connect(Config.server.mqtt.port, Config.server.mqtt.host, { ch ->
                if (ch.succeeded()) {
                    consumer = vertx.eventBus().consumer<String>(EVENT_TYPES.UPDATE_VALUE.toString(), { message ->
                        val event = Json.decodeValue(message.body(), UpdateEvent::class.java)
                        val mqttTopic = "/${event.siteName}/${event.mesureName}"
                        client.publish(mqttTopic,
                                Buffer.buffer(event.mesureValue?.toString() ?: ""),
                                MqttQoS.AT_MOST_ONCE,
                                false,
                                false)
                    })

                    fun ping(): Unit {
                        logger.trace { "PING" }

                        vertx.setTimer(waitingTimeBeforeRetry) {
                            //if the timer was too long :
                            if (System.currentTimeMillis() - lastPong > 3 * waitingTimeBeforeRetry) {
                                logger.trace { "FAIL" }
                                //disconnect
                                client.disconnect()
                                consumer.unregister()
                                //plan for reconnection :
                                lastPong = System.currentTimeMillis()
                                vertx.setTimer(waitingTimeBeforeRetry) { tryConnect(); }
                            } else {
                                //else plan the check and ping :
                                vertx.setTimer(waitingTimeBeforeRetry) { ping(); }
                                client.ping()
                            }
                        }
                    }
                    ping()

                } else {
                    logger.warn { "Failed to connect to a server" }
                    val timer = vertx.setTimer(waitingTimeBeforeRetry) { tryConnect(); }
                    logger.warn { "register timer $timer" }
                }
            })
        }

        tryConnect()

    }


}

fun main(args: Array<String>) {
    val vertx = Vertx.vertx()
    vertx.deployVerticle(MQTTVerticle::class.java.canonicalName)
}