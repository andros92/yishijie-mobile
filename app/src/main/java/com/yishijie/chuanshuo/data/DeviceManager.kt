package com.yishijie.chuanshuo.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DeviceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("yishijie", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_PHONE_FINGERPRINT = "phone_fingerprint"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_CURRENT_PLAYER_ID = "current_player_id"
        private const val KEY_CURRENT_PLAYER_NAME = "current_player_name"
        private const val KEY_CURRENT_NAME_CHANGED = "current_name_changed"
        private const val KEY_DEVICE_FINGERPRINT = "device_fingerprint"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: DeviceManager? = null

        fun getInstance(context: Context): DeviceManager {
            return instance ?: synchronized(this) {
                instance ?: DeviceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // ========== 设备指纹 ==========

    /**
     * 生成手机指纹（唯一标识这部手机）
     */
    fun getPhoneFingerprint(): String {
        var fingerprint = prefs.getString(KEY_PHONE_FINGERPRINT, null)
        if (fingerprint == null) {
            fingerprint = generatePhoneFingerprint()
            prefs.edit().putString(KEY_PHONE_FINGERPRINT, fingerprint).apply()
        }
        return fingerprint
    }

    @SuppressLint("HardwareIds")
    private fun generatePhoneFingerprint(): String {
        val androidId = Settings.Secure.ANDROID_ID ?: "unknown"
        val deviceInfo = "${Build.MANUFACTURER}_${Build.MODEL}_${Build.DEVICE}"
        return "phone_${deviceInfo}_${androidId}".take(64)
    }

    /**
     * 设置手环设备指纹（从手环获取）
     */
    fun setDeviceFingerprint(fingerprint: String) {
        prefs.edit().putString(KEY_DEVICE_FINGERPRINT, fingerprint).apply()
    }

    /**
     * 获取手环设备指纹
     */
    fun getDeviceFingerprint(): String? {
        return prefs.getString(KEY_DEVICE_FINGERPRINT, null)
    }

    // ========== 账号管理 ==========

    data class AccountInfo(
        val playerId: String,
        val playerName: String,
        val createdAt: Long = System.currentTimeMillis()
    )

    /**
     * 获取所有本地保存的账号
     */
    fun getAccounts(): List<AccountInfo> {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AccountInfo>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 保存账号到本地
     */
    fun saveAccount(playerId: String, playerName: String) {
        val accounts = getAccounts().toMutableList()
        val existing = accounts.indexOfFirst { it.playerId == playerId }
        if (existing >= 0) {
            accounts[existing] = AccountInfo(playerId, playerName)
        } else {
            accounts.add(AccountInfo(playerId, playerName))
        }
        prefs.edit().putString(KEY_ACCOUNTS, gson.toJson(accounts)).apply()
    }

    /**
     * 切换当前账号
     */
    fun switchAccount(playerId: String, playerName: String, nameChanged: Boolean = false) {
        prefs.edit()
            .putString(KEY_CURRENT_PLAYER_ID, playerId)
            .putString(KEY_CURRENT_PLAYER_NAME, playerName)
            .putBoolean(KEY_CURRENT_NAME_CHANGED, nameChanged)
            .apply()
    }

    /**
     * 获取当前玩家ID
     */
    fun getCurrentPlayerId(): String? {
        return prefs.getString(KEY_CURRENT_PLAYER_ID, null)
    }

    /**
     * 获取当前玩家名称
     */
    fun getCurrentPlayerName(): String? {
        return prefs.getString(KEY_CURRENT_PLAYER_NAME, null)
    }

    /**
     * 是否已改名
     */
    fun isNameChanged(): Boolean {
        return prefs.getBoolean(KEY_CURRENT_NAME_CHANGED, false)
    }

    /**
     * 标记已改名
     */
    fun setNameChanged(changed: Boolean) {
        prefs.edit().putBoolean(KEY_CURRENT_NAME_CHANGED, changed).apply()
    }

    /**
     * 更新玩家名称
     */
    fun updatePlayerName(playerId: String, newName: String) {
        // 更新账号列表
        val accounts = getAccounts().toMutableList()
        val index = accounts.indexOfFirst { it.playerId == playerId }
        if (index >= 0) {
            accounts[index] = accounts[index].copy(playerName = newName)
            prefs.edit().putString(KEY_ACCOUNTS, gson.toJson(accounts)).apply()
        }

        // 更新当前玩家名称
        if (getCurrentPlayerId() == playerId) {
            prefs.edit().putString(KEY_CURRENT_PLAYER_NAME, newName).apply()
        }
    }

    /**
     * 检查是否已登录
     */
    fun isLoggedIn(): Boolean {
        return getCurrentPlayerId() != null
    }

    /**
     * 清除登录状态
     */
    fun clearLoginState() {
        prefs.edit()
            .remove(KEY_CURRENT_PLAYER_ID)
            .remove(KEY_CURRENT_PLAYER_NAME)
            .remove(KEY_CURRENT_NAME_CHANGED)
            .apply()
    }
}
