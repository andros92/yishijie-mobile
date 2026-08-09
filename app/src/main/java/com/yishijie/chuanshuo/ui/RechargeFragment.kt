package com.yishijie.chuanshuo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.FragmentRechargeBinding
import com.yishijie.chuanshuo.interconnect.GameSyncManager
import kotlinx.coroutines.launch
import org.json.JSONObject

class RechargeFragment : Fragment() {

    private var _binding: FragmentRechargeBinding? = null
    private val binding get() = _binding!!
    private lateinit var deviceManager: DeviceManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRechargeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        deviceManager = DeviceManager.getInstance(requireContext())
        binding.cardPackage.setOnClickListener { handlePurchaseClick() }
        binding.btnCheckOrders.setOnClickListener { checkOrders() }
        binding.btnSyncSave.setOnClickListener { syncSaveToBand() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun handlePurchaseClick() {
        val playerId = deviceManager.getCurrentPlayerId()
        if (playerId == null) {
            status("请先连接手环并登录账号")
            return
        }
        val message = "3 万金币（¥3）\n\n您的玩家 ID：$playerId\n\n" +
                "请在爱发电支付页面的「备注 / 留言」中粘贴这个 ID，系统会自动发放金币。\n\n" +
                "💡 也可以在爱发电上直接搜索「异世界传说」找到我们"
        val dialog = AlertDialog.Builder(requireContext())
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
                    button.text = "去支付 ¥3"
                    button.isEnabled = true
                    button.setOnClickListener {
                        dialog.dismiss()
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("playerId", playerId))
                        Toast.makeText(requireContext(), "玩家 ID 已复制", Toast.LENGTH_SHORT).show()
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
            val save = GameSyncManager.getInstance(requireContext()).downloadSaveFromServer()
            if (save == null) {
                status("服务器没有存档，请先在手环上传")
                return@launch
            }
            GameSyncManager.getInstance(requireContext()).uploadSaveToBand(
                JSONObject(save.toString()),
                object : GameSyncManager.SaveCallback {
                    override fun onSaveUploaded(success: Boolean, message: String) {
                        if (_binding == null) return
                        status(if (success) "已同步到手环，打开手环即可看到金币" else "同步失败：$message")
                    }
                    override fun onSaveDownloaded(data: JSONObject?) {}
                    override fun onError(error: String) {
                        if (_binding == null) return
                        status("同步失败：$error")
                    }
                }
            )
        }
    }

    private fun status(msg: String) {
        binding.tvStatus.text = msg
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }
}
