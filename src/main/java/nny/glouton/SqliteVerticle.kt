package nny.glouton

import com.opencsv.CSVWriter
import io.vertx.core.Future
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import mu.KotlinLogging
import java.util.concurrent.CountDownLatch
import java.io.StringWriter




//SELECT * FROM SERIES JOIN SOURCES WHERE SOURCES.source_id=SERIES.source_id
//SELECT * FROM SERIES JOIN SOURCES WHERE SOURCES.source_id=SERIES.source_id AND name="/istic-labo-ext/extTemperature";
//select avg(lux2.timestamp),lux1.value,lux2.value value istic_labo_ext$lux1 lux1 inner join istic_labo_ext$lux2 lux2 on lux1.timestamp/10=lux2.timestamp/10 GROUP BY lux1.timestamp;


class SqliteVerticle : io.vertx.core.AbstractVerticle() {
    private val logger = KotlinLogging.logger {}
    val SOURCES_TABLE = "SOURCES"
    val SERIES_TABLE = "SERIES"
    val BaseOptions = JsonObject()
            .put("url", "jdbc:sqlite:data/timeseries.db")
            .put("driver_class", "org.sqlite.JDBC")

    inline fun getTableName(site: String, mesure: String) = (site + "$" + mesure).replace(Regex("[^A-Za-z0-9\$]"), "_")
    inline fun getViewName(site: String) = site.replace(Regex("[^A-Za-z0-9\$]"), "_")


    fun io.vertx.ext.sql.SQLConnection.execute(requestBatch: List<String>){
        var futures = mutableListOf<Future<Void>>()
        for (request in requestBatch) {
            val wait = CountDownLatch(1)
            this.execute(request){
                wait.countDown()
                if (it.succeeded()) {
                    System.out.println("succeeded : $request");
                } else {
                    println("FAILED ${it.cause()}")
                }
            }
        }
    }

    fun initialize(connection: io.vertx.ext.sql.SQLConnection){
        val createBatch = mutableListOf<String>()
        val viewBatch = mutableListOf<String>()
        val dropViewBatch = mutableListOf<String>()

        for (site in Config.sites.values) {
            val selects = mutableListOf<String>()
            for (measure in site.measures) {
                val tableName = getTableName(site.name, measure.name)
                val request = """CREATE TABLE ${tableName}(
                            |series_id INTEGER PRIMARY KEY ASC,
                            |timestamp INTEGER,
                            |value REAL
                            |);""".trimMargin();
                createBatch.add(request)
                selects.add("SELECT *, '${measure.name}' as Name FROM ${tableName}")
            }

            if (selects.isNotEmpty()) {
                val dropView = """drop view ${getViewName(site.name)} """
                val createView = """create view ${getViewName(site.name)} as ${selects.joinToString(" UNION ALL ")};"""
                dropViewBatch+=dropView
                viewBatch+=createView
            }
        }
        //FIRST CREATE ALL BASES :
        connection.execute(createBatch)
        connection.execute(dropViewBatch)
        connection.execute(viewBatch)

    }

    override fun start() {

        var consumer: MessageConsumer<String>
        val client = JDBCClient.createShared(vertx, BaseOptions).getConnection { conn ->
            if (conn.failed()) {
                logger.error { "Connection to sqlite failed" }
            } else {
                val connection = conn.result();

                initialize(connection = connection);

                consumer = vertx.eventBus().consumer<String>(EVENT_TYPES.UPDATE_VALUE.toString()) { message ->
                    val event = Json.decodeValue(message.body(), UpdateEvent::class.java)
                    var params = json {
                        array(event.timestamp,
                                event.mesureValue.toDouble()
                        )
                    }
                    val tableName = getTableName(event.siteName, event.mesureName)
                    connection.queryWithParams("INSERT OR IGNORE INTO ${tableName} (timestamp,value) VALUES (?,?)", params) {
                        if (it.succeeded()) {
                            logger.info { "inserted ${event.timestamp}:/${event.siteName}/${event.mesureName} = ${message.body()}" }
                        } else {
                            logger.info { it.cause() }
                        }

                    }
                }

                val historyConsumer = vertx.eventBus().consumer<String>(EVENT_TYPES.HISTORY_REQUEST.toString()) { message ->
                    val event = Json.decodeValue(message.body(), HistoryRequest::class.java)


                    val table =if(event.mesureName!=null){
                        getTableName(event.siteName, event.mesureName)
                    }else{
                        getViewName(event.siteName)
                    }
                    connection.query("SELECT *  FROM ${table}"){res ->
                        if (res.succeeded()) {
                            val resultSet = res.result()
                            val reply = """
                                |${resultSet.columnNames.joinToString (",")}
                                |${resultSet.results.map { it.joinToString (",") }.joinToString ("\n")}
                                """.trimMargin()
                            message.reply(reply)
                        } else {
                            println("HISTORY REQUEST FAILED ${res.cause()}")

                        }
                    }
                }

            }

        }
    }
}


