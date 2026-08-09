package com.yishijie.chuanshuo.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.ActivitySaveManagerBinding
import com.yishijie.chuanshuo.interconnect.GameSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SaveManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySaveManagerBinding
    private lateinit var syncManager: GameSyncManager
    private lateinit var deviceManager: DeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySaveManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
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
            AlertDialog.Builder(this)
                .setTitle("恢复云存档")
                .setMessage("将从云存档恢复到手环，覆盖手环当前存档（每日限 1 次）。确定继续？")
                .setPositiveButton("恢复") { _, _ -> doRestore() }
                .setNegativeButton("取消", null)
                .show()
        }

        binding.btnRefreshCloud.setOnClickListener {
            status("已刷新")
            loadCloudSummary()
        }

        loadCloudSummary()
    }

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

        val lv = stats?.num("lv", 1) ?: 1
        val clsName = classNames[cls?.str("key", "")] ?: "未就职"
        lines.add("玩家：${deviceManager.getCurrentPlayerName() ?: "—"} · 等级 Lv.$lv · $clsName")

        val coin = bag?.num("coin", 0) ?: 0
        lines.add("金币：$coin")
        lines.add("生命：${stats?.num("hp", 0) ?: 0} · 蓝量：${stats?.num("mp", 0) ?: 0} · 饱食：${stats?.num("hunger", 0) ?: 0}")

        val gearCount = data.gearCount()
        val equipCount = data.equipCount()
        val pets = (data.obj("pets")?.getAsJsonArray("list")?.size() ?: 0)
        val petCases = (data.obj("pet_cases")?.getAsJsonArray("list")?.size() ?: 0)
        lines.add("装备：${equipCount + gearCount} · 宠物：$pets · 宠物栏：$petCases")

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
            lines.add("关键物资：" + mats.joinToString("　"))
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

    companion object {
        private val classNames = mapOf(
            "warrior" to "战士",
            "knight" to "骑士",
            "priest" to "牧师",
            "mage" to "法师"
        )
    }
}
