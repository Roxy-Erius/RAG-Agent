package com.ragagent.ui

import android.content.Context
import android.view.ViewGroup

/**
 * 流式布局 — 子 View 超出宽度时自动换行。
 */
class FlowLayout(context: Context) : ViewGroup(context) {

    private val horizontalGap = 10  // px
    private val verticalGap = 10    // px

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        var x = 0
        var y = 0
        var rowHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            child.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            val w = child.measuredWidth
            val h = child.measuredHeight

            if (x + w > maxWidth && x > 0) {
                // 换行
                x = 0
                y += rowHeight + verticalGap
                rowHeight = 0
            }
            x += w + horizontalGap
            rowHeight = maxOf(rowHeight, h)
        }
        val totalHeight = y + rowHeight + paddingTop + paddingBottom
        setMeasuredDimension(maxWidth, resolveSize(totalHeight, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        var x = paddingLeft
        var y = paddingTop
        var rowHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val w = child.measuredWidth
            val h = child.measuredHeight

            if (x + w > maxWidth + paddingLeft && x > paddingLeft) {
                x = paddingLeft
                y += rowHeight + verticalGap
                rowHeight = 0
            }
            child.layout(x, y, x + w, y + h)
            x += w + horizontalGap
            rowHeight = maxOf(rowHeight, h)
        }
    }
}
