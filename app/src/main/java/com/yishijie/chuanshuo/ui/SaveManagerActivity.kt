package com.yishijie.chuanshuo.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yishijie.chuanshuo.R
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.ActivitySaveManagerBinding
import com.yishijie.chuanshuo.interconnect.GameSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SaveManagerActivity : BaseActivity() {

    private lateinit var binding: ActivitySaveManagerBinding
    private lateinit var syncManager: GameSyncManager
    private lateinit var deviceManager: DeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySaveManagerBinding.inflate(layoutInflater)
        setContentViewWithStatus(binding.root)
        syncManager = GameSyncManager.getInstance(this)
        deviceManager = DeviceManager.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }

        // 上传：从手环取整包存档 → 上传服务器 → 刷新概况
        binding.btnUploadServer.setOnClickListener {
            if (!requireLogin()) return@setOnClickListener
            status("等待手环存档…")
            syncManager.downloadSaveFromBand(object : GameSyncManager.SaveCallback {
                override fun onSaveUploaded(success: Boolean, message: String) {}
                override fun onSaveDownloaded(data: JSONObject?) {
                    if (data == null) {
                        status("手环没有返回存档，请确认手环已连接")
                        return
                    }
                    lifecycleScope.launch {
                        status("上传中…")
                        val res = withContext(Dispatchers.IO) {
                            syncManager.uploadSaveToServerWithResult(JsonParser().parse(data.toString()).asJsonObject)
                        }
                        status(if (res.ok) "上传成功：云存档已更新" else "上传失败：${res.error ?: "未知原因"}")
                        loadCloudSummary()
                    }
                }
                override fun onError(error: String) {
                    status("获取手环存档失败：$error")
                }
            })
        }

        // 恢复：从服务器下载 → 下发给手环（每日限 1 次）
        binding.btnRestoreServer.setOnClickListener {
            if (!requireLogin()) return@setOnClickListener
            if (!deviceManager.canRestoreToday()) {
                status("今天已恢复过存档（每日限 1 次），明天再来")
                return@setOnClickListener
            }
            showCustomConfirm("恢复云存档", "将从云存档恢复到手环，覆盖手环当前存档（每日限 1 次）。确定继续？") {
                doRestore()
            }
        }

        binding.btnRefreshCloud.setOnClickListener {
            status("已刷新")
            loadCloudSummary()
        }

        loadCloudSummary()
    }

    /**
     * 自绘确认弹窗（不用系统默认弹窗）
     */
    private fun showCustomConfirm(title: String, message: String, onOk: () -> Unit) {
        val card = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            elevation = dp(2).toFloat()
            setCardBackgroundColor(Color.parseColor("#141C34"))
            strokeColor = Color.parseColor("#3D5291")
            strokeWidth = 1
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
        }
        inner.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(Color.parseColor("#FFD36D"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        inner.addView(TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#F4F6FF"))
            setPadding(0, dp(12), 0, dp(18))
        })
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val cancel = TextView(this).apply {
            text = "取消"
            textSize = 15f
            setTextColor(Color.parseColor("#C8D4E8"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(26), dp(10), dp(26), dp(10))
            background = resources.getDrawable(R.drawable.bg_chip, theme)
        }
        val ok = TextView(this).apply {
            text = "恢复"
            textSize = 15f
            setTextColor(Color.parseColor("#0B0F1F"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(26), dp(10), dp(26), dp(10))
            background = resources.getDrawable(R.drawable.bg_btn_gold, theme)
        }
        val dialog = Dialog(this).apply {
            setContentView(card)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        cancel.setOnClickListener { dialog.dismiss() }
        ok.setOnClickListener {
            dialog.dismiss()
            onOk()
        }
        btnRow.addView(cancel)
        btnRow.addView(ok, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(8)
        })
        inner.addView(btnRow)
        card.addView(inner)
        dialog.show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun doRestore() {
        if (deviceManager.getCurrentPlayerId() == null) return
        if (!deviceManager.canRestoreToday()) {
            status("今天已恢复过存档（每日限 1 次），明天再来")
            return
        }
        lifecycleScope.launch {
            status("下载云存档…")
            val data = withContext(Dispatchers.IO) { syncManager.downloadSaveFromServer() }
            if (data == null) {
                status("下载失败：服务器上没有存档，或登录/网络异常")
                return@launch
            }
            deviceManager.markRestoredToday()
            status("已从服务器下载，正在下发给手环…")
            syncManager.uploadSaveToBand(JSONObject(data.toString()), object : GameSyncManager.SaveCallback {
                override fun onSaveUploaded(success: Boolean, message: String) {}
                override fun onSaveDownloaded(data: JSONObject?) {}
                override fun onError(error: String) {
                    status("下发给手环失败：$error")
                }
            })
        }
    }

    private fun requireLogin(): Boolean {
        if (deviceManager.getCurrentPlayerId() == null) {
            status("请先登录账号（首页注册/登录）")
            return false
        }
        return true
    }

    private fun status(msg: String) {
        binding.tvStatus.text = msg
    }

    private fun loadCloudSummary() {
        lifecycleScope.launch {
            binding.tvCloudInfo.text = "正在读取云存档…"
            val data = withContext(Dispatchers.IO) { syncManager.downloadSaveFromServer() }
            renderCloud(data)
        }
    }

    private fun renderCloud(data: JsonObject?) {
        if (data == null) {
            binding.tvCloudInfo.text = "暂无云存档\n\n点上方“上传当前存档”把背包/等级/宠物/装备备份到服务器。"
            return
        }
        val lines = ArrayList<String>()
        val stats = data.obj("stats")
        val bag = data.obj("bag")
        val cls = data.obj("class")

        // 完整性检查：真实存档至少要有等级属性和背包
        val incomplete = ArrayList<String>()
        if (stats == null || !stats.has("lv")) incomplete.add("等级/属性(stats)")
        if (bag == null) incomplete.add("背包(bag)")
        if (incomplete.isNotEmpty()) {
            lines.add("⚠️ 存档不完整（缺少 ${incomplete.joinToString("、")}）")
            lines.add("   可能不是当前账号的有效存档，请谨慎恢复")
        }

        val lv = stats?.num("lv", 1) ?: 1
        val exp = stats?.num("exp", 0) ?: 0
        val clsName = classNames[cls?.str("key", "")] ?: "未就职"
        lines.add("🧑 ${deviceManager.getCurrentPlayerName() ?: "—"}")
        lines.add("⭐ Lv.$lv · $clsName · 经验 $exp")

        val coin = bag?.num("coin", 0) ?: 0
        lines.add("💰 金币 $coin")
        lines.add("❤️ 生命 ${stats?.num("hp", 0) ?: 0} · 💙 蓝量 ${stats?.num("mp", 0) ?: 0} · 🍗 饱食 ${stats?.num("hunger", 0) ?: 0}")

        val gearCount = data.gearCount()
        val equipCount = data.equipCount()
        val pets = (data.obj("pets")?.getAsJsonArray("list")?.size() ?: 0)
        val petCases = (data.obj("pet_cases")?.getAsJsonArray("list")?.size() ?: 0)
        lines.add("⚔️ 装备 ${equipCount + gearCount}（身上 $equipCount / 仓库 $gearCount）")
        lines.add("🐾 宠物 $pets · 🧰 宠物栏 $petCases")

        // 技能与天赋
        val skillCount = cls?.getAsJsonArray("skills")?.size() ?: 0
        val talentCount = cls?.obj("talents")?.keySet()?.size ?: 0
        if (skillCount > 0 || talentCount > 0) {
            lines.add("📖 技能 $skillCount 个 · 天赋点已用 $talentCount")
        }

        // 装备明细
        val gearDetail = data.gearDetailText()
        if (gearDetail.isNotEmpty()) lines.add("🛡 仓库装备：$gearDetail")

        // 宠物明细
        val petDetail = data.petDetailText()
        if (petDetail.isNotEmpty()) lines.add("🐾 宠物明细：$petDetail")

        // 试炼塔
        val tower = data.obj("tower")?.num("bestFloor", 0) ?: 0
        if (tower > 0) lines.add("🗼 试炼塔最高层：$tower")

        val mats = ArrayList<String>()
        val keys = arrayOf(
            "wood" to "木材", "stone" to "石块", "copper" to "铜矿", "iron" to "铁矿",
            "spirit_crystal" to "灵晶", "gem_core" to "宝石核心", "pet_case" to "宠物栏",
            "boss_ticket" to "BOSS券"
        )
        for ((k, name) in keys) {
            val c = bag?.num(k, 0) ?: 0
            if (c > 0) mats.add("$name×$c")
        }
        if (mats.isNotEmpty()) {
            lines.add("📦 关键物资：" + mats.joinToString("　"))
        }

        binding.tvCloudInfo.text = lines.joinToString("\n")
    }

    private fun JsonObject.obj(key: String): JsonObject? = try {
        getAsJsonObject(key)
    } catch (e: Exception) {
        null
    }

    private fun JsonObject.num(key: String, def: Long): Long = try {
        get(key).asLong
    } catch (e: Exception) {
        def
    }

    private fun JsonObject.str(key: String, def: String): String = try {
        get(key).asString
    } catch (e: Exception) {
        def
    }

    private fun JsonObject.gearCount(): Int {
        val gear = obj("gear") ?: return 0
        var n = 0
        for (k in gear.keySet()) {
            try {
                n += gear.getAsJsonArray(k).size()
            } catch (e: Exception) {
            }
        }
        return n
    }

    private fun JsonObject.equipCount(): Int {
        val equip = obj("equip") ?: return 0
        var n = 0
        for (k in equip.keySet()) {
            val v = try {
                equip.get(k)
            } catch (e: Exception) {
                null
            }
            if (v != null && v.isJsonObject && v.asJsonObject.has("key")) n++
        }
        return n
    }

    private fun JsonObject.gearDetailText(): String {
        val gear = obj("gear") ?: return ""
        val parts = ArrayList<String>()
        for (k in gear.keySet()) {
            val arr = try {
                gear.getAsJsonArray(k)
            } catch (e: Exception) {
                null
            } ?: continue
            if (arr.size() == 0) continue
            val first = try {
                arr.get(0).asJsonObject
            } catch (e: Exception) {
                null
            }
            val q = first?.str("quality", "") ?: ""
            parts.add("$k×${arr.size()}" + if (q.isNotEmpty()) "（$q）" else "")
        }
        return parts.joinToString("、").take(120)
    }

    private fun JsonObject.petDetailText(): String {
        val list = obj("pets")?.getAsJsonArray("list") ?: return ""
        val parts = ArrayList<String>()
        for (i in 0 until list.size()) {
            val p = try {
                list.get(i).asJsonObject
            } catch (e: Exception) {
                null
            } ?: continue
            val name = p.str("name", "") ?: p.str("key", "宠物")
            parts.add("$name Lv.${p.num("lv", 1)}")
        }
        return parts.joinToString("、").take(120)
    }

    companion object {
        private val classNames = mapOf(
            "warrior" to "战士",
            "knight" to "骑士",
            "priest" to "牧师",
            "mage" to "法师"
        )
    }
}
