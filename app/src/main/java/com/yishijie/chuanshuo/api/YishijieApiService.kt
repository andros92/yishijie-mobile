package com.yishijie.chuanshuo.api

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.*

interface YishijieApiService {

    // ========== 注册 / 登录 ==========
    @POST("/api/yishijie/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("/api/yishijie/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // ========== 存档 ==========
    @GET("/api/yishijie/saves/{playerId}")
    suspend fun downloadSave(
        @Path("playerId") playerId: String,
        @Query("deviceFingerprint") deviceFingerprint: String,
        @Query("apiKey") apiKey: String
    ): Response<SaveResponse>

    @POST("/api/yishijie/saves/{playerId}")
    suspend fun uploadSave(
        @Path("playerId") playerId: String,
        @Body request: SaveUploadRequest
    ): Response<SaveResponse>

    // ========== 交易所 ==========
    @POST("/api/yishijie/exchange/list")
    suspend fun exchangeList(
        @Body request: ExchangeListRequest,
        @Header("X-Yishijie-Channel") channel: String = "companion"
    ): Response<ExchangeListResponse>

    @GET("/api/yishijie/exchange/listings")
    suspend fun exchangeListings(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 10,
        @Query("category") category: String? = null,
        @Query("keyword") keyword: String? = null,
        @Query("mine") mine: Boolean? = null,
        @Query("playerId") playerId: String? = null,
        @Query("deviceFingerprint") deviceFingerprint: String? = null,
        @Query("apiKey") apiKey: String? = null
    ): Response<ListingsResponse>

    @POST("/api/yishijie/exchange/buy")
    suspend fun exchangeBuy(
        @Body request: ExchangeBuyRequest,
        @Header("X-Yishijie-Channel") channel: String = "companion"
    ): Response<BaseResponse>

    @POST("/api/yishijie/exchange/cancel")
    suspend fun exchangeCancel(
        @Body request: ExchangeCancelRequest,
        @Header("X-Yishijie-Channel") channel: String = "companion"
    ): Response<BaseResponse>

    @GET("/api/yishijie/exchange/history")
    suspend fun exchangeHistory(@Query("playerId") playerId: String): Response<TradeHistoryResponse>

    // ========== 充值 ==========
    @POST("/api/yishijie/recharge/order")
    suspend fun createRechargeOrder(@Body request: RechargeOrderRequest): Response<RechargeOrderResponse>

    @POST("/api/yishijie/admin/mark-paid")
    suspend fun markOrderPaid(@Body request: MarkPaidRequest): Response<BaseResponse>

    // ========== 邮箱 ==========
    @GET("/api/yishijie/mail/{playerId}")
    suspend fun mailList(
        @Path("playerId") playerId: String,
        @Query("deviceFingerprint") deviceFingerprint: String,
        @Query("apiKey") apiKey: String
    ): Response<MailListResponse>

    @POST("/api/yishijie/mail/claim")
    suspend fun claimMail(@Body request: MailClaimRequest): Response<MailClaimResponse>

    // ========== 激活码 ==========
    @POST("/api/yishijie/redeem/redeem")
    suspend fun redeem(@Body request: RedeemRequest): Response<RedeemResponse>

    // ========== 排行榜 ==========
    @GET("/api/yishijie/leaderboard")
    suspend fun leaderboard(
        @Query("type") type: String = "level",
        @Query("limit") limit: Int = 50
    ): Response<LeaderboardResponse>

    // ========== PVP ==========
    @GET("/api/yishijie/pvp/targets")
    suspend fun pvpTargets(
        @Query("playerId") playerId: String,
        @Query("deviceFingerprint") deviceFingerprint: String,
        @Query("apiKey") apiKey: String
    ): Response<PvpTargetsResponse>

    @GET("/api/yishijie/pvp/matchmake")
    suspend fun pvpMatchmake(
        @Query("playerId") playerId: String,
        @Query("deviceFingerprint") deviceFingerprint: String,
        @Query("apiKey") apiKey: String
    ): Response<PvpTargetsResponse>

    @GET("/api/yishijie/pvp/defender")
    suspend fun pvpDefender(
        @Query("playerId") playerId: String,
        @Query("deviceFingerprint") deviceFingerprint: String,
        @Query("apiKey") apiKey: String,
        @Query("targetId") targetId: String
    ): Response<PvpDefenderResponse>

    @POST("/api/yishijie/pvp/report")
    suspend fun pvpReport(@Body request: PvpReportRequest): Response<PvpReportResponse>

    @POST("/api/yishijie/pvp/match")
    suspend fun pvpMatch(@Body request: PvpMatchRequest): Response<PvpMatchResponse>

    @GET("/api/yishijie/pvp/rating")
    suspend fun pvpRating(@Query("playerId") playerId: String): Response<PvpRatingResponse>

    @GET("/api/yishijie/pvp/leaderboard")
    suspend fun pvpLeaderboard(
        @Query("playerId") playerId: String,
        @Query("deviceFingerprint") deviceFingerprint: String,
        @Query("apiKey") apiKey: String
    ): Response<PvpLeaderboardResponse>

    @GET("/api/yishijie/pvp/matches")
    suspend fun pvpMatches(
        @Query("playerId") playerId: String,
        @Query("deviceFingerprint") deviceFingerprint: String,
        @Query("apiKey") apiKey: String
    ): Response<PvpMatchesResponse>

    // ========== PVP 房间对战 ==========
    @POST("/api/yishijie/pvp/room/create")
    suspend fun pvpRoomCreate(@Body body: JsonObject): Response<JsonObject>

    @POST("/api/yishijie/pvp/room/join")
    suspend fun pvpRoomJoin(@Body body: JsonObject): Response<JsonObject>

    @GET("/api/yishijie/pvp/room/{code}")
    suspend fun pvpRoomStatus(@Path("code") code: String): Response<JsonObject>

    @POST("/api/yishijie/pvp/room/fight")
    suspend fun pvpRoomFight(@Body body: JsonObject): Response<JsonObject>

    // ========== 爱发电充值 ==========
    @GET("/api/yishijie/payment/afdian-url")
    suspend fun afdianUrl(): Response<AfdianUrlResponse>

    @GET("/api/yishijie/payment/orders")
    suspend fun paymentOrders(
        @Query("playerId") playerId: String,
        @Query("deviceFingerprint") deviceFingerprint: String,
        @Query("apiKey") apiKey: String
    ): Response<PaymentOrdersResponse>

    // ========== 公告 / 版本 ==========
    @GET("/api/yishijie/announcements")
    suspend fun announcements(): Response<AnnouncementsResponse>

    @GET("/api/yishijie/version")
    suspend fun version(): Response<VersionResponse>

    @POST("/api/yishijie/rename")
    suspend fun rename(@Body request: RenameRequest): Response<RenameResponse>

    // 通用存档负载（手环整包存档）
    @GET("/api/yishijie/saves/{playerId}/raw")
    suspend fun downloadRawSave(
        @Path("playerId") playerId: String,
        @Query("deviceFingerprint") deviceFingerprint: String,
        @Query("apiKey") apiKey: String
    ): Response<JsonObject>
}
