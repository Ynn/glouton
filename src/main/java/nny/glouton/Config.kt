package nny.glouton

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.vertx.core.Vertx
import io.vertx.ext.web.client.WebClient
import mu.KotlinLogging
import java.io.File
import java.net.URL


@JsonIgnoreProperties(ignoreUnknown = true)
data class SiteConfig(val name: String, val period: Long = 5, @JsonProperty("plannings") val planMap: Map<String, URL> = mapOf(), @JsonProperty("measures") val measuresMap: Map<String,Measure> = mapOf<String,Measure>()) {
    val dataDir by lazy { "./data/" + name }

    @get:JsonIgnore
    val plannings by lazy{
        planMap.map { Planning(it.key, it.value) }
    }

    @get:JsonIgnore
    val measures by lazy{
        measuresMap.map { it.value.name=it.key; it.value }
    }
}

data class Config(val port: Int = 8080, val storeCSV: Boolean = false, val mqtt: MQTTConfig, val influx: InfluxConfig, val types : Map<String,String> = mapOf()) {
    companion object {
        private val logger = KotlinLogging.logger {}
        val cacheDir = "./cache/"
        val sitesdir = File("./config/sites");
        val mapper = YAMLMapper().registerModule(KotlinModule()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        val server by lazy {
            mapper.readValue<Config>(File("./config/config.yml"))
        }
        val sites by lazy {
            sitesdir.listFiles().map {
                mapper.readValue<SiteConfig>(it)
            }.associateBy({ it.name }, { it })
        }

    }
}

fun main(args: Array<String>) {
    val vertx = Vertx.vertx()
    MainVerticle.webclient = WebClient.create(vertx)

    println(Config.server.influx)

    println(Config.server.mqtt)
    println(Config.server.storeCSV)
    println(Config.server.port)
    println(Config.sites)

    for (site in Config.sites.values) {
        for (measure in site.measures) {
            println("try $measure")
            measure?.valueRetry(maxRetry = 5.atom()) { res ->
                print("read ${site.name}/${measure.name} " )
                if (res.succeeded()) {
                    println(res.result())
                }else{
                    println(res.cause())
                }
            }
        }

    }

}