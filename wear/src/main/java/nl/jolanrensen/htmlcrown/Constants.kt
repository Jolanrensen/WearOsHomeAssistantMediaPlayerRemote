package nl.jolanrensen.htmlcrown

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

operator fun JsonObject.set(key: String, value: JsonElement?) = add(key, value)

fun jsonObjectOf(vararg elements: Pair<String, *>): JsonObject {
    val json = JsonObject()
    elements.forEach {
        json[it.first] = when (it.second) {
            is JsonElement -> it.second as JsonElement
            is Boolean -> JsonPrimitive(it.second as Boolean)
            is Number -> JsonPrimitive(it.second as Number)
            is String -> JsonPrimitive(it.second as String)
            is Char -> JsonPrimitive(it.second as Char)
            else -> null
        }
    }
    return json
}

fun jsonArrayOf(elements: Collection<*>): JsonArray {
    val json = JsonArray()
    elements.forEach {
        json.add(
            when (it) {
                is JsonElement -> it
                is Boolean -> JsonPrimitive(it)
                is Number -> JsonPrimitive(it)
                is String -> JsonPrimitive(it)
                is Char -> JsonPrimitive(it)
                else -> null
            }
        )
    }
    return json
}

fun jsonArrayOf(vararg elements: Any): JsonArray {
    val json = JsonArray()
    elements.forEach {
        json.add(
            when (it) {
                is JsonElement -> it
                is Boolean -> JsonPrimitive(it)
                is Number -> JsonPrimitive(it)
                is String -> JsonPrimitive(it)
                is Char -> JsonPrimitive(it)
                else -> null
            }
        )
    }
    return json
}