package nny.glouton

import io.vertx.core.json.Json
import mu.KotlinLogging
import org.influxdb.InfluxDB
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.BatchPoints
import org.influxdb.dto.Point
import java.util.concurrent.TimeUnit


data class InfluxConfig(val host: String = "localhost", val port: Int, val user: String = "", val pass: String = "")


class InfluxVerticle : io.vertx.core.AbstractVerticle() {
    private val logger = KotlinLogging.logger {}
    private val DB_NAME = "timeseries"
    var waitingTimeBeforeRetry = 2000L

    inline fun getTableName(site: String, mesure: String) = (site + "$" + mesure).replace(Regex("[^A-Za-z0-9\$]"), "_")

    override fun start() {
        logger.info { "START INFLUX VERTICLE" }
        fun connect() {
            logger.info { "try to connect" }

            try {
                val influxDB = InfluxDBFactory.connect("${Config.server.influx.host}:${Config.server.influx.port}", Config.server.influx.user, Config.server.influx.pass)
                if (influxDB != null) {
                    val dbName = DB_NAME
                    influxDB.createDatabase(dbName)

                    val batchPoints = BatchPoints
                            .database(dbName)
                            .tag("async", "true")
                            .retentionPolicy("autogen")
                            .consistency(InfluxDB.ConsistencyLevel.ALL)
                            .build()
                    val consumer = vertx.eventBus().consumer<String>(EVENT_TYPES.UPDATE_VALUE.toString(), { message ->
                        try {
                            val event = Json.decodeValue(message.body(), UpdateEvent::class.java)
                            val name = getTableName(event.siteName, event.mesureName)
                            val point = Point.measurement(event.siteName)
                                    .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                                    .addField(event.mesureName, event.mesureValue.toDouble())
                            for (tag in event.tags) {
                                point.tag(tag.key, tag.value);
                            }
                            //logger.info { "write point to influx : $dbName" }

                            influxDB.write(dbName, "autogen", point.build());
                        }catch (e:Exception){
                            logger.error{"FAILED Writing : ${e.message}"}
                        }
                    })
                }
            } catch (e: Exception) {
                vertx.setTimer(waitingTimeBeforeRetry) {
                    logger.error {"FAILED : ${e.message} TRY TO RECONNECT in 2s"}
                    connect();
                }
            }
        }
        connect()
    }
}