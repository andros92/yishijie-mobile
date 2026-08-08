package com.yishijie.chuanshuo.interconnect

import android.content.Context
import android.util.Log
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.api.LoginRequest
import com.yishijie.chuanshuo.api.RegisterRequest
import com.yishijie.chuanshuo.api.SaveUploadRequest
import com.yishijie.chuanshuo.data.DeviceManager
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 异世界传说 - 存档/账号同步管理器
 * 手环 ↔ 手机（小米穿戴 SDK 消息），手机 ↔ 服务器（/api/yishijie）
 */
class GameSyncManager private constructor(
    private val context: Context,
    private val interconn: InterconnManager
) {
    companion object {
        private const val TAG = "GameSyncManager"

        @Volatile
        private var instance: GameSyncManager? = null

        fun getInstance(context: Context): GameSyncManager {
            return instance ?: synchronized(this) {
                val interconn = InterconnManager.getInstance(context)
                instance ?: GameSyncManager(context.applicationContext, interconn).also { instance = it }
            }
        }
    }

    interface SaveCallback {
        fun onSaveUploaded(success: Boolean, message: String)
        fun onSaveDownloaded(data: JSONObject?)
        fun onError(error: String)
    }

    interface AccountCallback {
        fun onRegistered(playerId: String, playerName: String, isNew: Boolean, error: String?)
        fun onLoggedIn(playerId: String?, playerName: String?, error: String?)
    }

    private val deviceManager = DeviceManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var saveCallback: SaveCallback? = null

    init {
        interconn.addMessageListener(object : InterconnManager.MessageListener {
            override fun onMessage(data: JSONObject) {
                handleGameMessage(data)
            }
        })
    }

    private fun handleGameMessage(json: JSONObject) {
        val type = json.optString("type", "")
        when (type) {
            "game_data" -> {
                // 手环推送整包存档（上传到服务器）
                scope.launch {
                    val raw = json.optJSONObject("data")
                    val ok = if (raw != null) {
                        uploadSaveToServer(com.google.gson.JsonParser().parse(raw.toString()).asJsonObject)
                    } else {
                        false
                    }
                    saveCallback?.onSaveUploaded(ok, if (ok) "手环存档已同步到服务器" else "同步失败")
                }
            }
            "save_uploaded" -> {
                saveCallback?.onSaveUploaded(
                    json.optBoolean("success", false),
                    json.optString("message", "")
                )
            }
            "save_downloaded" -> {
                saveCallback?.onSaveDownloaded(json.optJSONObject("data"))
            }
            "error" -> {
                saveCallback?.onError(json.optString("message", "未知错误"))
            }
        }
    }

    // ========== 账号 ==========
    fun registerAccount(playerName: String, callback: AccountCallback) {
        scope.launch {
            val fp = deviceManager.getDeviceFingerprint()
                ?: run { callback.onRegistered("", "", false, "未连接手环，无法获取设备指纹"); return@launch }
            val request = RegisterRequest(playerName, fp, deviceManager.getPhoneFingerprint())
            when (val r = ApiClient.safeApiCall { ApiClient.api.register(request) }) {
                is ApiResult.Success -> {
                    val d = r.data
                    if (d?.success == true && d.playerId != null) {
                        deviceManager.saveAccount(d.playerId, d.playerName ?: playerName)
                        deviceManager.switchAccount(d.playerId, d.playerName ?: playerName)
                        d.apiKey?.let { ApiClient.apiKey = it }
                        callback.onRegistered(d.playerId, d.playerName ?: playerName, d.isNew, null)
                    } else {
                        callback.onRegistered("", "", false, d?.error ?: "注册失败")
                    }
                }
                is ApiResult.Error -> callback.onRegistered("", "", false, r.message)
            }
        }
    }

    fun loginAccount(callback: AccountCallback) {
        scope.launch {
            val fp = deviceManager.getDeviceFingerprint()
                ?: run { callback.onLoggedIn(null, null, "未连接手环，无法获取设备指纹"); return@launch }
            val request = LoginRequest(fp, deviceManager.getPhoneFingerprint())
            when (val r = ApiClient.safeApiCall { ApiClient.api.login(request) }) {
                is ApiResult.Success -> {
                    val d = r.data
                    if (d?.success == true && d.playerId != null) {
                        deviceManager.saveAccount(d.playerId, d.playerName ?: "")
                        deviceManager.switchAccount(d.playerId, d.playerName ?: "")
                        callback.onLoggedIn(d.playerId, d.playerName, null)
                    } else {
                        callback.onLoggedIn(null, null, d?.error ?: "登录失败")
                    }
                }
                is ApiResult.Error -> callback.onLoggedIn(null, null, r.message)
            }
        }
    }

    // ========== 服务器存档 ==========
    suspend fun downloadSaveFromServer(): JsonObject? {
        val playerId = deviceManager.getCurrentPlayerId() ?: return null
        val fp = deviceManager.getDeviceFingerprint() ?: return null
        val key = ApiClient.apiKey ?: return null
        return when (val r = ApiClient.safeApiCall { ApiClient.api.downloadSave(playerId, fp, key) }) {
            is ApiResult.Success -> r.data?.data
            is ApiResult.Error -> {
                Log.e(TAG, "下载存档失败: ${r.message}")
                null
            }
        }
    }

    suspend fun uploadSaveToServer(save: JsonObject?): Boolean {
        if (save == null) return false
        val playerId = deviceManager.getCurrentPlayerId() ?: return false
        val fp = deviceManager.getDeviceFingerprint() ?: return false
        val key = ApiClient.apiKey ?: return false
        val request = SaveUploadRequest(fp, key, save)
        return when (val r = ApiClient.safeApiCall { ApiClient.api.uploadSave(playerId, request) }) {
            is ApiResult.Success -> r.data?.success == true
            is ApiResult.Error -> {
                Log.e(TAG, "上传存档失败: ${r.message}")
                false
            }
        }
    }

    // ========== 手环存档 ==========
    fun uploadSaveToBand(save: JSONObject, callback: SaveCallback) {
        this.saveCallback = callback
        interconn.sendToWatch(
            JSONObject().put("tag", "game").put("type", "upload_save").put("data", save),
            onFail = { err -> callback.onError(err) }
        )
    }

    fun downloadSaveFromBand(callback: SaveCallback) {
        this.saveCallback = callback
        interconn.sendToWatch(
            JSONObject().put("tag", "game").put("type", "download_save"),
            onFail = { err -> callback.onError(err) }
        )
    }

    fun release() {
        scope.cancel()
    }
}
