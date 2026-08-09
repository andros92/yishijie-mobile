package com.yishijie.chuanshuo.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.api.RedeemRequest
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.ActivityRedeemBinding
import kotlinx.coroutines.launch

/**
 * 礼包码兑换：兑换成功后奖励发到游戏邮箱（手机端直接调用服务器）
 */
class RedeemActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRedeemBinding
    private lateinit var deviceManager: DeviceManager
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRedeemBinding.inflate(layoutInflater)
        setContentView(binding.root)
        deviceManager = DeviceManager.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRedeem.setOnClickListener { doRedeem() }
    }

    private fun doRedeem() {
        if (busy) return
        val me = deviceManager.getCurrentPlayerId() ?: run {
            status("请先登录账号（首页注册/登录）")
            return
        }
        val fp = deviceManager.getDeviceFingerprint() ?: run {
            status("未获取到设备指纹，请先连接手环")
            return
        }
        val key = ApiClient.apiKey ?: run {
            status("缺少登录凭证，请重新登录")
            return
        }
        val code = binding.etCode.text.toString().trim()
        if (code.isEmpty()) {
            status("请输入礼包码")
            return
        }
        busy = true
        status("兑换中…")
        lifecycleScope.launch {
            val req = RedeemRequest(me, fp, key, code)
            when (val r = ApiClient.safeApiCall { ApiClient.api.redeem(req) }) {
                is ApiResult.Success -> {
                    val d = r.data
                    if (d?.success == true) {
                        val rewardText = formatRewards(d.rewards)
                        status("兑换成功！奖励已发送到邮箱，请查收。" + if (rewardText.isNotEmpty()) "\n奖励：$rewardText" else "")
                        binding.etCode.setText("")
                        Toast.makeText(this@RedeemActivity, "兑换成功，奖励已发到邮箱", Toast.LENGTH_SHORT).show()
                    } else {
                        status("兑换失败：${d?.error ?: "未知错误"}")
                    }
                }
                is ApiResult.Error -> status("兑换失败：${r.message}")
            }
            busy = false
        }
    }

    private fun formatRewards(rewards: JsonObject?): String {
        if (rewards == null || rewards.keySet().isEmpty()) return ""
        val parts = ArrayList<String>()
        val names = mapOf(
            "gold" to "金币", "coin" to "金币", "wood" to "木材", "stone" to "石块",
            "iron" to "铁矿", "copper" to "铜矿", "spirit_crystal" to "灵晶",
            "gem_core" to "宝石核心", "pet_case" to "宠物栏", "boss_ticket" to "BOSS券",
            "healing_potion" to "治疗药水", "mana_potion" to "蓝量药水",
            "class_change_ticket" to "转职券", "gem_protect_ticket" to "宝石保护券"
        )
        for (k in rewards.keySet()) {
            val v = try {
                rewards.get(k).asLong
            } catch (e: Exception) {
                continue
            }
            parts.add("${names[k] ?: k}×$v")
        }
        return parts.joinToString("、")
    }

    private fun status(msg: String) {
        binding.tvStatus.text = msg
    }
}
