package com.yishijie.chuanshuo.interconnect

import android.content.Context
import android.util.Log
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.api.ExchangeBuyRequest
import com.yishijie.chuanshuo.api.ExchangeCancelRequest
import com.yishijie.chuanshuo.api.ExchangeListRequest
import com.yishijie.chuanshuo.api.LoginRequest
import com.yishijie.chuanshuo.api.MailClaimRequest
import com.yishijie.chuanshuo.api.MarkPaidRequest
import com.yishijie.chuanshuo.api.PvpReportRequest
import com.yishijie.chuanshuo.api.RedeemRequest
import com.yishijie.chuanshuo.api.RechargeOrderRequest
import com.yishijie.chuanshuo.api.RegisterRequest
import com.yishijie.chuanshuo.api.SaveUploadRequest
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.service.CompanionService
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 异世界传说 - 手环↔手机↔服务器 桥接
 * 处理手环发来的 req_* 请求，调用 /api/yishijie，带 _reqId 回包
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

    data class SaveUploadResult(
        val ok: Boolean,
        val error: String? = null
    )

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
            "req_register" -> scope.launch { handleReqRegister(json) }
            "req_upload_save" -> scope.launch { handleReqUploadSave(json) }
            "req_download_save" -> scope.launch { handleReqDownloadSave(json) }
            "req_exchange_listings" -> scope.launch { handleReqExchangeListings(json) }
            "req_exchange_list" -> scope.launch { handleReqExchangeList(json) }
            "req_exchange_buy" -> scope.launch { handleReqExchangeBuy(json) }
            "req_exchange_cancel" -> scope.launch { handleReqExchangeCancel(json) }
            "req_mail_list" -> scope.launch { handleReqMailList(json) }
            "req_mail_claim" -> scope.launch { handleReqMailClaim(json) }
            "req_redeem" -> scope.launch { handleReqRedeem(json) }
            "req_leaderboard" -> scope.launch { handleReqLeaderboard(json) }
            "req_pvp_targets" -> scope.launch { handleReqPvpTargets(json) }
            "req_pvp_defender" -> scope.launch { handleReqPvpDefender(json) }
            "req_pvp_report" -> scope.launch { handleReqPvpReport(json) }
            "req_pvp_rating" -> scope.launch { handleReqPvpRating(json) }
            "req_pvp_room_create" -> scope.launch { handleReqPvpRoomCreate(json) }
            "req_pvp_room_join" -> scope.launch { handleReqPvpRoomJoin(json) }
            "req_pvp_room_status" -> scope.launch { handleReqPvpRoomStatus(json) }
            "req_pvp_room_fight" -> scope.launch { handleReqPvpRoomFight(json) }
            "req_recharge_order" -> scope.launch { handleReqRechargeOrder(json) }
            "player_id" -> scope.launch { handlePlayerId(json) }
            "game_data" -> {
                // 手环推送整包存档：自动上传服务器
                scope.launch {
                    val raw = json.optJSONObject("data")
                    val ok = if (raw != null) uploadSaveToServer(gsonObj(raw.toString())) else false
                    saveCallback?.onSaveUploaded(ok, if (ok) "手环存档已同步到服务器" else "同步失败")
                }
            }
            "save_uploaded" -> saveCallback?.onSaveUploaded(json.optBoolean("success", false), json.optString("message", ""))
            "save_downloaded" -> saveCallback?.onSaveDownloaded(json.optJSONObject("data"))
            "error" -> saveCallback?.onError(json.optString("message", "未知错误"))
        }
    }

    private fun gsonObj(s: String): JsonObject {
        return try {
            JsonParser().parse(s).asJsonObject
        } catch (e: Exception) {
            JsonObject()
        }
    }

    private fun sendResponse(reqId: Int, type: String, body: JSONObject) {
        val msg = JSONObject().apply {
            put("tag", "game")
            put("type", type)
            if (reqId != 0) put("_reqId", reqId)
            val it = body.keys()
            while (it.hasNext()) {
                val k = it.next()
                put(k, body.get(k))
            }
        }
        interconn.sendToWatch(msg, onFail = { err -> Log.e(TAG, "回包失败 type=$type: $err") })
    }

    /**
     * 设备指纹写入后通知界面刷新（连接状态广播比指纹响应早，界面可能停在“未知”）
     */
    private fun notifyFingerprintUpdated() {
        try {
            context.sendBroadcast(
                Intent(CompanionService.ACTION_DEVICE_FINGERPRINT).setPackage(context.packageName)
            )
        } catch (e: Exception) {
            Log.e(TAG, "指纹广播失败: ${e.message}")
        }
    }

    // ========== 注册 ==========
    private suspend fun handleReqRegister(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val name = json.optString("playerName", "手环玩家")
        val fp = json.optString("deviceFingerprint", "")
        if (fp.isEmpty()) {
            sendResponse(reqId, "register_result", JSONObject().put("error", "设备指纹为空"))
            return
        }
        // 先记录手环指纹，后续所有 API 校验都依赖它
        deviceManager.setDeviceFingerprint(fp)
        notifyFingerprintUpdated()
        val request = RegisterRequest(name, fp, deviceManager.getPhoneFingerprint())
        when (val r = ApiClient.safeApiCall { ApiClient.api.register(request) }) {
            is ApiResult.Success -> {
                val d = r.data
                if (d?.success == true && d.playerId != null) {
                    deviceManager.saveAccount(d.playerId, d.playerName ?: name)
                    deviceManager.switchAccount(d.playerId, d.playerName ?: name)
                    d.apiKey?.let { ApiClient.apiKey = it }
                    sendResponse(reqId, "register_result", JSONObject().apply {
                        put("playerId", d.playerId)
                        put("playerName", d.playerName ?: name)
                        put("isNew", d.isNew)
                    })
                    // 通知手环保存 playerId
                    interconn.sendToWatch(
                        JSONObject().put("tag", "game").put("type", "save_player_id")
                            .put("playerId", d.playerId)
                            .put("playerName", d.playerName ?: name)
                            .put("deviceFingerprint", fp)
                    )
                } else {
                    sendResponse(reqId, "register_result", JSONObject().put("error", d?.error ?: "注册失败"))
                }
            }
            is ApiResult.Error -> sendResponse(reqId, "register_result", JSONObject().put("error", r.message))
        }
    }

    /**
     * 手环回传身份（响应 request_player_id）：
     * 记录手环设备指纹，并同步手环上的账号（手环是唯一身份主体）
     */
    private suspend fun handlePlayerId(json: JSONObject) {
        val fp = json.optString("deviceFingerprint", "")
        if (fp.isNotEmpty()) {
            deviceManager.setDeviceFingerprint(fp)
            notifyFingerprintUpdated()
        }
        val playerId = json.optString("playerId", "")
        if (playerId.isNotEmpty()) {
            val playerName = json.optString("playerName", "手环玩家")
            deviceManager.saveAccount(playerId, playerName)
            deviceManager.switchAccount(playerId, playerName)
        }
    }

    // ========== 存档 ==========
    private suspend fun handleReqUploadSave(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val save = json.optJSONObject("gameData") ?: json.optJSONObject("data")
        val deviceTime = json.optLong("deviceTime", 0)
        val res = if (save != null) {
            uploadSaveToServerWithResult(gsonObj(save.toString()), deviceTime)
        } else {
            SaveUploadResult(false, "存档数据为空")
        }
        sendResponse(reqId, "save_uploaded", JSONObject().apply {
            put("success", res.ok)
            if (!res.ok) put("error", res.error ?: "上传失败")
        })
    }

    private suspend fun handleReqDownloadSave(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val data = downloadSaveFromServer()
        if (data != null) {
            sendResponse(reqId, "save_downloaded", JSONObject().put("data", JSONObject(data.toString())))
        } else {
            sendResponse(reqId, "save_downloaded", JSONObject().put("data", JSONObject.NULL))
        }
    }

    // ========== 交易所 ==========
    private suspend fun handleReqExchangeListings(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val page = json.optInt("page", 1)
        val cat = json.optString("category", "all")
        val keyword = json.optString("keyword", "").ifEmpty { null }
        val mine = json.optBoolean("mine", false)
        val me = deviceManager.getCurrentPlayerId()
        val fp = deviceManager.getDeviceFingerprint()
        val key = ApiClient.apiKey
        when (val r = ApiClient.safeApiCall {
            if (mine) ApiClient.api.exchangeListings(page, 20, cat, keyword, true, me, fp, key)
            else ApiClient.api.exchangeListings(page, 20, cat, keyword)
        }) {
            is ApiResult.Success -> {
                val arr = JSONArray()
                (r.data?.data ?: emptyList()).forEach { it ->
                    arr.put(JSONObject().apply {
                        put("id", it.id)
                        put("sellerName", it.seller_name)
                        put("itemKey", it.item_key)
                        put("itemName", it.item_name)
                        put("qty", it.qty)
                        put("price", it.price)
                        put("quality", it.quality)
                        put("gem", it.gem)
                        put("dur", it.dur)
                        put("maxDur", it.max_dur)
                        put("category", it.category)
                        put("created_at", it.created_at)
                        if (it.pet != null) put("pet", JSONObject(it.pet.toString()))
                    })
                }
                val body = JSONObject().put("data", arr)
                body.put("total", r.data?.total ?: 0)
                body.put("page", page)
                sendResponse(reqId, "exchange_listings", body)
            }
            is ApiResult.Error -> sendResponse(reqId, "exchange_listings", JSONObject().put("error", r.message))
        }
    }

    private suspend fun handleReqExchangeList(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val me = deviceManager.getCurrentPlayerId()
        val fp = deviceManager.getDeviceFingerprint() ?: json.optString("deviceFingerprint", "")
        val key = ApiClient.apiKey
        if (me == null || key == null) {
            sendResponse(reqId, "exchange_listed", JSONObject().put("error", "手机端未登录账号"))
            return
        }
        val request = ExchangeListRequest(
            playerId = me, deviceFingerprint = fp, apiKey = key,
            save = json.optJSONObject("gameData")?.let { gsonObj(it.toString()) },
            key = json.optString("itemKey"), name = json.optString("itemName"),
            img = json.optString("itemImg", ""),
            qty = json.optInt("qty", 1), price = json.optInt("price", 0),
            uid = json.optString("uid", ""),
            petCaseId = json.optString("petCaseId", ""),
            quality = if (json.has("quality")) json.optString("quality") else null,
            gem = if (json.has("gem")) json.optString("gem") else null,
            dur = json.optInt("dur", 0), maxDur = json.optInt("maxDur", 0),
            category = if (json.has("category")) json.optString("category") else null,
            pet = json.optJSONObject("pet")?.let { gsonObj(it.toString()) }
        )
        when (val r = ApiClient.safeApiCall { ApiClient.api.exchangeList(request) }) {
            is ApiResult.Success -> sendResponse(reqId, "exchange_listed", JSONObject().apply {
                put("success", r.data?.success == true)
                put("error", r.data?.error ?: "")
                put("listingId", r.data?.listingId ?: 0)
                r.data?.save?.let { put("save", JSONObject(it.toString())) }
            })
            is ApiResult.Error -> sendResponse(reqId, "exchange_listed", JSONObject().put("error", r.message))
        }
    }

    private suspend fun handleReqExchangeBuy(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val me = deviceManager.getCurrentPlayerId()
        val fp = deviceManager.getDeviceFingerprint() ?: json.optString("deviceFingerprint", "")
        val key = ApiClient.apiKey
        if (me == null || key == null) {
            sendResponse(reqId, "exchange_bought", JSONObject().put("error", "手机端未登录账号"))
            return
        }
        val request = ExchangeBuyRequest(
            json.optInt("listingId", 0), me, fp, key,
            json.optJSONObject("gameData")?.let { gsonObj(it.toString()) }
        )
        when (val r = ApiClient.safeApiCall { ApiClient.api.exchangeBuy(request) }) {
            is ApiResult.Success -> sendResponse(reqId, "exchange_bought", JSONObject().apply {
                put("success", r.data?.success == true)
                put("error", r.data?.error ?: "")
                r.data?.save?.let { put("save", JSONObject(it.toString())) }
            })
            is ApiResult.Error -> sendResponse(reqId, "exchange_bought", JSONObject().put("error", r.message))
        }
    }

    private suspend fun handleReqExchangeCancel(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val me = deviceManager.getCurrentPlayerId()
        val fp = deviceManager.getDeviceFingerprint() ?: json.optString("deviceFingerprint", "")
        val key = ApiClient.apiKey
        if (me == null || key == null) {
            sendResponse(reqId, "exchange_cancelled", JSONObject().put("error", "手机端未登录账号"))
            return
        }
        val request = ExchangeCancelRequest(
            json.optInt("listingId", 0), me, fp, key,
            json.optJSONObject("gameData")?.let { gsonObj(it.toString()) }
        )
        when (val r = ApiClient.safeApiCall { ApiClient.api.exchangeCancel(request) }) {
            is ApiResult.Success -> sendResponse(reqId, "exchange_cancelled", JSONObject().apply {
                put("success", r.data?.success == true)
                put("error", r.data?.error ?: "")
                r.data?.save?.let { put("save", JSONObject(it.toString())) }
            })
            is ApiResult.Error -> sendResponse(reqId, "exchange_cancelled", JSONObject().put("error", r.message))
        }
    }

    // ========== 邮箱 ==========
    private suspend fun handleReqMailList(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val me = deviceManager.getCurrentPlayerId()
        val fp = deviceManager.getDeviceFingerprint() ?: json.optString("deviceFingerprint", "")
        val key = ApiClient.apiKey
        if (me == null || key == null) {
            sendResponse(reqId, "mail_list", JSONObject().put("error", "手机端未登录账号"))
            return
        }
        when (val r = ApiClient.safeApiCall { ApiClient.api.mailList(me, fp, key) }) {
            is ApiResult.Success -> {
                val arr = JSONArray()
                (r.data?.data ?: emptyList()).forEach { m ->
                    arr.put(JSONObject().apply {
                        put("id", m.id)
                        put("title", m.title)
                        put("content", m.content)
                        put("coins", m.coins)
                        if (m.rewards != null) put("rewards", JSONObject(m.rewards.toString()))
                        put("claimed", m.claimed)
                        put("createdAt", m.created_at)
                    })
                }
                sendResponse(reqId, "mail_list", JSONObject().put("data", arr))
            }
            is ApiResult.Error -> sendResponse(reqId, "mail_list", JSONObject().put("error", r.message))
        }
    }

    private suspend fun handleReqMailClaim(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val me = deviceManager.getCurrentPlayerId()
        val fp = deviceManager.getDeviceFingerprint() ?: json.optString("deviceFingerprint", "")
        val key = ApiClient.apiKey
        if (me == null || key == null) {
            sendResponse(reqId, "mail_claimed", JSONObject().put("error", "手机端未登录账号"))
            return
        }
        val req = MailClaimRequest(
            me, fp, key, json.optInt("mailId", 0),
            json.optJSONObject("gameData")?.let { gsonObj(it.toString()) }
        )
        when (val r = ApiClient.safeApiCall { ApiClient.api.claimMail(req) }) {
            is ApiResult.Success -> sendResponse(reqId, "mail_claimed", JSONObject().apply {
                put("success", r.data?.success == true)
                put("coins", r.data?.coins ?: 0)
                if (r.data?.applied != null) put("applied", JSONObject(r.data.applied.toString()))
                r.data?.save?.let { put("save", JSONObject(it.toString())) }
                put("error", r.data?.error ?: "")
            })
            is ApiResult.Error -> sendResponse(reqId, "mail_claimed", JSONObject().put("error", r.message))
        }
    }

    // ========== 激活码 ==========
    private suspend fun handleReqRedeem(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val me = deviceManager.getCurrentPlayerId()
        val fp = deviceManager.getDeviceFingerprint() ?: json.optString("deviceFingerprint", "")
        val key = ApiClient.apiKey
        if (me == null || key == null) {
            sendResponse(reqId, "redeem_result", JSONObject().put("error", "手机端未登录账号"))
            return
        }
        val req = RedeemRequest(me, fp, key, json.optString("code", ""))
        when (val r = ApiClient.safeApiCall { ApiClient.api.redeem(req) }) {
            is ApiResult.Success -> sendResponse(reqId, "redeem_result", JSONObject().apply {
                put("success", r.data?.success == true)
                put("message", r.data?.message ?: "")
                put("error", r.data?.error ?: "")
            })
            is ApiResult.Error -> sendResponse(reqId, "redeem_result", JSONObject().put("error", r.message))
        }
    }

    // ========== 排行榜 ==========
    private suspend fun handleReqLeaderboard(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val type = json.optString("lbType", "level")
        // 手环屏幕小，只取前 5 名，减小蓝牙传输量
        when (val r = ApiClient.safeApiCall { ApiClient.api.leaderboard(type, 5) }) {
            is ApiResult.Success -> {
                val arr = JSONArray()
                (r.data?.data ?: emptyList()).forEach { it ->
                    arr.put(JSONObject().apply {
                        put("playerId", it.playerId)
                        put("playerName", it.playerName)
                        put("value", it.value)
                        put("exp", it.exp)
                        put("topLv", it.topLv)
                        put("count", it.count)
                    })
                }
                sendResponse(reqId, "leaderboard", JSONObject().put("data", arr))
            }
            is ApiResult.Error -> sendResponse(reqId, "leaderboard", JSONObject().put("error", r.message))
        }
    }

    // ========== PVP ==========
    private suspend fun handleReqPvpTargets(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val me = deviceManager.getCurrentPlayerId()
        val fp = deviceManager.getDeviceFingerprint() ?: json.optString("deviceFingerprint", "")
        val key = ApiClient.apiKey
        if (me == null || key == null) {
            sendResponse(reqId, "pvp_targets", JSONObject().put("error", "手机端未登录账号"))
            return
        }
        when (val r = ApiClient.safeApiCall { ApiClient.api.pvpTargets(me, fp, key) }) {
            is ApiResult.Success -> {
                val arr = JSONArray()
                (r.data?.data ?: emptyList()).forEach { it ->
                    arr.put(JSONObject().apply {
                        put("playerId", it.playerId)
                        put("playerName", it.playerName)
                        put("rating", it.rating)
                        put("lv", it.lv)
                    })
                }
                sendResponse(reqId, "pvp_targets", JSONObject().put("data", arr))
            }
            is ApiResult.Error -> sendResponse(reqId, "pvp_targets", JSONObject().put("error", r.message))
        }
    }

    private suspend fun handleReqPvpDefender(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val me = deviceManager.getCurrentPlayerId()
        val fp = deviceManager.getDeviceFingerprint() ?: json.optString("deviceFingerprint", "")
        val key = ApiClient.apiKey
        if (me == null || key == null) {
            sendResponse(reqId, "pvp_defender", JSONObject().put("error", "手机端未登录账号"))
            return
        }
        val targetId = json.optString("targetId", "")
        when (val r = ApiClient.safeApiCall { ApiClient.api.pvpDefender(me, fp, key, targetId) }) {
            is ApiResult.Success -> {
                val d = r.data?.defender
                if (d == null) {
                    sendResponse(reqId, "pvp_defender", JSONObject().put("error", r.data?.error ?: "无防守数据"))
                } else {
                    val obj = JSONObject().apply {
                        put("playerId", d.playerId)
                        put("playerName", d.playerName)
                        put("rating", d.rating)
                        if (d.cls != null) put("class", JSONObject(d.cls.toString()))
                        if (d.stats != null) put("stats", JSONObject(d.stats.toString()))
                        if (d.equip != null) put("equip", JSONObject(d.equip.toString()))
                        if (d.gear != null) put("gear", JSONObject(d.gear.toString()))
                        if (d.pets != null) put("pets", JSONObject(d.pets.toString()))
                    }
                    sendResponse(reqId, "pvp_defender", JSONObject().put("defender", obj))
                }
            }
            is ApiResult.Error -> sendResponse(reqId, "pvp_defender", JSONObject().put("error", r.message))
        }
    }

    private suspend fun handleReqPvpReport(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val me = deviceManager.getCurrentPlayerId()
        val fp = deviceManager.getDeviceFingerprint() ?: json.optString("deviceFingerprint", "")
        val key = ApiClient.apiKey
        if (me == null || key == null) {
            sendResponse(reqId, "pvp_report", JSONObject().put("error", "手机端未登录账号"))
            return
        }
        val req = PvpReportRequest(me, fp, key, json.optString("targetId", ""), json.optBoolean("win", false))
        when (val r = ApiClient.safeApiCall { ApiClient.api.pvpReport(req) }) {
            is ApiResult.Success -> sendResponse(reqId, "pvp_report", JSONObject().apply {
                put("success", r.data?.success == true)
                put("rating", r.data?.rating ?: 0)
                put("delta", r.data?.delta ?: 0)
                put("win", r.data?.win ?: false)
                put("error", r.data?.error ?: "")
            })
            is ApiResult.Error -> sendResponse(reqId, "pvp_report", JSONObject().put("error", r.message))
        }
    }

    private suspend fun handleReqPvpRating(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val me = deviceManager.getCurrentPlayerId()
        if (me == null) {
            sendResponse(reqId, "pvp_rating", JSONObject().put("error", "手机端未登录账号"))
            return
        }
        when (val r = ApiClient.safeApiCall { ApiClient.api.pvpRating(me) }) {
            is ApiResult.Success -> sendResponse(reqId, "pvp_rating", JSONObject().apply {
                put("rating", r.data?.rating ?: 1000)
                put("wins", r.data?.wins ?: 0)
                put("losses", r.data?.losses ?: 0)
                put("dailyLeft", r.data?.dailyLeft ?: 12)
            })
            is ApiResult.Error -> sendResponse(reqId, "pvp_rating", JSONObject().put("error", r.message))
        }
    }

    private fun pvpAuthPayload(json: JSONObject): JSONObject? {
        val me = deviceManager.getCurrentPlayerId() ?: return null
        val fp = deviceManager.getDeviceFingerprint() ?: return null
        val key = ApiClient.apiKey ?: return null
        return JSONObject().apply {
            put("playerId", me)
            put("deviceFingerprint", fp)
            put("apiKey", key)
            if (json.has("roomCode")) put("roomCode", json.optString("roomCode"))
        }
    }

    private suspend fun handleReqPvpRoomCreate(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val body = pvpAuthPayload(json)
        if (body == null) { sendResponse(reqId, "pvp_room_created", JSONObject().put("error", "手机端未登录账号")); return }
        when (val r = ApiClient.safeApiCall { ApiClient.api.pvpRoomCreate(gsonObj(body.toString())) }) {
            is ApiResult.Success -> sendResponse(reqId, "pvp_room_created", JSONObject(r.data.toString()))
            is ApiResult.Error -> sendResponse(reqId, "pvp_room_created", JSONObject().put("error", r.message))
        }
    }

    private suspend fun handleReqPvpRoomJoin(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val body = pvpAuthPayload(json)
        if (body == null) { sendResponse(reqId, "pvp_room_joined", JSONObject().put("error", "手机端未登录账号")); return }
        when (val r = ApiClient.safeApiCall { ApiClient.api.pvpRoomJoin(gsonObj(body.toString())) }) {
            is ApiResult.Success -> sendResponse(reqId, "pvp_room_joined", JSONObject(r.data.toString()))
            is ApiResult.Error -> sendResponse(reqId, "pvp_room_joined", JSONObject().put("error", r.message))
        }
    }

    private suspend fun handleReqPvpRoomStatus(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val code = json.optString("roomCode", "")
        when (val r = ApiClient.safeApiCall { ApiClient.api.pvpRoomStatus(code) }) {
            is ApiResult.Success -> sendResponse(reqId, "pvp_room_status", JSONObject(r.data.toString()))
            is ApiResult.Error -> sendResponse(reqId, "pvp_room_status", JSONObject().put("error", r.message))
        }
    }

    private suspend fun handleReqPvpRoomFight(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val body = pvpAuthPayload(json)
        if (body == null) { sendResponse(reqId, "pvp_room_fought", JSONObject().put("error", "手机端未登录账号")); return }
        when (val r = ApiClient.safeApiCall { ApiClient.api.pvpRoomFight(gsonObj(body.toString())) }) {
            is ApiResult.Success -> sendResponse(reqId, "pvp_room_fought", JSONObject(r.data.toString()))
            is ApiResult.Error -> sendResponse(reqId, "pvp_room_fought", JSONObject().put("error", r.message))
        }
    }

    // ========== 充值订单（手环发起） ==========
    private suspend fun handleReqRechargeOrder(json: JSONObject) {
        val reqId = json.optInt("_reqId", 0)
        val me = deviceManager.getCurrentPlayerId()
        val fp = deviceManager.getDeviceFingerprint() ?: json.optString("deviceFingerprint", "")
        val key = ApiClient.apiKey
        if (me == null || key == null) {
            sendResponse(reqId, "recharge_order", JSONObject().put("error", "手机端未登录账号"))
            return
        }
        val amount = json.optDouble("amount", 0.0)
        val request = RechargeOrderRequest(me, fp, key, amount)
        when (val r = ApiClient.safeApiCall { ApiClient.api.createRechargeOrder(request) }) {
            is ApiResult.Success -> sendResponse(reqId, "recharge_order", JSONObject().apply {
                put("success", r.data?.success == true)
                put("orderId", r.data?.orderId ?: "")
                put("qty", r.data?.qty ?: 0)
                put("error", r.data?.error ?: "")
            })
            is ApiResult.Error -> sendResponse(reqId, "recharge_order", JSONObject().put("error", r.message))
        }
    }

    // ========== 手机端直接调用（供 SaveManager 等页面使用） ==========
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

    suspend fun uploadSaveToServer(save: JsonObject?, deviceTime: Long = 0): Boolean {
        return uploadSaveToServerWithResult(save, deviceTime).ok
    }

    /**
     * 上传存档并返回具体失败原因（未登录/指纹缺失/服务器时间校验/鉴权等）
     */
    suspend fun uploadSaveToServerWithResult(save: JsonObject?, deviceTime: Long = 0): SaveUploadResult {
        if (save == null) return SaveUploadResult(false, "存档数据为空")
        val playerId = deviceManager.getCurrentPlayerId() ?: return SaveUploadResult(false, "未登录账号")
        val fp = deviceManager.getDeviceFingerprint() ?: return SaveUploadResult(false, "未获取到设备指纹，请先连接手环")
        val key = ApiClient.apiKey ?: return SaveUploadResult(false, "未登录账号")
        return when (val r = ApiClient.safeApiCall { ApiClient.api.uploadSave(playerId, SaveUploadRequest(fp, key, save, deviceTime)) }) {
            is ApiResult.Success -> {
                if (r.data?.success == true) {
                    SaveUploadResult(true)
                } else {
                    SaveUploadResult(false, r.data?.error ?: "服务器返回失败")
                }
            }
            is ApiResult.Error -> {
                Log.e(TAG, "上传存档失败: ${r.message}")
                SaveUploadResult(false, r.message)
            }
        }
    }

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
            when (val r = ApiClient.safeApiCall { ApiClient.api.login(LoginRequest(fp, deviceManager.getPhoneFingerprint())) }) {
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

    fun release() {
        scope.cancel()
    }
}
