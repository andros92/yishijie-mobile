package com.yishijie.chuanshuo.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.yishijie.chuanshuo.R
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.api.LeaderboardItem
import com.yishijie.chuanshuo.databinding.FragmentLeaderboardBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!
    private var type = "level"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tabLevel.setOnClickListener { switchType("level") }
        binding.tabPet.setOnClickListener { switchType("pet") }
        binding.tabTower.setOnClickListener { switchType("tower") }
        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun switchType(t: String) {
        if (type == t) return
        type = t
        binding.tabLevel.setBackgroundResource(if (t == "level") R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        binding.tabPet.setBackgroundResource(if (t == "pet") R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        binding.tabTower.setBackgroundResource(if (t == "tower") R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        binding.tabLevel.setTextColor(Color.parseColor(if (t == "level") "#FFFFFF" else "#6B7280"))
        binding.tabPet.setTextColor(Color.parseColor(if (t == "pet") "#FFFFFF" else "#6B7280"))
        binding.tabTower.setTextColor(Color.parseColor(if (t == "tower") "#FFFFFF" else "#6B7280"))
        load()
    }

    private fun load() {
        binding.tvStatus.text = "加载中..."
        CoroutineScope(Dispatchers.Main).launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.leaderboard(type, 50) }) {
                is ApiResult.Success -> render(r.data?.data ?: emptyList())
                is ApiResult.Error -> {
                    binding.llList.removeAllViews()
                    binding.llList.addView(emptyRow("加载失败：${r.message}"))
                    binding.tvStatus.text = ""
                }
            }
        }
    }

    private fun render(list: List<LeaderboardItem>) {
        binding.llList.removeAllViews()
        if (list.isEmpty()) {
            binding.llList.addView(emptyRow("暂无数据"))
            binding.tvStatus.text = ""
            return
        }
        list.forEachIndexed { index, it ->
            val valText = when (type) {
                "pet" -> "战力 ${it.value}" + if (it.count > 0) " · ${it.count}只" else ""
                "tower" -> "第 ${it.value} 层"
                else -> "Lv.${it.value}"
            }
            binding.llList.addView(row(index + 1, it.playerName.ifEmpty { it.playerId }, valText))
        }
        binding.tvStatus.text = ""
    }

    private fun emptyRow(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#9CA3AF"))
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(24))
        }
    }

    private fun row(rank: Int, name: String, value: String): com.google.android.material.card.MaterialCardView {
        val card = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            radius = dp(14).toFloat()
            elevation = dp(1).toFloat()
            setCardBackgroundColor(Color.WHITE)
            strokeColor = Color.parseColor("#E2E6EF")
            strokeWidth = 1
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        row.addView(TextView(requireContext()).apply {
            text = rank.toString()
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(when (rank) {
                1 -> Color.parseColor("#F59E0B")
                2 -> Color.parseColor("#94A3B8")
                3 -> Color.parseColor("#D97706")
                else -> Color.parseColor("#9CA3AF")
            })
            gravity = Gravity.CENTER
            setWidth(dp(32))
        })
        row.addView(TextView(requireContext()).apply {
            text = name
            textSize = 15f
            setTextColor(Color.parseColor("#1A1D26"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = value
            textSize = 13f
            setTextColor(Color.parseColor("#2563EB"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        card.addView(row)
        return card
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
