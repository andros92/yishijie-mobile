package com.yishijie.chuanshuo.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.yishijie.chuanshuo.R

/**
 * 自绘确认弹窗（应用风格，不用系统默认弹窗）
 */
object DialogUtils {

    fun dp(activity: Activity, v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    fun showConfirm(activity: Activity, title: String, message: String, okText: String = "确认", onOk: () -> Unit) {
        val card = MaterialCardView(activity).apply {
            radius = dp(activity, 20).toFloat()
            elevation = dp(activity, 2).toFloat()
            setCardBackgroundColor(Color.parseColor("#141C34"))
            strokeColor = Color.parseColor("#3D5291")
            strokeWidth = 1
        }
        val inner = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 18))
        }
        inner.addView(TextView(activity).apply {
            text = title
            textSize = 17f
            setTextColor(Color.parseColor("#FFD36D"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        inner.addView(TextView(activity).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#F4F6FF"))
            setPadding(0, dp(activity, 12), 0, dp(activity, 18))
        })
        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val cancel = TextView(activity).apply {
            text = "取消"
            textSize = 15f
            setTextColor(Color.parseColor("#C8D4E8"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(activity, 26), dp(activity, 10), dp(activity, 26), dp(activity, 10))
            background = activity.resources.getDrawable(R.drawable.bg_chip, activity.theme)
        }
        val ok = TextView(activity).apply {
            text = okText
            textSize = 15f
            setTextColor(Color.parseColor("#0B0F1F"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(activity, 26), dp(activity, 10), dp(activity, 26), dp(activity, 10))
            background = activity.resources.getDrawable(R.drawable.bg_btn_gold, activity.theme)
        }
        val dialog = Dialog(activity).apply {
            setContentView(card)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        cancel.setOnClickListener { dialog.dismiss() }
        ok.setOnClickListener {
            dialog.dismiss()
            onOk()
        }
        btnRow.addView(cancel)
        btnRow.addView(ok, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(activity, 8)
        })
        inner.addView(btnRow)
        card.addView(inner)
        dialog.show()
    }
}
