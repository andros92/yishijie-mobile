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
    private var returningFromBattle = false

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

    override fun onResume() {
        super.onResume()
        // 打完一场返回后清空对手列表：需要重新匹配
        if (returningFromBattle) {
            returningFromBattle = false
            binding.llMatch.removeAllViews()
            binding.btnStartMatch.text = "开始匹配"
        }
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
                    binding.tvRating.text = "${r.data?.rating ?: 1000}"
                    binding.tvWins.text = "胜 ${r.data?.wins ?: 0} · 负 ${r.data?.losses ?: 0}"
                    binding.tvDaily.text = "今日 ${r.data?.dailyLeft ?: 12} 次"
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
                        binding.tvMatchHint.text = "暂无可匹配的对手，稍后再试"
                        binding.btnStartMatch.text = "重新匹配"
                        return@launch
                    }
                    binding.tvMatchHint.text = "已匹配 ${list.size} 位对手 · 段位接近 · 点击挑战"
                    list.forEach { t -> binding.llMatch.addView(targetRow(t)) }
                    binding.btnStartMatch.text = "重新匹配"
                }
                is ApiResult.Error -> {
                    binding.llMatch.removeAllViews()
                    binding.tvMatchHint.text = "匹配失败：${r.message}"
                }
            }
        }
    }

    private fun showLeaderboard() {
        val c = credentials() ?: run { status("请先连接手环并登录账号"); return }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val scroll = android.widget.ScrollView(this).apply { addView(box) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(com.yishijie.chuanshuo.R.drawable.bg_card_player, theme)
            setPadding(dp(6), dp(8), dp(6), dp(8))
            addView(text("竞技场排行榜", 19f, Color.parseColor("#F5C453"), true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(2))
            })
            addView(text("段位定胜负 · 前三名授勋", 12f, Color.parseColor("#9AA3C0")).apply {
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(12))
            })
            box.addView(text("加载中...", 13f, Color.parseColor("#9AA3C0")).apply { gravity = Gravity.CENTER })
            addView(scroll)
        }
        val dialog = AlertDialog.Builder(this)
            .setView(panel)
            .setPositiveButton("关闭", null)
            .create()
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.pvpLeaderboard(c.first, c.second, c.third) }) {
                is ApiResult.Success -> {
                    box.removeAllViews()
                    val list = r.data?.data ?: emptyList()
                    if (list.isEmpty()) {
                        box.addView(text("暂无排名，快去打匹配吧", 14f, Color.parseColor("#9AA3C0")).apply {
                            gravity = Gravity.CENTER
                            setPadding(0, dp(20), 0, dp(20))
                        })
                        return@launch
                    }
                    list.forEachIndexed { i, it -> box.addView(leaderboardRow(i + 1, it.player_name, it.rating, it.wins, it.losses)) }
                }
                is ApiResult.Error -> {
                    box.removeAllViews()
                    box.addView(text("加载失败：${r.message}", 13f, Color.parseColor("#FF6B7A")).apply { gravity = Gravity.CENTER })
                }
            }
        }
    }

    private fun rankBadge(rank: Int): TextView {
        val res = when (rank) {
            1 -> com.yishijie.chuanshuo.R.drawable.rank_badge_gold
            2 -> com.yishijie.chuanshuo.R.drawable.rank_badge_silver
            3 -> com.yishijie.chuanshuo.R.drawable.rank_badge_bronze
            else -> com.yishijie.chuanshuo.R.drawable.rank_badge_normal
        }
        val color = when (rank) {
            1 -> "#F5C453"
            2 -> "#B8C2E0"
            3 -> "#D9925A"
            else -> "#9AA3C0"
        }
        return TextView(this).apply {
            text = "$rank"
            textSize = 14f
            setTextColor(Color.parseColor(color))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            background = resources.getDrawable(res, theme)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }
    }

    private fun leaderboardRow(rank: Int, name: String, rating: Int, wins: Int, losses: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = resources.getDrawable(com.yishijie.chuanshuo.R.drawable.bg_status_pill, theme)
        }
        row.addView(rankBadge(rank))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(12), 0, dp(8), 0)
        }
        info.addView(text(name.ifEmpty { "未知玩家" }, 15f, Color.parseColor("#F4F6FF"), true))
        info.addView(text("$wins 胜 · $losses 负", 11f, Color.parseColor("#9AA3C0")))
        row.addView(info)
        row.addView(text("$rating", 18f, Color.parseColor("#F5C453"), true))
        row.addView(text(" 分", 11f, Color.parseColor("#9AA3C0")))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(8)
        row.layoutParams = lp
        return row
    }

    private fun showMatchHistory() {
        val c = credentials() ?: run { status("请先连接手环并登录账号"); return }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val scroll = android.widget.ScrollView(this).apply { addView(box) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(com.yishijie.chuanshuo.R.drawable.bg_card_player, theme)
            setPadding(dp(6), dp(8), dp(6), dp(8))
            addView(text("我的对战", 19f, Color.parseColor("#F5C453"), true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(2))
            })
            addView(text("点击战绩查看回放", 12f, Color.parseColor("#9AA3C0")).apply {
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(12))
            })
            box.addView(text("加载中...", 13f, Color.parseColor("#9AA3C0")).apply { gravity = Gravity.CENTER })
            addView(scroll)
        }
        val dialog = AlertDialog.Builder(this)
            .setView(panel)
            .setPositiveButton("关闭", null)
            .create()
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), (resources.displayMetrics.heightPixels * 0.8).toInt())
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.pvpMatches(c.first, c.second, c.third) }) {
                is ApiResult.Success -> {
                    box.removeAllViews()
                    val list = r.data?.data ?: emptyList()
                    if (list.isEmpty()) {
                        box.addView(text("暂无对战记录", 14f, Color.parseColor("#9AA3C0")).apply {
                            gravity = Gravity.CENTER
                            setPadding(0, dp(20), 0, dp(20))
                        })
                        return@launch
                    }
                    list.forEach { m ->
                        val row = LinearLayout(this@PvpActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(12), dp(10), dp(12), dp(10))
                            background = resources.getDrawable(com.yishijie.chuanshuo.R.drawable.bg_status_pill, theme)
                            setOnClickListener { showReplay(m.opponent, m.log) }
                        }
                        val pill = text(if (m.win) "胜" else "负", 14f, Color.parseColor("#0B0F1F"), true).apply {
                            gravity = Gravity.CENTER
                            setPadding(dp(10), dp(4), dp(10), dp(4))
                            background = resources.getDrawable(
                                if (m.win) com.yishijie.chuanshuo.R.drawable.bg_btn_gold_gradient else com.yishijie.chuanshuo.R.drawable.bg_chip,
                                theme
                            )
                        }
                        row.addView(pill)
                        val info = LinearLayout(this@PvpActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            setPadding(dp(12), 0, dp(8), 0)
                        }
                        info.addView(text("vs ${m.opponent}", 15f, Color.parseColor("#F4F6FF"), true))
                        info.addView(text("${if (m.delta >= 0) "+" else ""}${m.delta} · ${m.createdAt}", 11f, Color.parseColor("#9AA3C0")))
                        row.addView(info)
                        row.addView(text("回放", 13f, Color.parseColor("#F5C453"), true))
                        box.addView(row, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(8) })
                    }
                }
                is ApiResult.Error -> {
                    box.removeAllViews()
                    box.addView(text("加载失败：${r.message}", 13f, Color.parseColor("#FF6B7A")).apply { gravity = Gravity.CENTER })
                }
            }
        }
    }

    private fun showReplay(opponent: String, log: List<String>) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        if (log.isEmpty()) {
            box.addView(text("本场没有可回放的日志", 13f, Color.parseColor("#9AA3C0")).apply { gravity = Gravity.CENTER })
        } else {
            log.forEach { line ->
                val color = when {
                    line.contains("暴击") -> "#FFD36D"
                    line.contains("胜利") || line.contains("败北") || line.contains("获胜") -> "#F4F6FF"
                    else -> "#C8D4E8"
                }
                box.addView(text(line, 13f, Color.parseColor(color)).apply { setPadding(dp(4), dp(2), dp(4), dp(2)) })
            }
        }
        val scroll = android.widget.ScrollView(this).apply { addView(box) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(com.yishijie.chuanshuo.R.drawable.bg_card_player, theme)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(text("战斗回放 · vs $opponent", 18f, Color.parseColor("#F5C453"), true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(10))
            })
            addView(scroll)
        }
        AlertDialog.Builder(this)
            .setView(panel)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun targetRow(t: PvpTargetItem): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            elevation = dp(2).toFloat()
            setCardBackgroundColor(Color.parseColor("#1A2242"))
            strokeColor = Color.parseColor("#33F5C453")
            strokeWidth = 1
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val avatar = TextView(this).apply {
            text = "⚔"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#F5C453"))
            background = resources.getDrawable(com.yishijie.chuanshuo.R.drawable.bg_avatar, theme)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        }
        inner.addView(avatar)
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(12), 0, dp(8), 0)
        }
        info.addView(text(t.playerName, 16f, Color.parseColor("#F4F6FF"), true))
        info.addView(text("Lv.${t.lv} · 段位 ${t.rating}", 12f, Color.parseColor("#9AA3C0")))
        inner.addView(info)
        val btn = TextView(this).apply {
            text = "挑战"
            textSize = 14f
            setTextColor(Color.parseColor("#0B0F1F"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(9), dp(20), dp(9))
            background = resources.getDrawable(com.yishijie.chuanshuo.R.drawable.bg_btn_gold_gradient, theme)
            setOnClickListener { confirmChallenge(t) }
        }
        inner.addView(btn)
        card.addView(inner)
        return card
    }

    private fun confirmChallenge(t: PvpTargetItem) {
        if (busy) return
        DialogUtils.showConfirm(
            this,
            "发起匹配",
            "向「${t.playerName}」（Lv.${t.lv}）发起挑战？\n对局结束后消耗 1 次今日匹配次数，双方使用真实存档，自己操控技能。",
            "挑战"
        ) {
            doMatch(t)
        }
    }

    private fun doMatch(t: PvpTargetItem) {
        val c = credentials() ?: return
        if (busy) return
        busy = true
        status("正在同步手环存档...")
        lifecycleScope.launch {
            syncWatchSave()
            busy = false
            returningFromBattle = true
            startActivityForResult(android.content.Intent(this@PvpActivity, PvpBattleActivity::class.java).apply {
                putExtra("targetId", t.playerId)
                putExtra("targetName", t.playerName)
            }, 1001)
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
