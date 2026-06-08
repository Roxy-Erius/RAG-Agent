package com.ragagent.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.ragagent.util.ImageUtil

/**
 * 全屏图片查看弹窗 — 点击商品图片后放大展示。
 */
class ImageViewerDialog(
    context: Context,
    private val imageBase64: String
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏暗色背景
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#CC000000")))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(24, 24, 24, 24)
        }

        ImageUtil.loadImage(imageView, imageBase64)
        container.addView(imageView)

        // 点击任意位置关闭
        container.setOnClickListener { dismiss() }

        setContentView(container)
    }
}
