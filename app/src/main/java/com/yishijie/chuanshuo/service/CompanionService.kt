package com.yishijie.chuanshuo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yishijie.chuanshuo.interconnect.InterconnManager

/**
 * 手环连接服务
 * 使用小米穿戴SDK维持与手环的连接
 */
class CompanionService : Service() {

    companion object {
        private const val TAG = "CompanionService"
        private const val CHANNEL_ID = "yishijie_companion"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_CONNECTION_STATUS = "com.yishijie.chuanshuo.CONNECTION_STATUS"
        const val ACTION_BRIDGE_STATE = "com.yishijie.chuanshuo.BRIDGE_STATE"
        const val EXTRA_CONNECTED = "connected"
        const val EXTRA_STATE = "state"
        const val EXTRA_DETAIL = "detail"

        fun start(context: Context) {
            val intent = Intent(context, CompanionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CompanionService::class.java))
        }
    }

    private lateinit var interconnManager: InterconnManager
    private var isConnected = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "服务创建")

        interconnManager = InterconnManager.getInstance(this)
        interconnManager.setStateListener(object : InterconnManager.ConnectionStateListener {
            override fun onStateChanged(state: InterconnManager.ConnectionState, detail: String) {
                val connected = state == InterconnManager.ConnectionState.READY
                if (isConnected != connected) {
                    isConnected = connected
                    broadcastConnectionStatus(connected, state.name, detail)
                }
                broadcastBridgeState(state.name, detail)
            }

            override fun onError(error: String) {
                Log.e(TAG, "连接错误: $error")
                broadcastConnectionStatus(false, "ERROR", error)
            }
        })

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("正在初始化手环连接..."))
        // 启动定期保活
        _keepAlive = true
        _keepAliveHandler.postDelayed(_keepAliveRunnable, 30000)
        // 持有 WakeLock 防止 CPU 休眠导致蓝牙断连
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Yishijie:Companion")
        wakeLock?.acquire(10 * 60 * 1000L)  // 10分钟超时，避免耗尽电池
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "服务启动")
        // 初始化小米穿戴SDK
        interconnManager.initialize()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "服务销毁")
        _keepAlive = false
        wakeLock?.let { if (it.isHeld) it.release() }
        interconnManager.setStateListener(null)
    }

    // 定期连接保活：每30秒检查一次，断开时自动重连
    private var _keepAlive = false
    private val _keepAliveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val _keepAliveRunnable = object : Runnable {
        override fun run() {
            if (!_keepAlive) return
            if (interconnManager.getState() == InterconnManager.ConnectionState.DISCONNECTED ||
                interconnManager.getState() == InterconnManager.ConnectionState.ERROR) {
                Log.i(TAG, "保活检测到断连，触发重连")
                interconnManager.refreshConnection()
            }
            _keepAliveHandler.postDelayed(this, 30000)
        }
    }

    /**
     * 广播连接状态
     */
    private fun broadcastConnectionStatus(connected: Boolean, state: String, detail: String) {
        val intent = Intent(ACTION_CONNECTION_STATUS).apply {
            putExtra(EXTRA_CONNECTED, connected)
            putExtra(EXTRA_STATE, state)
            putExtra(EXTRA_DETAIL, detail)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        updateNotification(if (connected) "手环已连接" else "等待手环连接...")
    }

    /**
     * 广播桥接状态详情
     */
    private fun broadcastBridgeState(state: String, detail: String) {
        val intent = Intent(ACTION_BRIDGE_STATE).apply {
            putExtra(EXTRA_STATE, state)
            putExtra(EXTRA_DETAIL, detail)
            putExtra("appInstalled", interconnManager.isAppInstalled())
            putExtra("devicePermission", interconnManager.isDevicePermissionGranted())
            putExtra("notifyPermission", interconnManager.isNotifyPermissionGranted())
            putExtra("watchConnected", interconnManager.isWatchConnected())
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "异世界传说",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "保持与手环的连接"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("异世界传说")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
