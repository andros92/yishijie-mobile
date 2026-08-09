package com.yishijie.chuanshuo.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import com.yishijie.chuanshuo.data.DeviceManager
import com.yishijie.chuanshuo.databinding.ActivitySaveManagerBinding
import com.yishijie.chuanshuo.interconnect.GameSyncManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SaveManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySaveManagerBinding
    private lateinit var syncManager: GameSyncManager
    private lateinit var deviceManager: DeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySaveManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        syncManager = GameSyncManager.getInstance(this)
        deviceManager = DeviceManager.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnDownloadServer.setOnClickListener {
            if (deviceManager.getCurrentPlayerId() == null) { status("请先登录账号"); return@setOnClickListener }
            lifecycleScope.launch {
                status("下载中...")
                val data = withContext(Dispatchers.IO) { syncManager.downloadSaveFromServer() }
                if (data != null) {
                    status("已从服务器下载存档")
                    syncManager.uploadSaveToBand(JSONObject(data.toString()), object : GameSyncManager.SaveCallback {
                        override fun onSaveUploaded(success: Boolean, message: String) {}
                        override fun onSaveDownloaded(data: JSONObject?) {}
                        override fun onError(error: String) { status("下发手环失败: $error") }
                    })
                } else {
                    status("下载失败：请检查登录/连接，或服务器上还没有存档")
                }
            }
        }

        binding.btnUploadServer.setOnClickListener {
            if (deviceManager.getCurrentPlayerId() == null) { status("请先登录账号"); return@setOnClickListener }
            lifecycleScope.launch {
                status("等待手环存档...")
                syncManager.downloadSaveFromBand(object : GameSyncManager.SaveCallback {
                    override fun onSaveUploaded(success: Boolean, message: String) {}
                    override fun onSaveDownloaded(data: JSONObject?) {
                        if (data == null) { status("手环没有返回存档"); return }
                        lifecycleScope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                syncManager.uploadSaveToServer(com.google.gson.JsonParser().parse(data.toString()).asJsonObject)
                            }
                            status(if (ok) "已上传到服务器" else "上传失败")
                        }
                    }
                    override fun onError(error: String) { status("获取手环存档失败: $error") }
                })
            }
        }

        binding.btnDownloadBand.setOnClickListener {
            syncManager.downloadSaveFromBand(object : GameSyncManager.SaveCallback {
                override fun onSaveUploaded(success: Boolean, message: String) {}
                override fun onSaveDownloaded(data: JSONObject?) {
                    status(if (data != null) "已从手环获取存档" else "手环没有返回存档")
                }
                override fun onError(error: String) { status("失败: $error") }
            })
        }

        binding.btnUploadBand.setOnClickListener {
            Toast.makeText(this, "请先连接手环，由手环端发起存档推送", Toast.LENGTH_SHORT).show()
            status("提示：手环端操作“上传存档”会推送到本机并自动同步服务器")
        }
    }

    private fun status(msg: String) {
        binding.tvStatus.text = msg
    }
}
