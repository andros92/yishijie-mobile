package com.yishijie.chuanshuo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.FragmentProfileBinding

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
        binding.rowSave.setOnClickListener { startActivity(Intent(requireContext(), SaveManagerActivity::class.java)) }
        binding.rowExchange.setOnClickListener { startActivity(Intent(requireContext(), ExchangeActivity::class.java)) }
        binding.rowBridge.setOnClickListener { startActivity(Intent(requireContext(), BridgeActivity::class.java)) }
        binding.rowAnnouncement.setOnClickListener { startActivity(Intent(requireContext(), AnnouncementActivity::class.java)) }
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
}
