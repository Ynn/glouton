package nny.glouton

import mu.KotlinLogging
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.filter.Filter
import net.fortuna.ical4j.filter.PeriodRule
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.component.CalendarComponent
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import org.ehcache.config.units.EntryUnit
import org.ehcache.config.units.MemoryUnit
import org.ehcache.expiry.Duration
import org.ehcache.expiry.Expirations
import org.ehcache.impl.config.persistence.CacheManagerPersistenceConfiguration
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit
import java.net.URLDecoder
import java.io.InputStream
import java.net.HttpURLConnection


data class Planning(val name: String, val url: URL) {
    private val logger = KotlinLogging.logger {}

    init{
        logger.trace{"CREATE NEW PLANNING $name:$url"}
    }


    companion object {
        val cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .with(CacheManagerPersistenceConfiguration(File(Config.cacheDir, "calendar.tmp")))
                .withCache("calendar", CacheConfigurationBuilder.newCacheConfigurationBuilder(String::class.java, net.fortuna.ical4j.model.Calendar::class.java,
                        ResourcePoolsBuilder.newResourcePoolsBuilder()
                                .heap(50, EntryUnit.ENTRIES)
                                .disk(50L, MemoryUnit.MB, true))
                        .withExpiry(Expirations.timeToLiveExpiration(Duration(12, TimeUnit.HOURS)))).build(true)

        val  calendarCache = cacheManager.getCache("calendar", String::class.java, net.fortuna.ical4j.model.Calendar::class.java);

        fun load(key : String): String {
            return ""
        }

    }

    fun getCalendar(): Calendar {
        if(!calendarCache.containsKey(name)) {
            val builder = CalendarBuilder()
            try {
                val calendar = builder.build(url.openStreamFollowRedirect())
                calendarCache.put(name, calendar);
            }catch (e:Exception){
                logger.error("FAILED TO READ : $url")
                e.printStackTrace()
                throw e
            }
        }
        return calendarCache.get(name);
    }


    var lastEvent : String? = null
    var lastEventTime : Long = 0
    private val cacheDuration = TimeUnit.MILLISECONDS.convert(10,TimeUnit.SECONDS)

    fun getEventNow() : String?{
        val elapsedTime =System.currentTimeMillis() - lastEventTime
        if(elapsedTime > cacheDuration) {
            val calendar = this.getCalendar()
            val today = java.util.Calendar.getInstance()

            // create a period starting now with a duration of one (1) day..
            val period = Period(DateTime(today.time), Dur(0, 0, 0, 1))
            val filter = Filter(PeriodRule<Component>(period))
            val eventsNow = filter.filter(calendar.getComponents<CalendarComponent>(Component.VEVENT) as Collection<Component>?)

            var result = mutableListOf<String>()
            if (eventsNow.isNotEmpty()) {
                for (component in eventsNow) {
                    val event = component.getProperty<Property>("SUMMARY")?.value ?: component.getProperty<Property>("DESCRIPTION")?.value ?: "?";
                    result.add(event)
                }
            }
            lastEventTime = System.currentTimeMillis()
            lastEvent = result.firstOrNull()
            logger.trace {"RECOMPUTE $lastEvent elapsed = ${elapsedTime}"}
        }else{
            logger.trace {"RETURN CACHE $lastEvent elapsed = ${elapsedTime}"}
        }
        return lastEvent
    }
}


fun main(args: Array<String>) {
    println(URL("http://labo.domo.4x.re/planning/922.ics").openStreamFollowRedirect())

    println(Config.sites)
    val plannings = Config.sites["istic-labo"]?.plannings?: listOf<Planning>()
    println(plannings)
    for (planning in plannings) {
        try {
            println(planning.getEventNow())
        }catch (e:Exception){
            e.printStackTrace()
        }

    }
}