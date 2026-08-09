package com.yishijie.chuanshuo.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.ActivityBridgeBinding
import com.yishijie.chuanshuo.interconnect.InterconnManager
import com.yishijie.chuanshuo.service.CompanionService

class BridgeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBridgeBinding
    private lateinit var deviceManager: DeviceManager
    private lateinit var interconnManager: InterconnManager

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                CompanionService.ACTION_CONNECTION_STATUS -> {
                    val connected = intent.getBooleanExtra(CompanionService.EXTRA_CONNECTED, false)
                    val state = intent.getStringExtra(CompanionService.EXTRA_STATE) ?: ""
                    val detail = intent.getStringExtra(CompanionService.EXTRA_DETAIL) ?: ""
                    updateConnectionUI(connected, state, detail)
                    updateProfileInfo()
                }
                CompanionService.ACTION_BRIDGE_STATE -> {
                    val state = intent.getStringExtra(CompanionService.EXTRA_STATE) ?: ""
                    val detail = intent.getStringExtra(CompanionService.EXTRA_DETAIL) ?: ""
                    val appInstalled = intent.getBooleanExtra("appInstalled", false)
                    val devicePermission = intent.getBooleanExtra("devicePermission", false)
                    val notifyPermission = intent.getBooleanExtra("notifyPermission", false)
                    val watchConnected = intent.getBooleanExtra("watchConnected", false)
                    updateBridgeStateUI(state, detail, appInstalled, devicePermission, notifyPermission, watchConnected)
                    updateProfileInfo()
                }
                CompanionService.ACTION_DEVICE_FINGERPRINT -> {
                    updateProfileInfo()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBridgeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceManager = DeviceManager.getInstance(this)
        interconnManager = InterconnManager.getInstance(this)

        setupUI()
        updateProfileInfo()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(CompanionService.ACTION_CONNECTION_STATUS)
            addAction(CompanionService.ACTION_BRIDGE_STATE)
            addAction(CompanionService.ACTION_DEVICE_FINGERPRINT)
        }
        registerReceiver(connectionReceiver, filter)
        // 刷新状态
        interconnManager.refreshConnection()
        updateProfileInfo()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(connectionReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    // 返回按钮（从 XML onClick 调用）
    fun onBackClicked(view: android.view.View) {
        finish()
    }

    private fun setupUI() {
        // 刷新桥接状态
        binding.btnRefreshBridge.setOnClickListener {
            interconnManager.refreshConnection()
            Toast.makeText(this, "正在刷新...", Toast.LENGTH_SHORT).show()
        }

        // 申请权限
        binding.btnRequestPermission.setOnClickListener {
            interconnManager.requestPermissions()
        }
    }

    /**
     * 更新账号信息
     */
    private fun updateProfileInfo() {
        val playerId = deviceManager.getCurrentPlayerId()
        val playerName = deviceManager.getCurrentPlayerName()
        val deviceFingerprint = deviceManager.getDeviceFingerprint()

        if (playerId != null) {
            binding.tvProfileStatus.text = "已绑定: $playerName"
            binding.tvProfileStatus.setTextColor(0xFF3CFF8D.toInt())
        } else {
            binding.tvProfileStatus.text = "未绑定"
            binding.tvProfileStatus.setTextColor(0xFFFF5E5E.toInt())
        }

        binding.tvDeviceFingerprint.text = "设备指纹: ${deviceFingerprint ?: "未知"}"
    }

    /**
     * 更新连接状态UI
     */
    private fun updateConnectionUI(connected: Boolean, state: String, detail: String) {
        runOnUiThread {
            if (connected) {
                binding.tvBridgeConnectedStatus.text = "已连接手表"
                binding.tvBridgeConnectedStatus.setTextColor(0xFF3CFF8D.toInt())
            } else {
                binding.tvBridgeConnectedStatus.text = "未连接手表"
                binding.tvBridgeConnectedStatus.setTextColor(0xFFFF5E5E.toInt())
            }

            binding.tvBridgePhase.text = "阶段: $state"
            binding.tvBridgeDetail.text = detail
            binding.tvStatus.text = detail
        }
    }

    /**
     * 更新桥接状态UI
     */
    private fun updateBridgeStateUI(
        state: String,
        detail: String,
        appInstalled: Boolean,
        devicePermission: Boolean,
        notifyPermission: Boolean,
        watchConnected: Boolean
    ) {
        runOnUiThread {
            // 应用安装状态
            if (appInstalled) {
                binding.tvAppInstalled.text = "已安装"
                binding.tvAppInstalled.setTextColor(0xFF3CFF8D.toInt())
            } else {
                binding.tvAppInstalled.text = "未安装"
                binding.tvAppInstalled.setTextColor(0xFFFF5E5E.toInt())
            }

            // 设备权限
            if (devicePermission) {
                binding.tvDevicePermission.text = "已授权"
                binding.tvDevicePermission.setTextColor(0xFF3CFF8D.toInt())
            } else {
                binding.tvDevicePermission.text = "未授权"
                binding.tvDevicePermission.setTextColor(0xFFFFB347.toInt())
            }

            // 通知权限
            if (notifyPermission) {
                binding.tvNotifyPermission.text = "已授权"
                binding.tvNotifyPermission.setTextColor(0xFF3CFF8D.toInt())
            } else {
                binding.tvNotifyPermission.text = "未授权"
                binding.tvNotifyPermission.setTextColor(0xFFFFB347.toInt())
            }

            // 手表连接
            if (watchConnected) {
                binding.tvWatchConnected.text = "已连接"
                binding.tvWatchConnected.setTextColor(0xFF3CFF8D.toInt())
            } else {
                binding.tvWatchConnected.text = "未连接"
                binding.tvWatchConnected.setTextColor(0xFFFF5E5E.toInt())
            }

            // 显示/隐藏权限按钮
            binding.btnRequestPermission.visibility =
                if (!devicePermission || !notifyPermission) android.view.View.VISIBLE else android.view.View.GONE
        }
    }
}
