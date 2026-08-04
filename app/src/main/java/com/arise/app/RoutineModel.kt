package com.arise.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RoutineModel(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var isActive: Boolean = false,
    var triggerType: String = "MANUAL", // "MANUAL" or "TIMER"
    var triggerTime: String = "08:00",  // HH:mm format (deprecated)
    var wallpaperUri: String? = null,    // URI to custom wallpaper image
    var blockedApps: List<String> = emptyList(), // List of blocked app package names
    var isLockScreenOverlayEnabled: Boolean = true,
    
    // Auto scheduling
    var autoStartTime: String? = null, 
    var autoEndTime: String? = null, 
    var isAutoTriggerEnabled: Boolean = false,
    
    // Pre-start Warning
    var preStartWarningMinutes: Int = 0, // 0 = disabled, 5, 10, etc.
    
    // Visual customization
    var iconResName: String = "star", // "star", "book", "bed", "gym", "music", "work"
    var customFocusMessage: String = "", // Daily target text
    var durationMinutes: Int = 0, // 0 means "Until turned off"
    
    // Explicit action toggles (Samsung parity - at least one must be active to save)
    var isWallpaperEnabled: Boolean = false,
    var isAppBlockEnabled: Boolean = false,
    var isTimerEnabled: Boolean = false,
    var isSoundAlertEnabled: Boolean = false
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
        
        // Auto scheduling & Warnings
        json.put("autoStartTime", autoStartTime ?: JSONObject.NULL)
        json.put("autoEndTime", autoEndTime ?: JSONObject.NULL)
        json.put("isAutoTriggerEnabled", isAutoTriggerEnabled)
        json.put("preStartWarningMinutes", preStartWarningMinutes)
        
        // Customizations
        json.put("iconResName", iconResName)
        json.put("customFocusMessage", customFocusMessage)
        json.put("durationMinutes", durationMinutes)
        
        // Action toggles
        json.put("isWallpaperEnabled", isWallpaperEnabled)
        json.put("isAppBlockEnabled", isAppBlockEnabled)
        json.put("isTimerEnabled", isTimerEnabled)
        json.put("isSoundAlertEnabled", isSoundAlertEnabled)
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
            
            val autoStartTime = if (json.isNull("autoStartTime")) null else json.getString("autoStartTime")
            val autoEndTime = if (json.isNull("autoEndTime")) null else json.getString("autoEndTime")
            val isAutoTriggerEnabled = json.optBoolean("isAutoTriggerEnabled", false)
            val preStartWarningMinutes = json.optInt("preStartWarningMinutes", 0)
            
            val iconResName = json.optString("iconResName", "star")
            val customFocusMessage = json.optString("customFocusMessage", "")
            val durationMinutes = json.optInt("durationMinutes", 0)
            
            val isWallpaperEnabled = json.optBoolean("isWallpaperEnabled", false)
            val isAppBlockEnabled = json.optBoolean("isAppBlockEnabled", false)
            val isTimerEnabled = json.optBoolean("isTimerEnabled", false)
            val isSoundAlertEnabled = json.optBoolean("isSoundAlertEnabled", false)
            
            return RoutineModel(
                id = id,
                name = name,
                isActive = isActive,
                triggerType = triggerType,
                triggerTime = triggerTime,
                wallpaperUri = wallpaperUri,
                blockedApps = blockedApps,
                isLockScreenOverlayEnabled = isLockScreenOverlayEnabled,
                autoStartTime = autoStartTime,
                autoEndTime = autoEndTime,
                isAutoTriggerEnabled = isAutoTriggerEnabled,
                preStartWarningMinutes = preStartWarningMinutes,
                iconResName = iconResName,
                customFocusMessage = customFocusMessage,
                durationMinutes = durationMinutes,
                isWallpaperEnabled = isWallpaperEnabled,
                isAppBlockEnabled = isAppBlockEnabled,
                isTimerEnabled = isTimerEnabled,
                isSoundAlertEnabled = isSoundAlertEnabled
            )
        }
    }
}
