package com.yishijie.chuanshuo.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yishijie.chuanshuo.BuildConfig
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.FragmentHomeBinding
import com.yishijie.chuanshuo.interconnect.GameSyncManager
import com.yishijie.chuanshuo.interconnect.InterconnManager
import com.yishijie.chuanshuo.service.CompanionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var deviceManager: DeviceManager
    private lateinit var interconnManager: InterconnManager
    private lateinit var syncManager: GameSyncManager
    private var updateChecked = false
    private var dataLoading = false

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == CompanionService.ACTION_CONNECTION_STATUS) {
                val connected = intent.getBooleanExtra(CompanionService.EXTRA_CONNECTED, false)
                val detail = intent.getStringExtra(CompanionService.EXTRA_DETAIL) ?: ""
                updateConnectionUI(connected, detail)
                refreshAccountUI()
            } else if (intent.action == CompanionService.ACTION_DEVICE_FINGERPRINT) {
                refreshAccountUI()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        deviceManager = DeviceManager.getInstance(requireContext())
        interconnManager = InterconnManager.getInstance(requireContext())
        syncManager = GameSyncManager.getInstance(requireContext())
        setupUI()
        refreshAccountUI()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(CompanionService.ACTION_CONNECTION_STATUS)
            addAction(CompanionService.ACTION_DEVICE_FINGERPRINT)
        }
        requireContext().registerReceiver(connectionReceiver, filter)
        refreshAccountUI()
        loadDataSummary()
        if (!updateChecked) {
            updateChecked = true
            checkUpdate()
        }
    }

    override fun onPause() {
        super.onPause()
        try { requireContext().unregisterReceiver(connectionReceiver) } catch (e: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupUI() {
        binding.btnRefreshConn.setOnClickListener {
            interconnManager.initialize()
            interconnManager.refreshConnection()
            CompanionService.start(requireContext())
            binding.tvToast.text = "正在刷新连接..."
        }
        binding.btnDisconnect.setOnClickListener {
            CompanionService.stop(requireContext())
            updateConnectionUI(false, "已断开连接")
            binding.tvToast.text = "已断开连接"
        }
        binding.btnBridge.setOnClickListener {
            if (!ClickGuard.allow()) return@setOnClickListener
            startActivity(Intent(requireContext(), BridgeActivity::class.java))
        }
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            if (name.isEmpty()) { binding.tvToast.text = "请输入玩家名称"; return@setOnClickListener }
            binding.tvToast.text = "注册中..."
            syncManager.registerAccount(name, object : GameSyncManager.AccountCallback {
                override fun onRegistered(playerId: String, playerName: String, isNew: Boolean, error: String?) {
                    if (_binding == null) return
                    binding.tvToast.text = if (error != null) "注册失败: $error" else (if (isNew) "注册成功" else "已注册") + "：$playerName ($playerId)"
                    refreshAccountUI()
                }
                override fun onLoggedIn(playerId: String?, playerName: String?, error: String?) {}
            })
        }
        binding.btnLogin.setOnClickListener {
            binding.tvToast.text = "登录中..."
            syncManager.loginAccount(object : GameSyncManager.AccountCallback {
                override fun onRegistered(playerId: String, playerName: String, isNew: Boolean, error: String?) {}
                override fun onLoggedIn(playerId: String?, playerName: String?, error: String?) {
                    if (_binding == null) return
                    binding.tvToast.text = if (error != null) "登录失败: $error" else "登录成功：$playerName ($playerId)"
                    refreshAccountUI()
                }
            })
        }
        binding.btnSave.setOnClickListener {
            if (!ClickGuard.allow()) return@setOnClickListener
            startActivity(Intent(requireContext(), SaveManagerActivity::class.java))
        }
        binding.btnExchange.setOnClickListener {
            if (!ClickGuard.allow()) return@setOnClickListener
            startActivity(Intent(requireContext(), ExchangeBrowseActivity::class.java))
        }
        binding.btnAnnouncements.setOnClickListener {
            if (!ClickGuard.allow()) return@setOnClickListener
            startActivity(Intent(requireContext(), AnnouncementActivity::class.java))
        }
        binding.btnRecharge.setOnClickListener {
            if (!ClickGuard.allow()) return@setOnClickListener
            (activity as? MainActivity)?.switchTab(MainActivity.TAB_RECHARGE)
        }
        binding.btnRefreshData.setOnClickListener {
            loadDataSummary()
            binding.tvToast.text = "正在刷新数据…"
        }
    }

    private fun updateConnectionUI(connected: Boolean, detail: String) {
        binding.tvConnStatus.text = if (connected) "已连接" else "未连接"
        binding.tvConnStatus.setTextColor(
            if (connected) 0xFF059669.toInt() else 0xFFDC2626.toInt()
        )
        binding.tvConnDetail.text = detail
    }

    private fun refreshAccountUI() {
        val id = deviceManager.getCurrentPlayerId()
        val name = deviceManager.getCurrentPlayerName()
        val loggedIn = id != null
        binding.tvAccount.text = if (loggedIn) "账号：$name（$id）" else "未注册"
        // 已有账号后收起注册/登录，不再常驻碍眼
        binding.etName.visibility = if (loggedIn) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnRegister.visibility = if (loggedIn) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnLogin.visibility = if (loggedIn) android.view.View.GONE else android.view.View.VISIBLE
        binding.tvConnDetail.text = "设备指纹：${deviceManager.getDeviceFingerprint() ?: "未知（需连接手环）"}"
    }

    /**
     * 首页数据卡：优先拉手环当前存档，手环不可用时回退读取云存档
     */
    private fun loadDataSummary() {
        if (dataLoading) return
        dataLoading = true
        binding.tvDataSummary.text = "加载中…"
        syncManager.downloadSaveFromBand(object : GameSyncManager.SaveCallback {
            override fun onSaveUploaded(success: Boolean, message: String) {}
            override fun onSaveDownloaded(data: JSONObject?) {
                dataLoading = false
                if (data != null) {
                    renderDataSummary(data)
                } else {
                    loadCloudSummaryFallback()
                }
            }
            override fun onError(error: String) {
                dataLoading = false
                loadCloudSummaryFallback()
            }
        })
    }

    private fun loadCloudSummaryFallback() {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) { syncManager.downloadSaveFromServer() }
            if (_binding == null) return@launch
            renderDataSummary(data?.let { JSONObject(it.toString()) })
        }
    }

    private fun renderDataSummary(data: JSONObject?) {
        if (_binding == null) return
        if (data == null) {
            binding.tvDataSummary.text = "暂无数据\n请连接手环，或在“存档管理”上传云存档。"
            return
        }
        val lines = ArrayList<String>()
        val stats = data.optJSONObject("stats")
        val bag = data.optJSONObject("bag")
        val cls = data.optJSONObject("class")
        val lv = stats?.optLong("lv", 1L) ?: 1L
        val exp = stats?.optLong("exp", 0L) ?: 0L
        val clsName = classNames[cls?.optString("key", "")] ?: "未就职"
        val name = deviceManager.getCurrentPlayerName() ?: "冒险者"
        lines.add("🧑 $name · Lv.$lv · $clsName")
        lines.add("⭐ 经验 $exp")
        lines.add("💰 金币 ${bag?.optLong("coin", 0L) ?: 0L}")
        lines.add("❤️ 生命 ${stats?.optLong("hp", 0L) ?: 0L}")
        lines.add("💙 蓝量 ${stats?.optLong("mp", 0L) ?: 0L}")
        lines.add("🍗 饱食 ${stats?.optLong("hunger", 0L) ?: 0L}/100")

        var gear = 0
        data.optJSONObject("gear")?.let { g ->
            val it = g.keys()
            while (it.hasNext()) {
                val k = it.next()
                gear += (g.optJSONArray(k)?.length() ?: 0)
            }
        }
        var equip = 0
        data.optJSONObject("equip")?.let { e ->
            val it = e.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = e.opt(k)
                if (v is JSONObject && v.has("key")) equip++
            }
        }
        val pets = data.optJSONObject("pets")?.optJSONArray("list")?.length() ?: 0
        val petCases = data.optJSONObject("pet_cases")?.optJSONArray("list")?.length() ?: 0
        val bagSlots = countBagSlots(bag, gear + equip, pets, petCases)
        lines.add("⚔️ 装备 ${gear + equip}")
        lines.add("🐾 宠物 $pets · 🧰 宠物栏 $petCases")
        lines.add("🎒 背包 $bagSlots/60")

        val mats = ArrayList<String>()
        val keys = arrayOf(
            "wood" to "🪵木材", "stone" to "🪨石块", "copper" to "🔶铜矿", "iron" to "⛓️铁矿",
            "gold" to "🟡金矿", "spirit_crystal" to "💎灵晶", "gem_core" to "💠宝石核心",
            "pet_case" to "🧰宠物栏", "boss_ticket" to "🎫BOSS券",
            "healing_potion" to "🧪治疗药水", "mana_potion" to "🔵蓝量药水"
        )
        for ((k, name) in keys) {
            val c = bag?.optLong(k, 0L) ?: 0L
            if (c > 0) mats.add("$name×$c")
        }
        if (mats.isNotEmpty()) {
            lines.add("📦 物资：" + mats.joinToString("　"))
        }
        binding.tvDataSummary.text = lines.joinToString("\n")
    }

    private fun countBagSlots(bag: JSONObject?, gearCount: Int, pets: Int, petCases: Int): Int {
        var types = 0
        bag?.keys()?.let { it ->
            while (it.hasNext()) {
                val k = it.next()
                if (k == "coin") continue
                if ((bag.optLong(k, 0L) ?: 0L) > 0) types++
            }
        }
        return types + gearCount + petCases
    }

    private fun checkUpdate() {
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.version() }) {
                is ApiResult.Success -> {
                    val v = r.data
                    if (v != null && v.versionCode > BuildConfig.VERSION_CODE) {
                        val builder = AlertDialog.Builder(requireContext())
                            .setTitle("发现新版本 ${v.versionName}")
                            .setMessage("更新内容：\n${v.updateNotes.ifEmpty { "修复与优化" }}")
                            .setNegativeButton("以后再说", null)
                        if (!v.downloadUrl.isNullOrEmpty()) {
                            builder.setPositiveButton("立即更新") { _, _ ->
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.downloadUrl)))
                                } catch (e: Exception) {
                                    binding.tvToast.text = "无法打开下载链接"
                                }
                            }
                        }
                        builder.show()
                    }
                }
                is ApiResult.Error -> {}
            }
        }
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
