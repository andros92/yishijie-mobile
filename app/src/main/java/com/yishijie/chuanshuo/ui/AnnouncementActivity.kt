package com.yishijie.chuanshuo.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.databinding.ActivityAnnouncementBinding
import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.launch

class AnnouncementActivity : BaseActivity() {

    private lateinit var binding: ActivityAnnouncementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnnouncementBinding.inflate(layoutInflater)
        setContentViewWithStatus(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        loadAnnouncements()
    }

    private fun loadAnnouncements() {
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.announcements() }) {
                is ApiResult.Success -> {
                    binding.llAnnouncements.removeAllViews()
                    val list = r.data?.data ?: emptyList()
                    if (list.isEmpty()) {
                        binding.llAnnouncements.addView(text("暂无公告"))
                        return@launch
                    }
                    list.forEach { a ->
                        val card = com.google.android.material.card.MaterialCardView(this@AnnouncementActivity).apply {
                            radius = dp(14).toFloat()
                            elevation = dp(1).toFloat()
                            setCardBackgroundColor(Color.parseColor("#1A2242"))
                            strokeColor = Color.parseColor("#2EFFFFFF")
                            strokeWidth = 1
                        }
                        val inner = LinearLayout(this@AnnouncementActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(12), dp(10), dp(12), dp(10))
                        }
                        inner.addView(text(a.title, 15f, Color.parseColor("#F4F6FF"), true))
                        inner.addView(text(a.content, 13f, Color.parseColor("#C7CFE8")))
                        inner.addView(text(a.created_at.take(10), 11f, Color.parseColor("#5D6B8C")))
                        card.addView(inner)
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(8) }
                        binding.llAnnouncements.addView(card, lp)
                    }
                }
                is ApiResult.Error -> binding.llAnnouncements.addView(text("加载失败: ${r.message}"))
            }
        }
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
