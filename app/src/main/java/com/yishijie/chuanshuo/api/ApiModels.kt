package com.yishijie.chuanshuo.api

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

// ========== 注册 / 登录 ==========
data class RegisterRequest(
    val playerName: String,
    val deviceFingerprint: String,
    val phoneFingerprint: String? = null
)

data class RegisterResponse(
    val success: Boolean = false,
    val playerId: String? = null,
    val playerName: String? = null,
    val apiKey: String? = null,
    val isNew: Boolean = false,
    val error: String? = null
)

data class LoginRequest(
    val deviceFingerprint: String,
    val phoneFingerprint: String? = null
)

data class LoginResponse(
    val success: Boolean = false,
    val playerId: String? = null,
    val playerName: String? = null,
    val error: String? = null
)

// ========== 存档 ==========
data class SaveUploadRequest(
    val deviceFingerprint: String,
    val apiKey: String,
    val data: JsonObject
)

data class SaveResponse(
    val success: Boolean = false,
    val data: JsonObject? = null,
    val error: String? = null
)

// ========== 交易所 ==========
data class ExchangeListRequest(
    val playerId: String,
    val deviceFingerprint: String,
    val apiKey: String,
    val key: String,
    val name: String,
    val img: String = "",
    val qty: Int,
    val price: Int,
    val quality: String? = null,
    val affixes: List<String>? = null,
    val gem: String? = null,
    val dur: Int = 0,
    val maxDur: Int = 0,
    val broken: Boolean = false,
    val category: String? = null,
    val pet: JsonObject? = null
)

data class ExchangeListResponse(
    val success: Boolean = false,
    val listingId: Int = 0,
    val error: String? = null
)

data class ListingItem(
    val id: Int = 0,
    val seller_id: String = "",
    val seller_name: String = "",
    val item_key: String = "",
    val item_name: String = "",
    val item_uid: String = "",
    val item_img: String = "",
    val category: String = "item",
    val pet: JsonObject? = null,
    val qty: Int = 1,
    val price: Int = 0,
    val quality: String = "",
    val gem: String = "",
    val dur: Int = 0,
    val max_dur: Int = 0,
    val broken: Int = 0,
    val created_at: String = ""
)

data class ListingsResponse(
    val success: Boolean = false,
    val data: List<ListingItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 10,
    val error: String? = null
)

data class ExchangeBuyRequest(
    val listingId: Int,
    val buyerId: String,
    val deviceFingerprint: String,
    val apiKey: String
)

data class ExchangeCancelRequest(
    val listingId: Int,
    val playerId: String,
    val deviceFingerprint: String,
    val apiKey: String
)

data class TradeHistoryItem(
    val id: Int = 0,
    val listing_id: Int = 0,
    val item_key: String = "",
    val item_name: String = "",
    val qty: Int = 1,
    val price: Int = 0,
    val fee: Int = 0,
    val seller_id: String = "",
    val buyer_id: String = "",
    val created_at: String = ""
)

data class TradeHistoryResponse(
    val success: Boolean = false,
    val data: List<TradeHistoryItem> = emptyList(),
    val error: String? = null
)

// ========== 充值 ==========
data class RechargeOrderRequest(
    val playerId: String,
    val deviceFingerprint: String,
    val apiKey: String,
    val amount: Double,
    val item: String = "coins"
)

data class RechargeOrderResponse(
    val success: Boolean = false,
    val orderId: String? = null,
    val amount: Double = 0.0,
    val item: String = "coins",
    val qty: Int = 0,
    val error: String? = null
)

data class MarkPaidRequest(
    val orderId: String,
    val secret: String
)

// ========== 公告 / 版本 ==========
data class Announcement(
    val id: Int = 0,
    val title: String = "",
    val content: String = "",
    val created_at: String = ""
)

data class AnnouncementsResponse(
    val success: Boolean = false,
    val data: List<Announcement> = emptyList(),
    val error: String? = null
)

// ========== 邮箱 ==========
data class MailItem(
    val id: Int = 0,
    val title: String = "",
    val content: String = "",
    val coins: Int = 0,
    val rewards: JsonObject? = null,
    val claimed: Int = 0,
    val created_at: String = ""
)

data class MailListResponse(
    val success: Boolean = false,
    val data: List<MailItem> = emptyList(),
    val error: String? = null
)

data class MailClaimRequest(
    val playerId: String,
    val deviceFingerprint: String,
    val apiKey: String,
    val mailId: Int
)

data class MailClaimResponse(
    val success: Boolean = false,
    val coins: Int = 0,
    val applied: JsonObject? = null,
    val error: String? = null
)

// ========== 激活码 ==========
data class RedeemRequest(
    val playerId: String,
    val deviceFingerprint: String,
    val apiKey: String,
    val code: String
)

data class RedeemResponse(
    val success: Boolean = false,
    val message: String = "",
    val rewards: JsonObject? = null,
    val error: String? = null
)

// ========== 排行榜 ==========
data class LeaderboardItem(
    val playerId: String = "",
    val playerName: String = "",
    val value: Int = 0,
    val exp: Int = 0,
    val topLv: Int = 0,
    val count: Int = 0
)

data class LeaderboardResponse(
    val success: Boolean = false,
    val data: List<LeaderboardItem> = emptyList(),
    val error: String? = null
)

// ========== PVP ==========
data class PvpTargetItem(
    val playerId: String = "",
    val playerName: String = "",
    val rating: Int = 1000,
    val lv: Int = 1
)

data class PvpTargetsResponse(
    val success: Boolean = false,
    val data: List<PvpTargetItem> = emptyList(),
    val error: String? = null
)

data class PvpDefender(
    val playerId: String = "",
    val playerName: String = "",
    val rating: Int = 1000,
    @SerializedName("class") val cls: JsonObject? = null,
    val stats: JsonObject? = null,
    val equip: JsonObject? = null,
    val gear: JsonObject? = null,
    val pets: JsonObject? = null
)

data class PvpDefenderResponse(
    val success: Boolean = false,
    val defender: PvpDefender? = null,
    val error: String? = null
)

data class PvpReportRequest(
    val playerId: String,
    val deviceFingerprint: String,
    val apiKey: String,
    val targetId: String,
    val win: Boolean
)

data class PvpReportResponse(
    val success: Boolean = false,
    val rating: Int = 0,
    val delta: Int = 0,
    val win: Boolean = false,
    val error: String? = null
)

data class PvpRatingResponse(
    val success: Boolean = false,
    val rating: Int = 1000,
    val wins: Int = 0,
    val losses: Int = 0,
    val error: String? = null
)

// ========== 爱发电充值 ==========
data class AfdianUrlResponse(
    val success: Boolean = false,
    val afdianUrl: String = "",
    val error: String? = null
)

data class PaymentOrderItem(
    val order_id: String = "",
    val amount: Double = 0.0,
    val qty: Int = 0,
    val status: String = "",
    val created_at: String = "",
    val paid_at: String? = null
)

data class PaymentOrdersResponse(
    val success: Boolean = false,
    val data: List<PaymentOrderItem> = emptyList(),
    val error: String? = null
)

data class VersionResponse(
    val success: Boolean = false,
    val versionCode: Int = 1,
    val versionName: String = "",
    val downloadUrl: String = "",
    val updateNotes: String = "",
    val error: String? = null
)

data class RenameRequest(
    val playerId: String,
    val deviceFingerprint: String,
    val apiKey: String,
    val newName: String
)

data class RenameResponse(
    val success: Boolean = false,
    val playerName: String = "",
    val message: String = "",
    val error: String? = null
)

data class BaseResponse(
    val success: Boolean = false,
    val error: String? = null
)
