package com.yishijie.chuanshuo.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.yishijie.chuanshuo.R
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.databinding.ActivityMainBinding
import com.yishijie.chuanshuo.interconnect.GameSyncManager
import com.yishijie.chuanshuo.interconnect.InterconnManager
import com.yishijie.chuanshuo.service.CompanionService
import java.io.File
import java.util.Date

class MainActivity : BaseActivity() {

    companion object {
        const val TAB_HOME = 0
        const val TAB_RANK = 1
        const val TAB_RECHARGE = 2
        const val TAB_PROFILE = 3
    }

    private lateinit var binding: ActivityMainBinding
    private var currentTab = -1
    private var lastSwitchAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLog()
        ApiClient.init(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentViewWithStatus(binding.root)

        val interconn = InterconnManager.getInstance(this)
        GameSyncManager.getInstance(this)
        interconn.initialize()
        CompanionService.start(this)

        binding.bottomNav.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchTab(TAB_HOME)
                R.id.nav_rank -> switchTab(TAB_RANK)
                R.id.nav_recharge -> switchTab(TAB_RECHARGE)
                R.id.nav_profile -> switchTab(TAB_PROFILE)
            }
            true
        }
        switchTab(TAB_HOME)
    }

    override fun onResume() {
        super.onResume()
        // 兜底：进程被系统回收重建后，Fragment 可能没恢复成功，容器空了就只剩背景
        // 检测到容器为空时重新挂载当前页签
        ensureFragmentAttached()
    }

    private fun ensureFragmentAttached() {
        try {
            if (isFinishing || isDestroyed) return
            val existing = supportFragmentManager.findFragmentById(R.id.content)
            if (existing == null) {
                val tab = if (currentTab >= 0) currentTab else TAB_HOME
                currentTab = -1
                switchTab(tab)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "恢复页面失败", e)
        }
    }

    // 全局崩溃日志：下次再出现白屏/闪退，可以从 files/crash.log 拿到真实堆栈
    private var prevCrashHandler: Thread.UncaughtExceptionHandler? = null
    private fun installCrashLog() {
        prevCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val f = File(filesDir, "crash.log")
                f.appendText("\n=== " + Date() + " [" + thread.name + "] ===\n" + Log.getStackTraceString(e) + "\n")
            } catch (ignored: Exception) {
            }
            prevCrashHandler?.uncaughtException(thread, e)
        }
    }

    fun switchTab(tab: Int) {
        val now = System.currentTimeMillis()
        if (now - lastSwitchAt < 400) return
        lastSwitchAt = now
        if (tab == currentTab) return
        currentTab = tab
        binding.bottomNav.menu.getItem(tab).isChecked = true
        val frag: Fragment = when (tab) {
            TAB_RANK -> LeaderboardFragment()
            TAB_RECHARGE -> RechargeFragment()
            TAB_PROFILE -> ProfileFragment()
            else -> HomeFragment()
        }
        try {
            if (!supportFragmentManager.isStateSaved) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content, frag)
                    .commitAllowingStateLoss()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "切换页面失败: $tab", e)
        }
    }
}
