package com.yishijie.chuanshuo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.ActivityRechargeBinding
import com.yishijie.chuanshuo.interconnect.GameSyncManager
import kotlinx.coroutines.launch
import org.json.JSONObject

class RechargeActivity : AppCompatActivity() {

    private data class Pkg(val price: Int, val coins: Int)

    private lateinit var binding: ActivityRechargeBinding
    private lateinit var deviceManager: DeviceManager
    private val packages = listOf(
        Pkg(1, 10000),
        Pkg(6, 60000),
        Pkg(30, 300000),
        Pkg(98, 980000)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRechargeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        deviceManager = DeviceManager.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCheckOrders.setOnClickListener { checkOrders() }
        binding.btnSyncSave.setOnClickListener { syncSaveToBand() }
        renderPackages()
    }

    private fun renderPackages() {
        binding.llPackages.removeAllViews()
        packages.forEachIndexed { index, pkg ->
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
                radius = dp(14).toFloat()
                elevation = dp(1).toFloat()
                setCardBackgroundColor(Color.WHITE)
                strokeColor = Color.parseColor("#E2E6EF")
                strokeWidth = 1
                isClickable = true
                isFocusable = true
                setOnClickListener { handlePurchaseClick(pkg) }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(16))
            }
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            info.addView(TextView(this).apply {
                text = "${pkg.coins / 10000} 万金币"
                textSize = 17f
                setTextColor(Color.parseColor("#1A1D26"))
                typeface = Typeface.DEFAULT_BOLD
            })
            info.addView(TextView(this).apply {
                text = "充 ¥${pkg.price} 到账 ${pkg.coins} 金币"
                textSize = 12f
                setTextColor(Color.parseColor("#6B7280"))
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(info)
            row.addView(TextView(this).apply {
                text = "¥${pkg.price}"
                textSize = 22f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(if (index == 0) Color.parseColor("#2563EB") else Color.parseColor("#111827"))
                }
            })
            card.addView(row)
            binding.llPackages.addView(card)
        }
    }

    private fun handlePurchaseClick(pkg: Pkg) {
        val playerId = deviceManager.getCurrentPlayerId()
        if (playerId == null) {
            status("请先连接手环并登录账号")
            return
        }
        val message = "${pkg.coins / 10000} 万金币（¥${pkg.price}）\n\n您的玩家 ID：$playerId\n\n" +
                "请在爱发电支付页面的「备注 / 留言」中粘贴这个 ID，系统会自动发放金币。"
        val dialog = AlertDialog.Builder(this)
            .setTitle("购买须知")
            .setMessage(message)
            .setPositiveButton("我已知晓（5s）") { _, _ -> }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()

        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button.isEnabled = false
        val handler = Handler(Looper.getMainLooper())
        var countdown = 5
        val runnable = object : Runnable {
            override fun run() {
                countdown--
                if (countdown > 0) {
                    button.text = "我已知晓（${countdown}s）"
                    handler.postDelayed(this, 1000)
                } else {
                    button.text = "去支付 ¥${pkg.price}"
                    button.isEnabled = true
                    button.setOnClickListener {
                        dialog.dismiss()
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("playerId", playerId))
                        Toast.makeText(this@RechargeActivity, "玩家 ID 已复制", Toast.LENGTH_SHORT).show()
                        openAfdian()
                    }
                }
            }
        }
        handler.postDelayed(runnable, 1000)
    }

    private fun openAfdian() {
        lifecycleScope.launch {
            status("正在打开爱发电...")
            when (val r = ApiClient.safeApiCall { ApiClient.api.afdianUrl() }) {
                is ApiResult.Success -> {
                    val url = r.data?.afdianUrl
                    if (url.isNullOrEmpty()) {
                        status("未配置爱发电链接")
                    } else {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            status("支付完成后回来点「查询到账」")
                        } catch (e: Exception) {
                            status("无法打开爱发电页面")
                        }
                    }
                }
                is ApiResult.Error -> status("获取支付链接失败: ${r.message}")
            }
        }
    }

    private fun checkOrders() {
        val me = deviceManager.getCurrentPlayerId() ?: run { status("请先登录账号"); return }
        val fp = deviceManager.getDeviceFingerprint() ?: run { status("请先连接手环"); return }
        val key = ApiClient.apiKey ?: run { status("缺少 apiKey，请重新登录"); return }
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.paymentOrders(me, fp, key) }) {
                is ApiResult.Success -> {
                    val paid = (r.data?.data ?: emptyList()).filter { it.status == "paid" }
                    if (paid.isEmpty()) {
                        status("暂无到账记录，支付后请稍等片刻再查询")
                    } else {
                        val last = paid.first()
                        status("已到账：¥${last.amount} → ${last.qty} 金币\n订单 ${last.order_id}")
                    }
                }
                is ApiResult.Error -> status("查询失败: ${r.message}")
            }
        }
    }

    private fun syncSaveToBand() {
        if (deviceManager.getCurrentPlayerId() == null) { status("请先登录账号"); return }
        status("正在从服务器同步存档...")
        lifecycleScope.launch {
            val save = GameSyncManager.getInstance(this@RechargeActivity).downloadSaveFromServer()
            if (save == null) {
                status("服务器没有存档，请先在手环上传")
                return@launch
            }
            GameSyncManager.getInstance(this@RechargeActivity).uploadSaveToBand(
                JSONObject(save.toString()),
                object : GameSyncManager.SaveCallback {
                    override fun onSaveUploaded(success: Boolean, message: String) {
                        runOnUiThread { status(if (success) "已同步到手环，打开手环即可看到金币" else "同步失败：$message") }
                    }
                    override fun onSaveDownloaded(data: JSONObject?) {}
                    override fun onError(error: String) {
                        runOnUiThread { status("同步失败：$error") }
                    }
                }
            )
        }
    }

    private fun status(msg: String) {
        binding.tvStatus.text = msg
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
