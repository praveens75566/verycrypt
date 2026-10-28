package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Represents a structured sensitive credential or secret key stored within an encrypted note.
 * (e.g. recovery seeds, private keys, API tokens, passwords, PINs)
 */
data class NoteSecretField(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val value: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("label", label)
            put("value", value)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): NoteSecretField {
            return NoteSecretField(
                id = obj.optString("id", UUID.randomUUID().toString()),
                label = obj.optString("label", ""),
                value = obj.optString("value", "")
            )
        }

        fun listToJsonString(fields: List<NoteSecretField>): String {
            val array = JSONArray()
            fields.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJsonString(jsonStr: String): List<NoteSecretField> {
            if (jsonStr.isBlank()) return emptyList()
            return try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<NoteSecretField>()
                for (i in 0 until array.length()) {
                    list.add(fromJson(array.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
