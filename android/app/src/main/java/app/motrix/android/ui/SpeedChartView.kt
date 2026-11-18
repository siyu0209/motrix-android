package app.motrix.android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import app.motrix.android.util.Fmt

/**
 * 轻量速度趋势图（无第三方依赖）：维护最近 N 个采样点的下载/上传速度，
 * 画成两条折线。用于原生版仪表板「统计图表」(P1)。
 */
class SpeedChartView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private val n = 60
    private val down = LongArray(n)
    private val up = LongArray(n)
    private var count = 0

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x22000000
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AA3AF.toInt()
        textSize = dp(10f)
    }
    private val cDown = 0xFF4F8CFF.toInt()
    private val cUp = 0xFFB07CFF.toInt()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    /** 推入一个采样点（字节/秒）。 */
    fun push(d: Long, u: Long) {
        System.arraycopy(down, 1, down, 0, n - 1)
        System.arraycopy(up, 1, up, 0, n - 1)
        down[n - 1] = if (d < 0) 0 else d
        up[n - 1] = if (u < 0) 0 else u
        if (count < n) count++
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val padL = dp(8f); val padR = dp(8f); val padT = dp(12f); val padB = dp(14f)
        val cw = w - padL - padR; val ch = h - padT - padB
        if (cw <= 0 || ch <= 0) return

        // 网格 4 条横线
        for (i in 0..3) {
            val y = padT + ch * i / 3f
            canvas.drawLine(padL, y, padL + cw, y, gridPaint)
        }

        var max = 1L
        for (i in 0 until n) {
            if (down[i] > max) max = down[i]
            if (up[i] > max) max = up[i]
        }

        fun build(arr: LongArray): Path {
            val p = Path()
            if (count <= 0) return p
            val start = n - count
            for (i in 0 until count) {
                val idx = start + i
                val x = if (count == 1) padL else padL + cw * (i / (count - 1).toFloat())
                val y = padT + ch - ch * (arr[idx].toFloat() / max.toFloat())
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }

        linePaint.color = cDown
        canvas.drawPath(build(down), linePaint)
        linePaint.color = cUp
        canvas.drawPath(build(up), linePaint)

        // 峰值标注 + 时间轴
        canvas.drawText(Fmt.speed(max), padL, padT - dp(2f), textPaint)
        canvas.drawText("60s", padL + cw - dp(24f), h - dp(2f), textPaint)
    }
}
