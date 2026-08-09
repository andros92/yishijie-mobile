package com.yishijie.chuanshuo.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.api.ListingItem
import com.yishijie.chuanshuo.databinding.ActivityExchangeBrowseBinding
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 交易所浏览页：只读查看在售挂单（物品/装备/宠物），不做任何交易操作。
 * 挂单、购买、撤单只能通过手环端进行。
 */
class ExchangeBrowseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExchangeBrowseBinding
    private var category: String? = null
    private var page = 1
    private var totalPages = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExchangeBrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSearch.setOnClickListener { page = 1; loadListings() }
        binding.etKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                page = 1
                loadListings()
                true
            } else false
        }

        val cats = listOf(
            binding.catAll to null,
            binding.catItem to "item",
            binding.catGear to "gear",
            binding.catPet to "pet"
        )
        cats.forEach { (view, cat) ->
            view.setOnClickListener {
                category = cat
                cats.forEach { (v, c) -> setChip(v, c == cat) }
                page = 1
                loadListings()
            }
        }
        binding.btnPrev.setOnClickListener {
            if (page > 1) {
                page--
                loadListings()
            }
        }
        binding.btnNext.setOnClickListener {
            if (page < totalPages) {
                page++
                loadListings()
            }
        }
        loadListings()
    }

    private fun setChip(view: TextView, on: Boolean) {
        view.background = resources.getDrawable(
            if (on) com.yishijie.chuanshuo.R.drawable.bg_chip_on else com.yishijie.chuanshuo.R.drawable.bg_chip,
            theme
        )
        view.setTextColor(Color.parseColor(if (on) "#0B0F1F" else "#9AA3C0"))
        view.setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun loadListings() {
        binding.llListings.removeAllViews()
        binding.llListings.addView(text("加载中...", 14f, Color.parseColor("#9AA3C0")))
        val kw = binding.etKeyword.text.toString().trim().ifEmpty { null }
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall {
                ApiClient.api.exchangeListings(page, 20, category, kw)
            }) {
                is ApiResult.Success -> {
                    binding.llListings.removeAllViews()
                    val data = r.data?.data ?: emptyList()
                    totalPages = maxOf(1, (r.data?.total ?: 0).let { total -> (total + 19) / 20 })
                    binding.tvPage.text = "第 $page / $totalPages 页"
                    if (data.isEmpty()) {
                        binding.llListings.addView(text("暂无在售挂单", 14f, Color.parseColor("#9AA3C0")))
                        return@launch
                    }
                    data.forEach { item -> binding.llListings.addView(row(item)) }
                }
                is ApiResult.Error -> {
                    binding.llListings.removeAllViews()
                    binding.llListings.addView(text("加载失败: ${r.message}", 14f, Color.parseColor("#FF6B7A")))
                }
            }
        }
    }

    private fun row(item: ListingItem): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(14).toFloat()
            elevation = dp(1).toFloat()
            setCardBackgroundColor(Color.parseColor("#1A2242"))
            strokeColor = Color.parseColor("#2EFFFFFF")
            strokeWidth = 1
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val pet = item.pet?.let { JSONObject(it.toString()) }
        val title = if (pet != null) {
            "宠物·${pet.optString("name", item.item_name)} Lv.${pet.optInt("lv", 1)}"
        } else {
            val q = if (item.quality.isNotEmpty()) "[${item.quality}]" else ""
            "${item.item_name}$q ×${item.qty}"
        }
        inner.addView(text(title, 15f, Color.parseColor("#F4F6FF"), true))
        inner.addView(text("${item.price} 金币 · ${item.seller_name}", 13f, Color.parseColor("#F5C453")))
        if (item.item_uid.isNotEmpty()) {
            inner.addView(text("ID: ${item.item_uid}", 11f, Color.parseColor("#5D6B8C")))
        }
        card.addView(inner)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        card.layoutParams = lp
        return card
    }

    private fun text(txt: String, size: Float = 14f, color: Int = Color.parseColor("#F4F6FF"), bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = txt
            textSize = size
            setTextColor(color)
            setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            gravity = Gravity.START
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
