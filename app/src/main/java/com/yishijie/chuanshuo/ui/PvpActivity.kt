package com.yishijie.chuanshuo.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.api.PvpMatchRequest
import com.yishijie.chuanshuo.api.PvpTargetItem
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.ActivityPvpBinding
import com.yishijie.chuanshuo.interconnect.GameSyncManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.gson.JsonObject
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * PVP 竞技场（纯手机端）：匹配战由服务端模拟并结算，房间对战双方就绪后服务端模拟。
 * 开战前先从手环拉取最新存档上传，保证服务端用的是玩家当前属性。
 */
class PvpActivity : BaseActivity() {

    private lateinit var binding: ActivityPvpBinding
    private lateinit var deviceManager: DeviceManager
    private lateinit var syncManager: GameSyncManager
    private var roomCode = ""
    private var pollJob: Job? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPvpBinding.inflate(layoutInflater)
        setContentViewWithStatus(binding.root)
        deviceManager = DeviceManager.getInstance(this)
        syncManager = GameSyncManager.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.tabMatch.setOnClickListener { switchTab(0) }
        binding.tabRoom.setOnClickListener { switchTab(1) }
        binding.btnCreateRoom.setOnClickListener { createRoom() }
        binding.btnJoinRoom.setOnClickListener { joinRoom() }
        binding.btnRoomFight.setOnClickListener { fightRoom() }
        binding.btnStartMatch.setOnClickListener { startMatchmaking() }
        binding.btnLeaderboard.setOnClickListener { if (ClickGuard.allow()) showLeaderboard() }
        binding.btnReplay.setOnClickListener { if (ClickGuard.allow()) showMatchHistory() }

        loadRating()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun credentials(): Triple<String, String, String>? {
        val me = deviceManager.getCurrentPlayerId() ?: return null
        val fp = deviceManager.getDeviceFingerprint() ?: return null
        val key = ApiClient.apiKey ?: return null
        return Triple(me, fp, key)
    }

    private fun switchTab(tab: Int) {
        val match = tab == 0
        binding.llMatchArea.visibility = if (match) android.view.View.VISIBLE else android.view.View.GONE
        binding.llRoom.visibility = if (match) android.view.View.GONE else android.view.View.VISIBLE
        binding.tabMatch.background = resources.getDrawable(
            if (match) com.yishijie.chuanshuo.R.drawable.bg_btn_gold else com.yishijie.chuanshuo.R.drawable.bg_chip, theme
        )
        binding.tabRoom.background = resources.getDrawable(
            if (match) com.yishijie.chuanshuo.R.drawable.bg_chip else com.yishijie.chuanshuo.R.drawable.bg_btn_gold, theme
        )
        binding.tabMatch.setTextColor(Color.parseColor(if (match) "#0B0F1F" else "#9AA3C0"))
        binding.tabRoom.setTextColor(Color.parseColor(if (match) "#9AA3C0" else "#0B0F1F"))
        binding.tvStatus.text = ""
        pollJob?.cancel()
    }

    private fun loadRating() {
        val (me, _, _) = credentials() ?: return
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.pvpRating(me) }) {
                is ApiResult.Success -> {
                    binding.tvRating.text = "段位分：${r.data?.rating ?: 1000}"
                    binding.tvDaily.text = "今日剩余 ${r.data?.dailyLeft ?: 12} 次"
                }
                is ApiResult.Error -> status("获取段位失败：${r.message}")
            }
        }
    }

    /**
     * 开始匹配：按 Elo 从服务器取 5 个段位最接近的对手（参照垃圾佬 battle_match）
     */
    private fun startMatchmaking() {
        val c = credentials() ?: run { status("请先连接手环并登录账号"); return }
        DialogUtils.showConfirm(
            this,
            "开始匹配",
            "将按段位为你匹配 5 位实力接近的对手，确认开始？（${binding.tvDaily.text}）",
            "开始匹配"
        ) {
            doMatchmake()
        }
    }

    private fun doMatchmake() {
        val c = credentials() ?: run { status("请先连接手环并登录账号"); return }
        binding.llMatch.removeAllViews()
        binding.llMatch.addView(text("正在匹配对手...", 14f, Color.parseColor("#9AA3C0")))
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.pvpMatchmake(c.first, c.second, c.third) }) {
                is ApiResult.Success -> {
                    binding.llMatch.removeAllViews()
                    val list = r.data?.data ?: emptyList()
                    if (list.isEmpty()) {
                        binding.llMatch.addView(text("暂无可匹配的对手，稍后再试", 14f, Color.parseColor("#9AA3C0")))
                        binding.btnStartMatch.text = "重新匹配"
                        return@launch
                    }
                    binding.llMatch.addView(
                        text("已匹配 ${list.size} 位对手（段位接近），点“挑战”开战", 13f, Color.parseColor("#9AA3C0"))
                    )
                    list.forEach { t -> binding.llMatch.addView(targetRow(t)) }
                    binding.btnStartMatch.text = "重新匹配"
                }
                is ApiResult.Error -> {
                    binding.llMatch.removeAllViews()
                    binding.llMatch.addView(text("匹配失败：${r.message}", 14f, Color.parseColor("#FF6B7A")))
                }
            }
        }
    }

    private fun showLeaderboard() {
        val c = credentials() ?: run { status("请先连接手环并登录账号"); return }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        box.addView(text("加载中...", 13f, Color.parseColor("#9AA3C0")))
        val scroll = android.widget.ScrollView(this).apply { addView(box) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("竞技场排行榜")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .create()
        dialog.show()
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.pvpLeaderboard(c.first, c.second, c.third) }) {
                is ApiResult.Success -> {
                    box.removeAllViews()
                    val list = r.data?.data ?: emptyList()
                    if (list.isEmpty()) {
                        box.addView(text("暂无排名，快去打匹配吧", 14f, Color.parseColor("#9AA3C0")))
                        return@launch
                    }
                    list.forEachIndexed { i, it ->
                        box.addView(
                            text(
                                "${i + 1}. ${it.player_name.ifEmpty { "未知玩家" }} · ${it.rating} 分（${it.wins}胜${it.losses}负）",
                                13f, Color.parseColor("#C8D4E8")
                            )
                        )
                    }
                }
                is ApiResult.Error -> {
                    box.removeAllViews()
                    box.addView(text("加载失败：${r.message}", 13f, Color.parseColor("#FF6B7A")))
                }
            }
        }
    }

    private fun showMatchHistory() {
        val c = credentials() ?: run { status("请先连接手环并登录账号"); return }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        box.addView(text("加载中...", 13f, Color.parseColor("#9AA3C0")))
        val scroll = android.widget.ScrollView(this).apply { addView(box) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("我的对战 · 点战绩看回放")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .create()
        dialog.show()
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.pvpMatches(c.first, c.second, c.third) }) {
                is ApiResult.Success -> {
                    box.removeAllViews()
                    val list = r.data?.data ?: emptyList()
                    if (list.isEmpty()) {
                        box.addView(text("暂无对战记录", 14f, Color.parseColor("#9AA3C0")))
                        return@launch
                    }
                    list.forEach { m ->
                        val row = text(
                            "vs ${m.opponent} · ${if (m.win) "胜" else "负"} ${if (m.delta >= 0) "+" else ""}${m.delta} · ${m.createdAt}",
                            13f,
                            if (m.win) Color.parseColor("#5CFFB8") else Color.parseColor("#FF9A9A")
                        )
                        row.setPadding(dp(4), dp(8), dp(4), dp(8))
                        row.setOnClickListener { showReplay(m.opponent, m.log) }
                        box.addView(row)
                    }
                }
                is ApiResult.Error -> {
                    box.removeAllViews()
                    box.addView(text("加载失败：${r.message}", 13f, Color.parseColor("#FF6B7A")))
                }
            }
        }
    }

    private fun showReplay(opponent: String, log: List<String>) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        if (log.isEmpty()) {
            box.addView(text("本场没有可回放的日志", 13f, Color.parseColor("#9AA3C0")))
        } else {
            log.forEach { line ->
                val color = when {
                    line.contains("暴击") -> "#FFD36D"
                    line.contains("胜利") || line.contains("败北") || line.contains("获胜") -> "#F4F6FF"
                    else -> "#C8D4E8"
                }
                box.addView(text(line, 13f, Color.parseColor(color)))
            }
        }
        val scroll = android.widget.ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this)
            .setTitle("战斗回放 · vs $opponent")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun targetRow(t: PvpTargetItem): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(14).toFloat()
            elevation = dp(1).toFloat()
            setCardBackgroundColor(Color.parseColor("#1A2242"))
            strokeColor = Color.parseColor("#2EFFFFFF")
            strokeWidth = 1
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(text(t.playerName, 15f, Color.parseColor("#F4F6FF"), true))
        info.addView(text("Lv.${t.lv} · 段位 ${t.rating}", 12f, Color.parseColor("#9AA3C0")))
        inner.addView(info)
        val btn = TextView(this).apply {
            text = "挑战"
            textSize = 13f
            setTextColor(Color.parseColor("#0B0F1F"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = resources.getDrawable(com.yishijie.chuanshuo.R.drawable.bg_btn_gold, theme)
            setOnClickListener { confirmChallenge(t) }
        }
        inner.addView(btn)
        card.addView(inner)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(8)
        card.layoutParams = lp
        return card
    }

    private fun confirmChallenge(t: PvpTargetItem) {
        if (busy) return
        DialogUtils.showConfirm(
            this,
            "发起匹配",
            "向「${t.playerName}」（Lv.${t.lv}）发起挑战？\n本场由服务器模拟结算，消耗 1 次今日匹配次数。",
            "挑战"
        ) {
            doMatch(t)
        }
    }

    private fun doMatch(t: PvpTargetItem) {
        val c = credentials() ?: return
        if (busy) return
        busy = true
        status("正在同步手环存档并匹配...")
        lifecycleScope.launch {
            syncWatchSave()
            when (val r = ApiClient.safeApiCall {
                ApiClient.api.pvpMatch(PvpMatchRequest(c.first, c.second, c.third, t.playerId))
            }) {
                is ApiResult.Success -> {
                    val d = r.data
                    if (d?.success == true) {
                        showBattleLog(
                            if (d.aWin) "胜利！" else "败北",
                            d.log,
                            "段位 ${d.rating}（${if (d.delta >= 0) "+" else ""}${d.delta}）· 今日剩余 ${d.dailyLeft} 次"
                        )
                        binding.tvRating.text = "段位分：${d.rating}"
                        binding.tvDaily.text = "今日剩余 ${d.dailyLeft} 次"
                    } else {
                        status(d?.error ?: "匹配失败")
                    }
                }
                is ApiResult.Error -> status("匹配失败：${r.message}")
            }
            busy = false
        }
    }

    private fun createRoom() {
        val c = credentials() ?: return
        if (busy) return
        busy = true
        status("创建房间中...")
        lifecycleScope.launch {
            val body = JsonObject().apply {
                addProperty("playerId", c.first)
                addProperty("deviceFingerprint", c.second)
                addProperty("apiKey", c.third)
            }
            when (val r = ApiClient.safeApiCall { ApiClient.api.pvpRoomCreate(body) }) {
                is ApiResult.Success -> {
                    roomCode = r.data?.get("roomCode")?.asString ?: ""
                    status("房间创建成功：$roomCode，等待对手加入")
                    binding.tvRoomStatus.text = "房间码：$roomCode\n等待对手加入（分享房间码给好友）"
                    startPolling()
                }
                is ApiResult.Error -> status("创建失败：${r.message}")
            }
            busy = false
        }
    }

    private fun joinRoom() {
        val c = credentials() ?: return
        val code = binding.etRoomCode.text.toString().trim()
        if (!Regex("^\\d{4}$").matches(code)) {
            status("请输入 4 位数字房间码")
            return
        }
        if (busy) return
        busy = true
        status("加入房间中...")
        lifecycleScope.launch {
            val body = JsonObject().apply {
                addProperty("playerId", c.first)
                addProperty("deviceFingerprint", c.second)
                addProperty("apiKey", c.third)
                addProperty("roomCode", code)
            }
            when (val r = ApiClient.safeApiCall { ApiClient.api.pvpRoomJoin(body) }) {
                is ApiResult.Success -> {
                    roomCode = code
                    status("已加入房间 $code")
                    startPolling()
                }
                is ApiResult.Error -> status("加入失败：${r.message}")
            }
            busy = false
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (roomCode.isNotEmpty()) {
                if (credentials() == null) break
                when (val r = ApiClient.safeApiCall { ApiClient.api.pvpRoomStatus(roomCode) }) {
                    is ApiResult.Success -> {
                        val room = r.data?.getAsJsonObject("room") ?: break
                        val status2 = room.get("status")?.asString ?: ""
                        val host = room.get("hostName")?.asString ?: ""
                        val guest = room.get("guestName")?.asString ?: ""
                        val winner = room.get("winner")?.asString ?: ""
                        binding.tvRoomStatus.text = "房间码：${room.get("code")?.asString ?: roomCode}\n房主：$host\n对手：${guest.ifEmpty { "等待中" }}\n状态：${when (status2) {
                            "waiting" -> "等待对手"
                            "ready" -> "双方就绪，可开战"
                            "finished" -> "已结束${if (winner.isNotEmpty()) "（胜者：$winner）" else ""}"
                            else -> status2
                        }}"
                        if (status2 == "finished") {
                            pollJob?.cancel()
                            binding.tvRoomStatus.text = (room.get("log")?.asString ?: "") + if (winner.isNotEmpty()) "\n\n胜者：$winner" else ""
                            break
                        }
                    }
                    is ApiResult.Error -> {
                        status("房间状态获取失败：${r.message}")
                        break
                    }
                }
                delay(3000)
            }
        }
    }

    private fun fightRoom() {
        val c = credentials() ?: return
        if (roomCode.isEmpty()) {
            status("请先创建或加入房间")
            return
        }
        if (busy) return
        busy = true
        status("正在同步手环存档并开战...")
        lifecycleScope.launch {
            syncWatchSave()
            val body = JsonObject().apply {
                addProperty("playerId", c.first)
                addProperty("deviceFingerprint", c.second)
                addProperty("apiKey", c.third)
                addProperty("roomCode", roomCode)
            }
            when (val r = ApiClient.safeApiCall { ApiClient.api.pvpRoomFight(body) }) {
                is ApiResult.Success -> {
                    val winner = r.data?.get("winner")?.asString ?: ""
                    val log = r.data?.getAsJsonArray("log")?.let { arr ->
                        (0 until arr.size()).map { arr.get(it).asString }
                    } ?: emptyList()
                    showBattleLog(if (winner == "host" || winner == "guest") "对战结束" else "对战结束", log, if (winner.isNotEmpty()) "胜者：$winner" else "")
                    pollJob?.cancel()
                    roomCode = ""
                    binding.tvRoomStatus.text = ""
                }
                is ApiResult.Error -> status("开战失败：${r.message}")
            }
            busy = false
        }
    }

    /** 从手环拉取当前存档并上传服务器，保证对战使用最新属性 */
    private suspend fun syncWatchSave() {
        val data = suspendCancellableCoroutine<JSONObject?> { cont ->
            syncManager.downloadSaveFromBand(object : GameSyncManager.SaveCallback {
                override fun onSaveUploaded(success: Boolean, message: String) {}
                override fun onSaveDownloaded(data: JSONObject?) { cont.resume(data) }
                override fun onError(error: String) { cont.resume(null) }
            })
        }
        if (data == null) return
        runCatching { syncManager.uploadSaveToServer(com.google.gson.JsonParser().parse(data.toString()).asJsonObject) }
    }

    private fun showBattleLog(title: String, log: List<String>, summary: String) {
        val sb = StringBuilder()
        log.forEach { sb.append(it).append('\n') }
        if (summary.isNotEmpty()) sb.append('\n').append(summary)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(sb.toString().ifEmpty { "无战斗记录" })
            .setPositiveButton("确定", null)
            .show()
    }

    private fun text(txt: String, size: Float = 14f, color: Int = Color.parseColor("#F4F6FF"), bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = txt
            textSize = size
            setTextColor(color)
            setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            gravity = Gravity.START
        }
    }

    private fun status(msg: String) {
        binding.tvStatus.text = msg
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
