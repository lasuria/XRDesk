package com.xrdesk

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight URL-based AdBlocker using AdGuard-style filters.
 */
object AdBlockEngine {
    private const val TAG = "AdBlock"
    private const val FILTER_URL = "https://filters.adtidy.org/android/filters/2_optimized.txt"
    private const val FILTER_FILE_NAME = "adblock_filters.txt"
    private const val UPDATE_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000 // 7 days

    private val domainBlacklist = ConcurrentHashMap.newKeySet<String>()
    // Rules indexed by domain for fast lookup
    private val domainIndexedRules = ConcurrentHashMap<String, MutableList<String>>()
    // Rules that apply to all domains
    private val genericRules = CopyOnWriteArrayList<String>()
    
    private val sessionWhitelist = ConcurrentHashMap.newKeySet<String>()
    private val manualWhitelist = ConcurrentHashMap.newKeySet<String>()

    private val blockedCount = AtomicInteger(0)
    private var isInitialized = false

    enum class UpdateStatus { IDLE, CHECKING, UPDATED, UP_TO_DATE, ERROR }
    private var updateStatusListener: ((UpdateStatus) -> Unit)? = null

    private val matchCache = ConcurrentHashMap<String, Boolean>()

    data class BlockedEvent(val domain: String, val url: String, val timestamp: Long = System.currentTimeMillis())
    private val blockedLog = mutableListOf<BlockedEvent>()
    private const val MAX_LOG_SIZE = 10

    fun initialize(context: Context) {
        if (isInitialized) return
        
        val filterFile = File(context.filesDir, FILTER_FILE_NAME)
        if (!filterFile.exists()) {
            try {
                context.assets.open(FILTER_FILE_NAME).use { input ->
                    filterFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Bundled filters copied to internal storage")
                if (SettingsStore.adBlockLastUpdateTimestamp == 0L) {
                    SettingsStore.setAdBlockInfo(context, null, null, filterFile.lastModified())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy bundled filters: ${e.message}")
            }
        }

        if (filterFile.exists()) {
            loadFilters(context, filterFile)
        }

        if (SettingsStore.adBlockEnabled && System.currentTimeMillis() - SettingsStore.adBlockLastUpdateTimestamp > UPDATE_INTERVAL_MS) {
            updateFiltersAsync(context)
        }
        
        isInitialized = true
    }

    fun setUpdateStatusListener(listener: (UpdateStatus) -> Unit) {
        this.updateStatusListener = listener
    }

    fun isBlocked(url: String, isTargetSession: Boolean = false): Boolean {
        if (!isInitialized || !SettingsStore.adBlockEnabled) return false
        val startTime = System.currentTimeMillis()
        
        val uri = try { url.toUri() } catch (e: Exception) { null } ?: return false
        val host = uri.host?.lowercase() ?: return false
        val isTarget = isTargetSession || host.contains("jut-su.net") || host.contains("jut.su")

        // 0. Trusted Domain Guard - Never block resources from the main site itself
        // if they don't match known bad domains. This helps with navigation.
        if (host == "jut-su.net" || host == "jut.su" || host == "www.jut-su.net") {
            if (isTarget) Log.v(TAG, "Trusted site resource allowed: $url")
            return false
        }

        // 1. Media Guard - Never block video streams
        if (isMediaUrl(url)) {
            if (isTarget) Log.d(TAG, "Media allowed: $url")
            return false
        }

        // 2. Whitelist check (Session or Manual)
        if (sessionWhitelist.contains(host) || manualWhitelist.contains(host)) {
            if (isTarget) Log.d(TAG, "Whitelisted (direct): $url")
            return false
        }
        
        // Check if any parent domain is whitelisted
        if (isWhitelistedDomain(host)) {
            if (isTarget) Log.d(TAG, "Whitelisted (parent): $url")
            return false
        }

        // 3. Blacklist matching
        val blockedByDomain = domainBlacklist.contains(host)
        val blockedByPath = if (!blockedByDomain) matchPathRules(url) else false
        val isBlacklisted = blockedByDomain || blockedByPath
        
        if (isBlacklisted) {
            blockedCount.incrementAndGet()
            synchronized(blockedLog) {
                if (blockedLog.size >= MAX_LOG_SIZE) blockedLog.removeAt(0)
                blockedLog.add(BlockedEvent(host, url))
            }
            if (isTarget) {
                val reason = if (blockedByDomain) "domain" else "path rule"
                val duration = System.currentTimeMillis() - startTime
                val type = if (url.contains(".js")) "SCRIPT" else "RESOURCE"
                Log.w(TAG, "BLOCKED ($type by $reason, ${duration}ms): $url")
                DiagnosticsLog.add("AdBlock", "Blocked $type: $url")
            } else {
                Log.v(TAG, "Blocked: $url")
            }
            return true
        }

        if (isTarget) {
            val duration = System.currentTimeMillis() - startTime
            Log.v(TAG, "Allowed (${duration}ms): $url")
        }
        return false
    }

    private fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains(".ts") || lower.contains(".m4s") ||
               lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".m4v")
    }

    private fun isWhitelistedDomain(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size < 2) return false
        
        var current = parts.last()
        for (i in parts.size - 2 downTo 0) {
            current = parts[i] + "." + current
            if (sessionWhitelist.contains(current) || manualWhitelist.contains(current)) return true
        }
        return false
    }

    private fun matchPathRules(url: String): Boolean {
        // High-performance cache check
        matchCache[url]?.let { return it }

        val start = System.currentTimeMillis()
        val lowerUrl = url.lowercase()
        
        // 1. Check generic rules first (usually few)
        for (rule in genericRules) {
            if (lowerUrl.contains(rule)) {
                if (matchCache.size > 1000) matchCache.clear()
                matchCache[url] = true
                return true
            }
        }
        
        // 2. Extract domain and check domain-specific rules
        val uri = try { url.toUri() } catch (e: Exception) { null }
        val host = uri?.host?.lowercase()
        if (host != null) {
            val domainParts = host.split(".")
            // Check full host, then parent domains (e.g. sub.domain.com, domain.com)
            var current = ""
            for (i in domainParts.indices.reversed()) {
                current = if (current.isEmpty()) domainParts[i] else domainParts[i] + "." + current
                domainIndexedRules[current]?.let { rules ->
                    for (rule in rules) {
                        if (lowerUrl.contains(rule)) {
                            if (matchCache.size > 1000) matchCache.clear()
                            matchCache[url] = true
                            return true
                        }
                    }
                }
            }
        }
        
        val duration = System.currentTimeMillis() - start
        if (duration > 100) {
            Log.w(TAG, "Slow AdBlock matching: ${duration}ms url=$url")
        }

        // Manage cache size
        if (matchCache.size > 1000) matchCache.clear()
        matchCache[url] = false
        
        return false
    }

    fun addToSessionWhitelist(url: String) {
        try {
            val host = url.toUri().host?.lowercase()
            if (host != null) {
                sessionWhitelist.add(host)
                Log.d(TAG, "Added to session whitelist: $host")
            }
        } catch (e: Exception) {}
    }

    fun updateFiltersAsync(context: Context, force: Boolean = false) {
        updateStatusListener?.invoke(UpdateStatus.CHECKING)
        
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val requestBuilder = Request.Builder().url(FILTER_URL)
        if (!force && SettingsStore.adBlockETag != null) {
            requestBuilder.header("If-None-Match", SettingsStore.adBlockETag!!)
        }
        
        val request = requestBuilder.build()
        
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to download filters: ${e.message}")
                updateStatusListener?.invoke(UpdateStatus.ERROR)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.code == 304) {
                    Log.i(TAG, "Filters are up to date (304)")
                    SettingsStore.setAdBlockInfo(context, SettingsStore.adBlockFilterVersion, SettingsStore.adBlockFilterPublished, System.currentTimeMillis())
                    updateStatusListener?.invoke(UpdateStatus.UP_TO_DATE)
                    response.close()
                    return
                }

                if (!response.isSuccessful) {
                    updateStatusListener?.invoke(UpdateStatus.ERROR)
                    response.close()
                    return
                }
                val body = response.body
                if (body == null) {
                    updateStatusListener?.invoke(UpdateStatus.ERROR)
                    response.close()
                    return
                }
                
                val etag = response.header("ETag")
                val tempFile = File(context.filesDir, "${FILTER_FILE_NAME}.tmp")
                try {
                    tempFile.outputStream().use { out ->
                        body.byteStream().copyTo(out)
                    }
                    
                    if (tempFile.length() > 1024 * 10) {
                        val targetFile = File(context.filesDir, FILTER_FILE_NAME)
                        if (tempFile.renameTo(targetFile)) {
                            loadFilters(context, targetFile, etag)
                            Log.i(TAG, "Filters updated successfully")
                            updateStatusListener?.invoke(UpdateStatus.UPDATED)
                        } else {
                            updateStatusListener?.invoke(UpdateStatus.ERROR)
                        }
                    } else {
                        updateStatusListener?.invoke(UpdateStatus.ERROR)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving filters: ${e.message}")
                    updateStatusListener?.invoke(UpdateStatus.ERROR)
                } finally {
                    if (tempFile.exists()) tempFile.delete()
                    response.close()
                }
            }
        })
    }

    private fun loadFilters(context: Context, file: File, newEtag: String? = null) {
        try {
            val newDomains = mutableSetOf<String>()
            val newDomainRules = mutableMapOf<String, MutableList<String>>()
            val newGenericRules = mutableListOf<String>()
            
            var version: String? = null
            var published: String? = null
            
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val clean = line.trim()
                    if (clean.isEmpty()) continue
                    
                    if (clean.startsWith("!")) {
                        if (clean.startsWith("! Version:")) version = clean.substring(10).trim()
                        if (clean.startsWith("! TimeUpdated:")) published = clean.substring(14).trim()
                        continue
                    }
                    
                    if (clean.startsWith("[")) continue
                    
                    if (clean.startsWith("||") && clean.endsWith("^") ) {
                        val domain = clean.substring(2, clean.length - 1).lowercase()
                        newDomains.add(domain)
                    } else if (clean.startsWith("||")) {
                        // Domain-specific path rule
                        val rule = clean.substring(2).lowercase()
                        val domainEnd = rule.indexOfAny(charArrayOf('/', ':', '?', '*'))
                        val domain = if (domainEnd != -1) rule.substring(0, domainEnd) else rule
                        
                        if (domain.isNotEmpty()) {
                            newDomainRules.getOrPut(domain) { mutableListOf() }.add(rule)
                        } else {
                            newGenericRules.add(rule)
                        }
                    } else if (!clean.contains("#") && !clean.contains("@")) {
                        newGenericRules.add(clean.lowercase())
                    }
                }
            }
            
            domainBlacklist.clear()
            domainBlacklist.addAll(newDomains)
            
            domainIndexedRules.clear()
            domainIndexedRules.putAll(newDomainRules)
            
            genericRules.clear()
            genericRules.addAll(newGenericRules)
            
            matchCache.clear()
            
            SettingsStore.setAdBlockInfo(context, version, published, System.currentTimeMillis(), newEtag)
            
            Log.i(TAG, "Loaded ${newDomains.size} domains, ${newDomainRules.size} domain rules, ${newGenericRules.size} generic rules (v=$version)")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading filters: ${e.message}")
        }
    }
}
