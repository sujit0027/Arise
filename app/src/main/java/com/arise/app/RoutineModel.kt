package com.arise.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RoutineModel(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var isActive: Boolean = false,
    var triggerType: String = "MANUAL", // "MANUAL" or "TIMER"
    var triggerTime: String = "08:00",  // "HH:mm" format
    var wallpaperUri: String? = null,    // URI to custom wallpaper image
    var blockedApps: List<String> = emptyList(), // List of blocked app package names
    var isLockScreenOverlayEnabled: Boolean = true
) {
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("isActive", isActive)
        json.put("triggerType", triggerType)
        json.put("triggerTime", triggerTime)
        json.put("wallpaperUri", wallpaperUri ?: JSONObject.NULL)
        
        val appsArray = JSONArray()
        blockedApps.forEach { appsArray.put(it) }
        json.put("blockedApps", appsArray)
        
        json.put("isLockScreenOverlayEnabled", isLockScreenOverlayEnabled)
        return json
    }

    companion object {
        fun fromJson(jsonStr: String): RoutineModel {
            val json = JSONObject(jsonStr)
            val id = json.optString("id", UUID.randomUUID().toString())
            val name = json.getString("name")
            val isActive = json.optBoolean("isActive", false)
            val triggerType = json.optString("triggerType", "MANUAL")
            val triggerTime = json.optString("triggerTime", "08:00")
            val wallpaperUri = if (json.isNull("wallpaperUri")) null else json.getString("wallpaperUri")
            
            val appsArray = json.optJSONArray("blockedApps")
            val blockedApps = mutableListOf<String>()
            if (appsArray != null) {
                for (i in 0 until appsArray.length()) {
                    blockedApps.add(appsArray.getString(i))
                }
            }
            
            val isLockScreenOverlayEnabled = json.optBoolean("isLockScreenOverlayEnabled", true)
            
            return RoutineModel(id, name, isActive, triggerType, triggerTime, wallpaperUri, blockedApps, isLockScreenOverlayEnabled)
        }
    }
}
