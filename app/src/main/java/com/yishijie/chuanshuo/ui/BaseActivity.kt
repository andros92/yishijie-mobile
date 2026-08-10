package com.yishijie.chuanshuo.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yishijie.chuanshuo.interconnect.InterconnManager
import com.yishijie.chuanshuo.service.CompanionService

/**
 * 所有页面基类：顶部左上角常驻显示手环连接状态（任意页面可见）
 */
abstract class BaseActivity : AppCompatActivity() {

    private var statusText: TextView? = null
    private var refreshPending = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshConnStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        try {
            registerReceiver(receiver, IntentFilter().apply {
                addAction(CompanionService.ACTION_CONNECTION_STATUS)
                addAction(CompanionService.ACTION_BRIDGE_STATE)
                addAction(CompanionService.ACTION_DEVICE_FINGERPRINT)
            })
        } catch (e: Exception) {
            // ignore
        }
        refreshConnStatus()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * 子类用这个方法代替 setContentView：内容顶部会多一条连接状态条
     */
    protected fun setContentViewWithStatus(content: View) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1020"))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(3), dp(10), dp(3))
            setBackgroundColor(Color.parseColor("#101830"))
        }
        statusText = TextView(this).apply {
            text = "○ 未连接手环"
            textSize = 11f
            setTextColor(Color.parseColor("#FF6B7A"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        bar.addView(statusText)
        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(
            content,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)
    }

    private fun refreshConnStatus() {
        runOnUiThread {
            val tv = statusText ?: return@runOnUiThread
            val connected = try {
                InterconnManager.getInstance(this@BaseActivity).getState() == InterconnManager.ConnectionState.READY
            } catch (e: Exception) {
                false
            }
            tv.text = if (connected) "● 已连接手环" else "○ 未连接手环"
            tv.setTextColor(if (connected) Color.parseColor("#3CFF8D") else Color.parseColor("#FF6B7A"))
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
