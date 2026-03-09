package com.example.kftgcs.database.tlog

import androidx.room.TypeConverter

/**
 * Type converters for Tlog entities
 */
class TlogTypeConverters {

    @TypeConverter
    fun fromEventType(eventType: EventType): String {
        return eventType.name
    }

    @TypeConverter
    fun toEventType(eventType: String): EventType {
        return EventType.valueOf(eventType)
    }

    @TypeConverter
    fun fromEventSeverity(severity: EventSeverity): String {
        return severity.name
    }

    @TypeConverter
    fun toEventSeverity(severity: String): EventSeverity {
        return EventSeverity.valueOf(severity)
    }
}
