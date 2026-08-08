package com.yishijie.chuanshuo.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.ActivityMainBinding
import com.yishijie.chuanshuo.interconnect.GameSyncManager
import com.yishijie.chuanshuo.interconnect.InterconnManager
import com.yishijie.chuanshuo.service.CompanionService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var deviceManager: DeviceManager
    private lateinit var interconnManager: InterconnManager
    private lateinit var syncManager: GameSyncManager

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == CompanionService.ACTION_CONNECTION_STATUS) {
                val connected = intent.getBooleanExtra(CompanionService.EXTRA_CONNECTED, false)
                val detail = intent.getStringExtra(CompanionService.EXTRA_DETAIL) ?: ""
                runOnUiThread { updateConnectionUI(connected, detail) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceManager = DeviceManager.getInstance(this)
        interconnManager = InterconnManager.getInstance(this)
        syncManager = GameSyncManager.getInstance(this)

        CompanionService.start(this)
        interconnManager.initialize()

        setupUI()
        refreshAccountUI()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(connectionReceiver, IntentFilter(CompanionService.ACTION_CONNECTION_STATUS))
        refreshAccountUI()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(connectionReceiver) } catch (e: Exception) {}
    }

    private fun setupUI() {
        binding.btnRefreshConn.setOnClickListener {
            interconnManager.refreshConnection()
            showToast("正在刷新连接...")
        }
        binding.btnBridge.setOnClickListener {
            startActivity(Intent(this, BridgeActivity::class.java))
        }
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            if (name.isEmpty()) { showToast("请输入玩家名称"); return@setOnClickListener }
            binding.tvToast.text = "注册中..."
            syncManager.registerAccount(name, object : GameSyncManager.AccountCallback {
                override fun onRegistered(playerId: String, playerName: String, isNew: Boolean, error: String?) {
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
                    binding.tvToast.text = if (error != null) "登录失败: $error" else "登录成功：$playerName ($playerId)"
                    refreshAccountUI()
                }
            })
        }
        binding.btnSave.setOnClickListener { startActivity(Intent(this, SaveManagerActivity::class.java)) }
        binding.btnExchange.setOnClickListener { startActivity(Intent(this, ExchangeActivity::class.java)) }
        binding.btnRecharge.setOnClickListener { startActivity(Intent(this, RechargeActivity::class.java)) }
        binding.btnAnnouncements.setOnClickListener { startActivity(Intent(this, AnnouncementActivity::class.java)) }
    }

    private fun updateConnectionUI(connected: Boolean, detail: String) {
        binding.tvConnStatus.text = if (connected) "已连接手环" else "未连接手环"
        binding.tvConnStatus.setTextColor(
            if (connected) 0xFF3CFF8D.toInt() else 0xFFFF5E5E.toInt()
        )
        binding.tvConnDetail.text = detail
    }

    private fun refreshAccountUI() {
        val id = deviceManager.getCurrentPlayerId()
        val name = deviceManager.getCurrentPlayerName()
        binding.tvAccount.text = if (id != null) "账号：$name（$id）" else "未注册"
        binding.tvConnDetail.text = "设备指纹：${deviceManager.getDeviceFingerprint() ?: "未知（需连接手环）"}"
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
