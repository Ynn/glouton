package nny.glouton

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import java.io.File
import java.net.URL


@JsonIgnoreProperties(ignoreUnknown = true)
data class SiteConfig(val name: String, val period: Long = 5, val plannings: Map<String, URL> = mapOf(), val measures: Map<String, MesureConfig> = mapOf()) {
    val dataDir by lazy { "./data/" + name }
    fun plannings() = plannings.map { Planning(it.key, it.value) }
}

data class Config(val port: Int = 8080, val storeCSV: Boolean = false, val mqtt: MQTTConfig, val influx: InfluxConfig) {
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
    println(Config.server.influx)

    println(Config.server.mqtt)
    println(Config.server.storeCSV)
    println(Config.server.port)
    println(Config.sites)

    for (site in Config.sites.values) {
        for (name in site.measures.keys) {
            println("try $name")
            site.measures[name]?.value { v ->
                println("$name = ${v}")
            }
        }

    }

}