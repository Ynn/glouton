package nny.glouton

import java.time.Instant

enum class EVENT_TYPES {
    UPDATE_VALUE,
    CONFIG_UPDATE,
    HISTORY_REQUEST
}

data class UpdateEvent(val siteName: String, val mesureName: String, val mesureValue: String) {
    val tags = mutableMapOf<String,String>()
    val timestamp = Instant.now().epochSecond
    fun tag(name:String, value:String){
        tags[name] = value;
    }
}

data class HistoryRequest(val siteName: String, val mesureName: String? = null)
