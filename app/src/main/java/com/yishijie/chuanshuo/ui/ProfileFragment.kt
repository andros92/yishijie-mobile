package com.yishijie.chuanshuo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.api.ApiResult
import com.yishijie.chuanshuo.api.RenameRequest
import com.yishijie.chuanshuo.BuildConfig
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var deviceManager: DeviceManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        deviceManager = DeviceManager.getInstance(requireContext())
        binding.btnCopyId.setOnClickListener { copyId() }
        binding.rowRename.setOnClickListener { rename() }
        binding.rowSave.setOnClickListener { startActivity(Intent(requireContext(), SaveManagerActivity::class.java)) }
        binding.rowPvp.setOnClickListener { startActivity(Intent(requireContext(), PvpActivity::class.java)) }
        binding.rowExchange.setOnClickListener { startActivity(Intent(requireContext(), ExchangeBrowseActivity::class.java)) }
        binding.rowBridge.setOnClickListener { startActivity(Intent(requireContext(), BridgeActivity::class.java)) }
        binding.rowAnnouncement.setOnClickListener { startActivity(Intent(requireContext(), AnnouncementActivity::class.java)) }
        binding.rowRedeem.setOnClickListener { startActivity(Intent(requireContext(), RedeemActivity::class.java)) }
        binding.rowUpdate.setOnClickListener { checkUpdate() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refresh() {
        val id = deviceManager.getCurrentPlayerId()
        val name = deviceManager.getCurrentPlayerName()
        binding.tvProfileName.text = if (name.isNullOrEmpty()) "未登录" else name
        binding.tvProfileId.text = if (id.isNullOrEmpty()) "玩家ID：--" else "玩家ID：$id"
        binding.tvProfileTip.text = if (id.isNullOrEmpty()) "连接手环并在首页注册后显示" else "充值 / 交易时使用玩家 ID"
    }

    private fun copyId() {
        val id = deviceManager.getCurrentPlayerId()
        if (id.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "请先登录账号", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("playerId", id))
        Toast.makeText(requireContext(), "玩家 ID 已复制：$id", Toast.LENGTH_SHORT).show()
    }

    private fun rename() {
        val me = deviceManager.getCurrentPlayerId() ?: run { Toast.makeText(requireContext(), "请先登录账号", Toast.LENGTH_SHORT).show(); return }
        val input = EditText(requireContext()).apply {
            hint = "新昵称（2-12字）"
            setText(deviceManager.getCurrentPlayerName() ?: "")
            setSingleLine(true)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("修改昵称")
            .setMessage("每月限改一次，不能使用违禁词")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim()
                if (name.length < 2 || name.length > 12) {
                    Toast.makeText(requireContext(), "昵称需 2-12 个字符", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                doRename(me, name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doRename(playerId: String, newName: String) {
        val fp = deviceManager.getDeviceFingerprint() ?: run { Toast.makeText(requireContext(), "请先连接手环", Toast.LENGTH_SHORT).show(); return }
        val key = ApiClient.apiKey ?: run { Toast.makeText(requireContext(), "缺少 apiKey，请重新登录", Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.rename(RenameRequest(playerId, fp, key, newName)) }) {
                is ApiResult.Success -> {
                    val d = r.data
                    if (d?.success == true) {
                        deviceManager.updatePlayerName(playerId, d.playerName.ifEmpty { newName })
                        deviceManager.setNameChanged(true)
                        refresh()
                        Toast.makeText(requireContext(), d.message.ifEmpty { "改名成功" }, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), d?.error ?: "改名失败", Toast.LENGTH_SHORT).show()
                    }
                }
                is ApiResult.Error -> Toast.makeText(requireContext(), "改名失败：${r.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 手动检查更新：有新版本弹窗并跳转下载链接，已是最新则提示
    private fun checkUpdate() {
        lifecycleScope.launch {
            when (val r = ApiClient.safeApiCall { ApiClient.api.version() }) {
                is ApiResult.Success -> {
                    val v = r.data
                    if (v != null && v.versionCode > BuildConfig.VERSION_CODE) {
                        val builder = AlertDialog.Builder(requireContext())
                            .setTitle("发现新版本 ${v.versionName}")
                            .setMessage("更新内容：\n${v.updateNotes.ifEmpty { "修复与优化" }}")
                            .setNegativeButton("以后再说", null)
                        if (!v.downloadUrl.isNullOrEmpty()) {
                            builder.setPositiveButton("立即更新") { _, _ ->
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.downloadUrl)))
                                } catch (e: Exception) {
                                    Toast.makeText(requireContext(), "无法打开下载链接", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        builder.show()
                    } else {
                        Toast.makeText(requireContext(), "已是最新版本（${v?.versionName ?: BuildConfig.VERSION_NAME}）", Toast.LENGTH_SHORT).show()
                    }
                }
                is ApiResult.Error -> Toast.makeText(requireContext(), "检查更新失败：${r.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
