package com.yishijie.chuanshuo.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.api.MarkPaidRequest
import com.yishijie.chuanshuo.api.RechargeOrderRequest
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.ActivityRechargeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RechargeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRechargeBinding
    private lateinit var deviceManager: DeviceManager
    private var lastOrderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRechargeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        deviceManager = DeviceManager.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCreateOrder.setOnClickListener { createOrder() }
        binding.btnMarkPaid.setOnClickListener { markPaid() }
    }

    private fun createOrder() {
        val me = deviceManager.getCurrentPlayerId() ?: run { status("请先登录账号"); return }
        val fp = deviceManager.getDeviceFingerprint() ?: run { status("请先连接手环"); return }
        val key = ApiClient.apiKey ?: run { status("缺少 apiKey，请重新登录"); return }
        val amount = binding.etAmount.text.toString().toDoubleOrNull()
        if (amount == null || amount <= 0) { status("请输入有效金额"); return }
        CoroutineScope(Dispatchers.Main).launch {
            when (val r = ApiClient.safeApiCall {
                ApiClient.api.createRechargeOrder(RechargeOrderRequest(me, fp, key, amount))
            }) {
                is ApiResult.Success -> {
                    val d = r.data
                    if (d?.success == true) {
                        lastOrderId = d.orderId
                        status("订单已创建：${d.orderId}\n${d.qty} 金币，等待支付确认")
                    } else {
                        status("创建失败: ${d?.error}")
                    }
                }
                is ApiResult.Error -> status("创建失败: ${r.message}")
            }
        }
    }

    private fun markPaid() {
        val orderId = lastOrderId ?: run { status("请先创建订单"); return }
        val secret = binding.etSecret.text.toString().trim()
        if (secret.isEmpty()) { status("请输入管理密钥"); return }
        CoroutineScope(Dispatchers.Main).launch {
            when (val r = ApiClient.safeApiCall {
                ApiClient.api.markOrderPaid(MarkPaidRequest(orderId, secret))
            }) {
                is ApiResult.Success -> status(if (r.data?.success == true) "已确认到账，金币已入存档" else "确认失败: ${r.data?.error}")
                is ApiResult.Error -> status("确认失败: ${r.message}")
            }
        }
    }

    private fun status(msg: String) {
        binding.tvStatus.text = msg
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
