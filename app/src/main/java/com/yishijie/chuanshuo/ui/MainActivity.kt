package com.yishijie.chuanshuo.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.yishijie.chuanshuo.R
import com.yishijie.chuanshuo.api.ApiClient
import com.yishijie.chuanshuo.databinding.ActivityMainBinding
import com.yishijie.chuanshuo.interconnect.GameSyncManager
import com.yishijie.chuanshuo.interconnect.InterconnManager
import com.yishijie.chuanshuo.service.CompanionService

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAB_HOME = 0
        const val TAB_RANK = 1
        const val TAB_RECHARGE = 2
        const val TAB_PROFILE = 3
    }

    private lateinit var binding: ActivityMainBinding
    private var currentTab = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InterconnManager.getInstance(this)
        GameSyncManager.getInstance(this)
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

    fun switchTab(tab: Int) {
        if (tab == currentTab) return
        currentTab = tab
        binding.bottomNav.menu.getItem(tab).isChecked = true
        val frag: Fragment = when (tab) {
            TAB_RANK -> LeaderboardFragment()
            TAB_RECHARGE -> RechargeFragment()
            TAB_PROFILE -> ProfileFragment()
            else -> HomeFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, frag)
            .commitAllowingStateLoss()
    }
}
