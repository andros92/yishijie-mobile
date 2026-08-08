package com.yishijie.chuanshuo.api

import com.google.gson.JsonObject

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
    val img: String,
    val qty: Int,
    val price: Int,
    val quality: String? = null,
    val affixes: List<String>? = null,
    val gem: String? = null,
    val dur: Int = 0,
    val maxDur: Int = 0,
    val broken: Boolean = false
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
    val item_img: String = "",
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
    val error: String? = null
)

data class VersionResponse(
    val success: Boolean = false,
    val version: String = "",
    val error: String? = null
)

data class BaseResponse(
    val success: Boolean = false,
    val error: String? = null
)
