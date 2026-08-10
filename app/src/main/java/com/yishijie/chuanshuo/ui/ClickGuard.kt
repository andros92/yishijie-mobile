package com.yishijie.chuanshuo.ui

/**
 * 全局点击防抖：防止快速双击重复跳转/重复提交导致页面异常（空白屏等）
 */
object ClickGuard {
    private var last = 0L

    @Synchronized
    fun allow(gapMs: Long = 500): Boolean {
        val now = System.currentTimeMillis()
        if (now - last < gapMs) return false
        last = now
        return true
    }
}
