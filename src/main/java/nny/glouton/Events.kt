package nny.glouton

import java.time.Instant

enum class EVENT_TYPES {
    UPDATE_VALUE,
    CONFIG_UPDATE
}

data class UpdateEvent(val siteName: String, val mesureName: String, val mesureValue: String) {
    val tags = mutableMapOf<String,String>()
    val timestamp = Instant.now().epochSecond
    fun tag(name:String, value:String){
        tags[name] = value;
    }
}
