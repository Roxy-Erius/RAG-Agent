package com.ragagent.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView

/**
 * 图片工具 — 解码 Base64 data URL 并加载到 ImageView。
 */
object ImageUtil {

    /**
     * 将 Base64 data URL（data:image/jpeg;base64,...）解码为 Bitmap。
     */
    fun decodeBase64(dataUrl: String): Bitmap? {
        return try {
            val pure = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            val bytes = Base64.decode(pure, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将 Base64 data URL 加载到 ImageView，失败时显示占位图。
     */
    fun loadImage(imageView: ImageView, imageBase64: String?, placeholderResId: Int? = null) {
        if (!imageBase64.isNullOrBlank()) {
            val bitmap = decodeBase64(imageBase64)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                return
            }
        }
        if (placeholderResId != null) {
            imageView.setImageResource(placeholderResId)
        }
    }
}
