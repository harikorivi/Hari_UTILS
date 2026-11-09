package com.xeroxphotopro.image

import com.xeroxphotopro.core.*
import org.json.JSONArray
import org.json.JSONObject

object RepairCodec {
    fun decode(value: String): List<MaskStroke> = runCatching {
        val array=JSONArray(value); require(array.length()<=64)
        List(array.length()) { index ->
            val item=array.getJSONObject(index); val points=item.getJSONArray("points")
            require(points.length() in 1..256)
            MaskStroke(List(points.length()) { i -> val p=points.getJSONArray(i); BrushPoint(p.getDouble(0),p.getDouble(1)) },item.getDouble("radius"),item.getBoolean("restore"))
        }
    }.getOrDefault(emptyList())
    fun encode(strokes: List<MaskStroke>): String {
        require(strokes.size<=64)
        val array=JSONArray()
        strokes.forEach { stroke ->
            val points=JSONArray()
            stroke.points.forEach { points.put(JSONArray().put(it.u).put(it.v)) }
            array.put(JSONObject().put("points",points).put("radius",stroke.radius).put("restore",stroke.restore))
        }
        return array.toString()
    }
}
