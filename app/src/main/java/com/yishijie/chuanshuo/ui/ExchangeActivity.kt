package com.yishijie.chuanshuo.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yishijie.chuanshuo.R
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.api.ExchangeBuyRequest
import com.yishijie.chuanshuo.api.ExchangeCancelRequest
import com.yishijie.chuanshuo.api.ExchangeListRequest
import com.yishijie.chuanshuo.api.ListingItem
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.ActivityExchangeBinding
import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.launch

class ExchangeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExchangeBinding
    private lateinit var deviceManager: DeviceManager
    private var mineMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExchangeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        deviceManager = DeviceManager.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnHistory.setOnClickListener { showHistory() }
        binding.btnList.setOnClickListener { doList() }
        binding.tabHall.setOnClickListener { switchTab(false) }
        binding.tabMine.setOnClickListener { switchTab(true) }
        binding.btnSearch.setOnClickListener { loadListings() }
        binding.etSearch.setOnEditorActionListener { _, _, _ -> loadListings(); true }
        applyTabStyle()
        loadListings()
    }

    private fun switchTab(mine: Boolean) {
        if (mineMode == mine) return
        mineMode = mine
        applyTabStyle()
        loadListings()
    }

    private fun applyTabStyle() {
        binding.tabHall.setBackgroundResource(if (!mineMode) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        binding.tabMine.setBackgroundResource(if (mineMode) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        binding.tabHall.setTextColor(Color.parseColor(if (!mineMode) "#111827" else "#9AA3C0"))
        binding.tabMine.setTextColor(Color.parseColor(if (mineMode) "#111827" else "#9AA3C0"))
    }

    private fun loadListings() {
        lifecycleScope.launch {
            val kw = binding.etSearch.text.toString().trim()
            val me = deviceManager.getCurrentPlayerId()
            val fp = deviceManager.getDeviceFingerprint()
            val key = ApiClient.apiKey
            val call = if (mineMode && me != null && fp != null && key != null) {
                ApiClient.api.exchangeListings(1, 20, null, kw.ifEmpty { null }, true, me, fp, key)
            } else {
                ApiClient.api.exchangeListings(1, 20, null, kw.ifEmpty { null })
            }
            when (val r = ApiClient.safeApiCall { call }) {
                is ApiResult.Success -> renderListings(r.data?.data ?: emptyList())
                is ApiResult.Error -> status("加载失败: ${r.message}")
            }
        }
    }

    private fun renderListings(list: List<ListingItem>) {
        binding.llListings.removeAllViews()
        if (list.isEmpty()) {
            binding.llListings.addView(emptyRow(if (mineMode) "暂无挂单" else "暂无在售挂单"))
            return
        }
        list.forEach { it ->
            val quality = if (it.quality.isNotEmpty()) " [${it.quality}]" else ""
            val gem = if (it.gem.isNotEmpty()) "◆" else ""
            binding.llListings.addView(row(it, "${it.item_name}$quality$gem ×${it.qty}"))
        }
    }

    private fun emptyRow(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#5D6B8C"))
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(24))
        }
    }

    private fun row(item: ListingItem, title: String): com.google.android.material.card.MaterialCardView {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            radius = dp(14).toFloat()
            elevation = dp(1).toFloat()
            setCardBackgroundColor(Color.parseColor("#1A2242"))
            strokeColor = Color.parseColor("#2EFFFFFF")
            strokeWidth = 1
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(14))
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.parseColor("#F4F6FF"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        info.addView(TextView(this).apply {
            text = if (item.item_uid.isNotEmpty()) "UID ${item.item_uid} · ${item.price} 金币" else "${item.price} 金币"
            textSize = 12f
            setTextColor(Color.parseColor("#9AA3C0"))
            setPadding(0, dp(3), 0, 0)
        })
        info.setOnClickListener { showItemDetail(item) }
        row.addView(info)
        row.addView(actionButton(item))
        card.addView(row)
        return card
    }

    private fun actionButton(item: ListingItem): TextView {
        return if (mineMode) {
            TextView(this).apply {
                text = if (item.status == "on") "撤单" else "已处理"
                textSize = 13f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor(if (item.status == "on") "#E05260" else "#3A4668"))
                }
                if (item.status == "on") {
                    setOnClickListener {
                        AlertDialog.Builder(this@ExchangeActivity)
                            .setTitle("撤单")
                            .setMessage("确认撤下 ${item.item_name}×${item.qty} 的挂单？物品会退回背包")
                            .setPositiveButton("撤单") { _, _ -> doCancel(item) }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            }
        } else {
            TextView(this).apply {
                text = "购买"
                textSize = 13f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#7B8CFF"))
                }
                setOnClickListener {
                    val me = deviceManager.getCurrentPlayerId()
                    if (me == null) { status("请先登录账号"); return@setOnClickListener }
                    AlertDialog.Builder(this@ExchangeActivity)
                        .setTitle("确认购买")
                        .setMessage("花费 ${item.price} 金币购买 ${item.item_name}×${item.qty}？\n（手续费由卖家承担）")
                        .setPositiveButton("购买") { _, _ -> doBuy(item) }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        }
    }

    private fun showItemDetail(item: ListingItem) {
        val sb = StringBuilder()
        sb.append("物品：${item.item_name}\n")
        if (item.item_uid.isNotEmpty()) sb.append("ID：${item.item_uid}\n")
        if (item.quality.isNotEmpty()) sb.append("品质：${item.quality}\n")
        if (item.gem.isNotEmpty()) sb.append("镶嵌：${item.gem}\n")
        if (item.dur > 0) sb.append("耐久：${item.dur}/${item.max_dur}\n")
        sb.append("数量：${item.qty}\n")
        sb.append("价格：${item.price} 金币\n")
        sb.append("卖家：${item.seller_name}")
        AlertDialog.Builder(this)
            .setTitle("物品详情")
            .setMessage(sb.toString())
            .setPositiveButton("购买") { _, _ -> doBuy(item) }
            .setNeutralButton("取消", null)
            .show()
    }

    private fun doBuy(item: ListingItem) {
        val me = deviceManager.getCurrentPlayerId() ?: run { status("请先登录"); return }
        val fp = deviceManager.getDeviceFingerprint() ?: run { status("请先连接手环"); return }
        val key = ApiClient.apiKey ?: run { status("缺少 apiKey，请重新登录"); return }
        lifecycleScope.launch {
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

    private fun doCancel(item: ListingItem) {
        val me = deviceManager.getCurrentPlayerId() ?: run { status("请先登录"); return }
        val fp = deviceManager.getDeviceFingerprint() ?: run { status("请先连接手环"); return }
        val key = ApiClient.apiKey ?: run { status("缺少 apiKey，请重新登录"); return }
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall {
                ApiClient.api.exchangeCancel(ExchangeCancelRequest(item.id, me, fp, key))
            }) {
                is ApiResult.Success -> {
                    status(if (r.data?.success == true) "已撤单，物品退回背包" else "撤单失败: ${r.data?.error}")
                    loadListings()
                }
                is ApiResult.Error -> status("撤单失败: ${r.message}")
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
        lifecycleScope.launch {
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
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.exchangeHistory(me) }) {
                is ApiResult.Success -> {
                    val sb = StringBuilder()
                    (r.data?.data ?: emptyList()).forEach { h ->
                        sb.append("${h.item_name}×${h.qty} ${h.price}金（手续费${h.fee}） ${if (h.seller_id == me) "卖出" else "买入"}")
                        if (h.item_uid.isNotEmpty()) sb.append(" ID:${h.item_uid}")
                        sb.append("\n")
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
