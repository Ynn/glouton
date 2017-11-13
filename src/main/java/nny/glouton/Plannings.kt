package nny.glouton

import com.google.common.cache.CacheBuilder
import com.google.common.cache.LoadingCache
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
import com.sun.corba.se.impl.orbutil.graph.Graph
import javax.cache.integration.CacheLoader




data class Planning(val name: String, val url: URL) {
    private val logger = KotlinLogging.logger {}

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

//        var eventCache: LoadingCache<String, String> = CacheBuilder.newBuilder()
//                .maximumSize(1000)
//                .expireAfterWrite(10, TimeUnit.MINUTES)
//                .build<String, String>(object : CacheLoader<String, String> {
//                    override fun load(key: String): String {
//                        return ""
//                    }
//                })

    }



    fun getCalendar(): Calendar {
        if(!calendarCache.containsKey(name)) {
            val builder = CalendarBuilder()
            val calendar = builder.build(url.openStream())
            calendarCache.put(name, calendar);
        }
        return calendarCache.get(name);
    }


    fun getEventNow() : String?{
        val calendar = this.getCalendar()
        val today = java.util.Calendar.getInstance()

        // create a period starting now with a duration of one (1) day..
        val period = Period(DateTime(today.time), Dur(700, 0, 0, 1))
        val filter = Filter(PeriodRule<Component>(period))
        val eventsNow = filter.filter(calendar.getComponents<CalendarComponent>(Component.VEVENT) as Collection<Component>?)

        var result = mutableListOf<String>()
        if (eventsNow.isNotEmpty()) {
            for(component in eventsNow){
                val event = component.getProperty<Property>("SUMMARY")?.value?:component.getProperty<Property>("DESCRIPTION")?.value?:"?";
                result.add(event)
            }
        }

        return result.firstOrNull();
    }
}


fun main(args: Array<String>) {
    val plannings = Config.sites["plannings"]?.plannings()?: listOf<Planning>()
    println(plannings)
    for (planning in plannings) {
        println(planning.getEventNow())
    }
}