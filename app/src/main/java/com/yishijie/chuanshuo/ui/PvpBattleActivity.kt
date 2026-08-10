package com.yishijie.chuanshuo.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.api.PvpBattleSkill
import com.yishijie.chuanshuo.api.PvpBattleStartRequest
import com.yishijie.chuanshuo.api.PvpBattleState
import com.yishijie.chuanshuo.api.PvpBattleUnit
import com.yishijie.chuanshuo.api.PvpTurnRequest
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.ActivityPvpBattleBinding
import kotlinx.coroutines.launch

/**
 * PVP 实时对战（手机端操控）：每回合把玩家选择的动作发给服务端，服务端权威结算后返回双方状态。
 * 存档使用最后一次上传的存档，宠物协同出战，敌方由 AI 操控（对手真实存档）。
 */
class PvpBattleActivity : BaseActivity() {

    private lateinit var binding: ActivityPvpBattleBinding
    private lateinit var deviceManager: DeviceManager
    private var targetId = ""
    private var targetName = ""
    private var busy = false
    private var ended = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPvpBattleBinding.inflate(layoutInflater)
        setContentViewWithStatus(binding.root)
        deviceManager = DeviceManager.getInstance(this)
        targetId = intent.getStringExtra("targetId") ?: ""
        targetName = intent.getStringExtra("targetName") ?: "对手"

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAttack.setOnClickListener { if (!busy && !ended) sendAction(JsonObject().apply { addProperty("type", "attack") }) }
        binding.btnSkills.setOnClickListener { if (!busy && !ended) showSkills() }
        binding.btnFlee.setOnClickListener { if (!busy && !ended) confirmFlee() }
        binding.btnCloseSkills.setOnClickListener { binding.skillPanel.visibility = android.view.View.GONE }
        binding.enemyName.text = targetName

        startBattle()
    }

    private fun credentials(): Triple<String, String, String>? {
        val me = deviceManager.getCurrentPlayerId() ?: return null
        val fp = deviceManager.getDeviceFingerprint() ?: return null
        val key = ApiClient.apiKey ?: return null
        return Triple(me, fp, key)
    }

    private fun startBattle() {
        val c = credentials() ?: run { toast("请先连接手环并登录账号"); finish(); return }
        busy = true
        binding.logText.text = "正在读取双方存档..."
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall {
                ApiClient.api.pvpBattleStart(PvpBattleStartRequest(c.first, c.second, c.third, targetId))
            }) {
                is ApiResult.Success -> {
                    val d = r.data
                    if (d?.success == true && d.battle != null) {
                        render(d.battle)
                    } else {
                        toast(d?.error ?: "开战失败")
                        finish()
                    }
                }
                is ApiResult.Error -> {
                    toast("开战失败：${r.message}")
                    finish()
                }
            }
            busy = false
        }
    }

    private fun sendAction(action: JsonObject) {
        val c = credentials() ?: return
        if (busy || ended) return
        busy = true
        setControlsEnabled(false)
        binding.skillPanel.visibility = android.view.View.GONE
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall {
                ApiClient.api.pvpBattleTurn(PvpTurnRequest(c.first, c.second, c.third, targetId, action))
            }) {
                is ApiResult.Success -> {
                    val d = r.data
                    if (d?.success == true && d.battle != null) {
                        render(d.battle)
                        if (d.ended) {
                            ended = true
                            setControlsEnabled(false)
                            showResult(d.win == true, d.rating, d.delta, d.dailyLeft, d.battle.log)
                        }
                    } else {
                        toast(d?.error ?: "回合失败")
                        if (d?.error?.contains("过期") == true || d?.error?.contains("不存在") == true) {
                            finish()
                        }
                    }
                }
                is ApiResult.Error -> toast("回合失败：${r.message}")
            }
            busy = false
            setControlsEnabled(!ended)
        }
    }

    private fun render(s: PvpBattleState) {
        val a = s.attacker
        val d = s.defender
        binding.tvTurn.text = "回合 ${s.turn}"
        if (d != null) {
            binding.enemyName.text = d.name + " Lv." + d.lv
            binding.enemyRating.text = if (d.isWarrior) "怒${d.rage}" else if (d.isMage) "充${d.charge}" else ""
            setBar(binding.enemyHpFill, binding.enemyHpRest, d.hp, d.maxHp)
            setBar(binding.enemyMpFill, binding.enemyMpRest, d.mp, d.maxMp)
            binding.enemyHpText.text = "${d.hp}/${d.maxHp}"
            binding.enemyMpText.text = "${d.mp}/${d.maxMp}"
        }
        if (a != null) {
            binding.myName.text = a.name + " Lv." + a.lv
            binding.myPet.text = a.pet?.let { "宠物：${it.name} Lv.${it.lv}" } ?: ""
            setBar(binding.myHpFill, binding.myHpRest, a.hp, a.maxHp)
            setBar(binding.myMpFill, binding.myMpRest, a.mp, a.maxMp)
            binding.myHpText.text = "${a.hp}/${a.maxHp}"
            binding.myMpText.text = "${a.mp}/${a.maxMp}"
            val extra = StringBuilder()
            if (a.isWarrior) extra.append("怒气 ${a.rage}/100")
            if (a.isMage) extra.append(if (extra.isNotEmpty()) " · " else "").append("充能 ${a.charge}/5")
            if (a.shield > 0) extra.append(if (extra.isNotEmpty()) " · " else "").append("护盾 ${a.shield}")
            if (a.burnTurns > 0) extra.append(if (extra.isNotEmpty()) " · " else "").append("灼烧 ${a.burnTurns}回合")
            if (a.poisonTurns > 0) extra.append(if (extra.isNotEmpty()) " · " else "").append("中毒 ${a.poisonTurns}回合")
            if (a.atkDown > 0) extra.append(if (extra.isNotEmpty()) " · " else "").append("攻击减半")
            if (a.dmgRed > 0) extra.append(if (extra.isNotEmpty()) " · " else "").append("减伤")
            binding.myExtra.text = extra.toString()
            renderSkills(a.skills)
        }
        binding.logText.text = s.log.joinToString("\n")
        val scroll = binding.logText.parent as? android.widget.ScrollView
        scroll?.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun renderSkills(skills: List<PvpBattleSkill>) {
        binding.skillList.removeAllViews()
        skills.forEach { sk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = resources.getDrawable(com.yishijie.chuanshuo.R.drawable.bg_input, theme)
                setOnClickListener {
                    binding.skillPanel.visibility = android.view.View.GONE
                    sendAction(JsonObject().apply {
                        addProperty("type", "skill")
                        addProperty("skill", sk.key)
                    })
                }
            }
            row.addView(text(sk.name, 14f, Color.parseColor("#F4F6FF"), true).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(text(sk.cost, 12f, Color.parseColor("#F5C453")))
            binding.skillList.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) })
        }
    }

    private fun showSkills() {
        binding.skillPanel.visibility = android.view.View.VISIBLE
    }

    private fun confirmFlee() {
        DialogUtils.showConfirm(this, "撤退", "撤退视为本场失败（不消耗次数、不结算积分），确定吗？", "撤退") {
            sendAction(JsonObject().apply { addProperty("type", "flee") })
        }
    }

    private fun showResult(win: Boolean, rating: Int?, delta: Int?, dailyLeft: Int?, log: List<String>) {
        val msg = StringBuilder()
        if (win) msg.append("你击败了对手！")
        else msg.append("你被对手击败了！")
        if (rating != null) msg.append("\n段位 $rating（${if ((delta ?: 0) >= 0) "+" else ""}${delta ?: 0}）")
        if (dailyLeft != null) msg.append("\n今日剩余 $dailyLeft 次")
        AlertDialog.Builder(this)
            .setTitle(if (win) "胜利！" else "败北")
            .setMessage(msg.toString())
            .setPositiveButton("查看回放") { _, _ -> showReplay(log) }
            .setNegativeButton("返回竞技场") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showReplay(log: List<String>) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        (log.ifEmpty { listOf("本场没有可回放的日志") }).forEach { line ->
            box.addView(text(line, 13f, Color.parseColor("#C8D4E8")))
        }
        val scroll = android.widget.ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this)
            .setTitle("战斗回放 · vs $targetName")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .setNegativeButton("返回竞技场") { _, _ -> finish() }
            .show()
    }

    private fun setBar(fill: android.view.View, rest: android.view.View, cur: Int, max: Int) {
        val pct = if (max <= 0) 0f else (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        val f = fill.layoutParams as LinearLayout.LayoutParams
        val r = rest.layoutParams as LinearLayout.LayoutParams
        f.weight = pct
        r.weight = 1f - pct
        fill.layoutParams = f
        rest.layoutParams = r
    }

    private fun setControlsEnabled(on: Boolean) {
        binding.btnAttack.isEnabled = on
        binding.btnSkills.isEnabled = on
        binding.btnFlee.isEnabled = on
        binding.btnAttack.alpha = if (on) 1f else 0.4f
        binding.btnSkills.alpha = if (on) 1f else 0.4f
        binding.btnFlee.alpha = if (on) 1f else 0.4f
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
