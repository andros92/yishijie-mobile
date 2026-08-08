package com.yishijie.chuanshuo.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.api.ExchangeBuyRequest
import com.yishijie.chuanshuo.api.ExchangeCancelRequest
import com.yishijie.chuanshuo.api.ExchangeListRequest
import com.yishijie.chuanshuo.api.ListingItem
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.ActivityExchangeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExchangeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExchangeBinding
    private lateinit var deviceManager: DeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExchangeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        deviceManager = DeviceManager.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnHistory.setOnClickListener { showHistory() }
        binding.btnList.setOnClickListener { doList() }
        loadListings()
    }

    private fun loadListings() {
        CoroutineScope(Dispatchers.Main).launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.exchangeListings(1, 20) }) {
                is ApiResult.Success -> renderListings(r.data?.data ?: emptyList())
                is ApiResult.Error -> status("加载失败: ${r.message}")
            }
        }
    }

    private fun renderListings(list: List<ListingItem>) {
        binding.llListings.removeAllViews()
        if (list.isEmpty()) {
            binding.llListings.addView(row("暂无在售挂单", Color.parseColor("#8890B0"), null))
            return
        }
        list.forEach { it ->
            val quality = if (it.quality.isNotEmpty()) " [${it.quality}]" else ""
            val gem = if (it.gem.isNotEmpty()) "◆" else ""
            val line = "${it.item_name}$quality$gem ×${it.qty} — ${it.price}金（卖家：${it.seller_name}）"
            binding.llListings.addView(row(line, Color.parseColor("#F0F0F8"), it))
        }
    }

    private fun row(text: String, color: Int, item: ListingItem?): TextView {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(color)
            gravity = Gravity.START
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        if (item != null) {
            tv.setOnClickListener {
                val me = deviceManager.getCurrentPlayerId()
                if (me == null) { status("请先登录账号"); return@setOnClickListener }
                AlertDialog.Builder(this)
                    .setTitle("购买")
                    .setMessage("确认花 ${item.price} 金币购买 ${item.item_name}×${item.qty}？（手续费由卖家承担）")
                    .setPositiveButton("购买") { _, _ ->
                        doBuy(item)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        return tv
    }

    private fun doBuy(item: ListingItem) {
        val me = deviceManager.getCurrentPlayerId() ?: run { status("请先登录"); return }
        val fp = deviceManager.getDeviceFingerprint() ?: run { status("请先连接手环"); return }
        val key = ApiClient.apiKey ?: run { status("缺少 apiKey，请重新登录"); return }
        CoroutineScope(Dispatchers.Main).launch {
            when (val r = ApiClient.safeApiCall {
                ApiClient.api.exchangeBuy(ExchangeBuyRequest(item.id, me, fp, key))
            }) {
                is ApiResult.Success -> {
                    status(if (r.data?.success == true) "购买成功！" else "购买失败: ${r.data?.error}")
                    loadListings()
                }
                is ApiResult.Error -> status("购买失败: ${r.message}")
            }
        }
    }

    private fun doList() {
        val me = deviceManager.getCurrentPlayerId() ?: run { status("请先登录"); return }
        val fp = deviceManager.getDeviceFingerprint() ?: run { status("请先连接手环"); return }
        val key = ApiClient.apiKey ?: run { status("缺少 apiKey，请重新登录"); return }
        val k = binding.etItemKey.text.toString().trim()
        val name = binding.etItemName.text.toString().trim()
        val qty = binding.etItemQty.text.toString().toIntOrNull() ?: 1
        val price = binding.etItemPrice.text.toString().toIntOrNull()
        if (k.isEmpty() || name.isEmpty() || price == null || price <= 0) {
            status("请填写 物品key / 名称 / 价格")
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            when (val r = ApiClient.safeApiCall {
                ApiClient.api.exchangeList(
                    ExchangeListRequest(
                        playerId = me, deviceFingerprint = fp, apiKey = key,
                        key = k, name = name, img = "", qty = qty, price = price,
                        quality = binding.etItemQuality.text.toString().trim().ifEmpty { null }
                    )
                )
            }) {
                is ApiResult.Success -> {
                    status(if (r.data?.success == true) "挂单成功 #${r.data.listingId}" else "挂单失败: ${r.data?.error}")
                    loadListings()
                }
                is ApiResult.Error -> status("挂单失败: ${r.message}")
            }
        }
    }

    private fun showHistory() {
        val me = deviceManager.getCurrentPlayerId() ?: run { status("请先登录"); return }
        CoroutineScope(Dispatchers.Main).launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.exchangeHistory(me) }) {
                is ApiResult.Success -> {
                    val sb = StringBuilder()
                    (r.data?.data ?: emptyList()).forEach { h ->
                        sb.append("${h.item_name}×${h.qty} ${h.price}金（手续费${h.fee}） ${if (h.seller_id == me) "卖出" else "买入"}\n")
                    }
                    AlertDialog.Builder(this@ExchangeActivity)
                        .setTitle("成交记录")
                        .setMessage(sb.toString().ifEmpty { "暂无记录" })
                        .setPositiveButton("确定", null)
                        .show()
                }
                is ApiResult.Error -> status("获取记录失败: ${r.message}")
            }
        }
    }

    private fun status(msg: String) {
        binding.tvStatus.text = msg
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
