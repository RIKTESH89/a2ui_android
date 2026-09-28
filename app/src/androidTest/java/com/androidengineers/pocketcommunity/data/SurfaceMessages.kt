package com.androidengineers.pocketcommunity.data

import com.androidengineers.pocketcommunity.ui.UIAGENT_CATALOG_ID
import kotlinx.serialization.json.*

/** Deterministic protocol examples. These exercise the real renderer, not model inference. */
class SurfaceMessages(private val id: String) {
    private val components = mutableListOf<JsonObject>()
    private val children = mutableListOf<String>()
    private val values = buildJsonObject { repeat(4) { put("choice$it", false) } }

    private fun add(type: String, fields: JsonObject): String {
        val key = "c${components.size}"
        components += buildJsonObject {
            put("id", key)
            put("component", type)
            fields.forEach { (name, value) -> put(name, value) }
        }
        children += key
        return key
    }

    fun text(value: String, variant: String = "body") {
        add(
            "Text",
            buildJsonObject {
                put("text", value)
                put("variant", variant)
            },
        )
    }

    fun button(label: String, prompt: String) {
        val labelId = add("Text", buildJsonObject { put("text", label) })
        children.remove(labelId)
        add(
            "Button",
            buildJsonObject {
                put("child", labelId)
                put("variant", "primary")
                putJsonObject("action") {
                    putJsonObject("event") {
                        put("name", "ask")
                        putJsonObject("context") { put("prompt", prompt) }
                    }
                }
            },
        )
    }

    fun checkbox(label: String, index: Int) {
        add(
            "CheckBox",
            buildJsonObject {
                put("label", label)
                putJsonObject("value") { put("path", "/choice$index") }
            },
        )
    }

    fun build(): List<String> {
        components += buildJsonObject {
            put("id", "content")
            put("component", "Column")
            put("align", "stretch")
            put("children", JsonArray(children.map(::JsonPrimitive)))
        }
        components += buildJsonObject {
            put("id", "root")
            put("component", "Card")
            put("child", "content")
        }
        fun message(key: String, body: JsonObject) =
            buildJsonObject {
                    put("version", "v0.9")
                    put(key, body)
                }
                .toString()
        return listOf(
            message(
                "createSurface",
                buildJsonObject {
                    put("surfaceId", id)
                    put("catalogId", UIAGENT_CATALOG_ID)
                    put("sendDataModel", true)
                },
            ),
            message(
                "updateDataModel",
                buildJsonObject {
                    put("surfaceId", id)
                    put("path", "/")
                    put("value", values)
                },
            ),
            message(
                "updateComponents",
                buildJsonObject {
                    put("surfaceId", id)
                    put("components", JsonArray(components))
                },
            ),
        )
    }

    companion object {
        fun flight(id: String) =
            SurfaceMessages(id)
                .apply {
                    text("ILLUSTRATIVE · NOT A BOOKING", "caption")
                    text("Bengaluru 09:40  →  Tokyo 20:15", "h4")
                    text("Tue, 14 Oct · Terminal 1 · Gate G12 · Seat 14A")
                    button("Create packing checklist", "Create a packing checklist for this trip")
                }
                .build()

        fun checklist(id: String) =
            SurfaceMessages(id)
                .apply {
                    text("Packing list", "h4")
                    listOf("Passport", "Power adapter", "Comfortable shoes").forEachIndexed {
                        index,
                        label -> checkbox(label, index)
                    }
                    button("Refine list", "Refine my packing list using checked items")
                }
                .build()
    }
}
