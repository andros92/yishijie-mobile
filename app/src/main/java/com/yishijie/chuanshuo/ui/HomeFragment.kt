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
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var deviceManager: DeviceManager
    private lateinit var interconnManager: InterconnManager
    private lateinit var syncManager: GameSyncManager
    private var updateChecked = false

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == CompanionService.ACTION_CONNECTION_STATUS) {
                val connected = intent.getBooleanExtra(CompanionService.EXTRA_CONNECTED, false)
                val detail = intent.getStringExtra(CompanionService.EXTRA_DETAIL) ?: ""
                updateConnectionUI(connected, detail)
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
        requireContext().registerReceiver(connectionReceiver, IntentFilter(CompanionService.ACTION_CONNECTION_STATUS))
        refreshAccountUI()
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
        binding.btnSave.setOnClickListener { startActivity(Intent(requireContext(), SaveManagerActivity::class.java)) }
        binding.btnExchange.setOnClickListener { startActivity(Intent(requireContext(), ExchangeBrowseActivity::class.java)) }
        binding.btnAnnouncements.setOnClickListener { startActivity(Intent(requireContext(), AnnouncementActivity::class.java)) }
        binding.btnRecharge.setOnClickListener { (activity as? MainActivity)?.switchTab(MainActivity.TAB_RECHARGE) }
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
        binding.tvAccount.text = if (id != null) "账号：$name（$id）" else "未注册"
        binding.tvConnDetail.text = "设备指纹：${deviceManager.getDeviceFingerprint() ?: "未知（需连接手环）"}"
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
}
