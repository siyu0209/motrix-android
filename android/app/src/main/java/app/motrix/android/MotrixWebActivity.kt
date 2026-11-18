package app.motrix.android

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.motrix.android.engine.Aria2Rpc
import app.motrix.android.engine.Aria2Service
import app.motrix.android.engine.Engine
import app.motrix.android.web.BridgeServer
import app.motrix.android.web.RpcDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 路线3B：加载 Motrix 原版 React Web UI（assets/renderer-web），
 * 通过本地兼容 server（BridgeServer：静态托管 + /rpc 桥 + /rpc/events WS）
 * 同源连端内 aria2 引擎，最大限度还原原版界面与功能。
 *
 * 调试通道（无需 adb 注入触摸即可自动化导航/截图）：
 *  am start -n app.motrix.android/.MainActivity --es mtx_action open_classic \
 *      [--es mtx_path dashboard] [--es mtx_js "<javascript>"]
 *  mtx_js 执行结果会以 tag=MTXJS 打到 logcat。
 */
class MotrixWebActivity : AppCompatActivity() {

    private var server: BridgeServer? = null
    private var webView: WebView? = null
    private val port = 18601
    private lateinit var dispatcher: RpcDispatcher
    private var pendingJs: String? = null
    private var baseUrl = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Engine.init(this)
        dispatcher = RpcDispatcher(this)
        val eng = Prefs(this).loadSettings()

        // 1) 起前台服务 → 内部 Engine.start(settings) 拉起 aria2 引擎
        try {
            ContextCompat.startForegroundService(this, Intent(this, Aria2Service::class.java))
        } catch (e: Exception) {
            Log.e("MotrixWeb", "service start failed: ${e.message}")
        }

        // 2) 本地兼容 server：静态托管 renderer-web + /rpc 桥 + /rpc/events WebSocket
        try {
            server = BridgeServer(this, port, dispatcher).also { it.start(4000, false) }
        } catch (e: Exception) {
            Log.e("MotrixWeb", "bridge start failed: ${e.message}")
        }

        // 3) WebView 同源加载（renderer 会以 location.origin 作为后端基址）
        baseUrl = "http://127.0.0.1:$port"
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = false
            settings.useWideViewPort = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val js = pendingJs
                    if (js != null) {
                        pendingJs = null
                        // 给 React 挂载/路由一点时间
                        view?.postDelayed({ evalJs(js) }, 1500)
                    }
                }
            }
        }
        webView = web
        web.setBackgroundColor(0xFF0E0E10.toInt())

        // ── D-3B-05：右上角「原生」入口 → 回到原生界面（MainActivity） ──
        // WebView 外套一层 FrameLayout，把入口按钮作为原生悬浮层压在 WebView 之上，
        // 不侵入 M·O 顶栏/筛选/侧栏 toggle 的 DOM；只占右上角自身边界这一小块，
        // 通过 WindowInsets 规避状态栏/导航栏/刘海（竖屏、横屏安全区都不遮挡）。
        val root = FrameLayout(this)
        root.addView(
            web,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val nativeBtn = TextView(this).apply {
            text = "原生"
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            contentDescription = "返回原生界面"
            background = GradientDrawable().apply {
                cornerRadius = dp(17).toFloat()
                setColor(0xCC20242B.toInt())
                setStroke(dp(1), 0x33FFFFFF)
            }
            elevation = dp(6).toFloat()
            setPadding(dp(12), dp(5), dp(12), dp(5))
            alpha = 0.94f
            setOnClickListener { returnToNative() }
        }
        // 位置改到左下角（原右上角会遮挡页面右侧筛选/搜索按钮）；
        // 抬高到底部状态条(常规/速度/引擎就绪)之上，避免压住底部功能按钮。
        val btnLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(34),
            Gravity.BOTTOM or Gravity.START
        ).apply {
            leftMargin = dp(12)
            bottomMargin = dp(64)
        }
        root.addView(nativeBtn, btnLp)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            btnLp.leftMargin = bars.left + dp(12)
            btnLp.bottomMargin = bars.bottom + dp(64)
            nativeBtn.layoutParams = btnLp
            insets
        }
        setContentView(root)

        val path = intent?.getStringExtra("mtx_path")
        // 调试：--es mtx_vw "640" 用固定 CSS 视口宽度模拟不同设备宽度（配合 @media 断点验证自适应）
        val vw = intent?.getStringExtra("mtx_vw")
        val q = if (!vw.isNullOrBlank()) "?vw=$vw" else ""
        pendingJs = parseJs(intent)
        web.loadUrl("http://127.0.0.1:$port" + normalizePath(path) + q)

        // 4) 等 aria2 就绪后把 RPC 挂给 dispatcher
        lifecycleScope.launch {
            val rpc = Aria2Rpc(eng.rpcPort, eng.rpcSecret)
            for (i in 0 until 120) {
                if (rpc.versionOk()) {
                    dispatcher.attach(rpc)
                    Log.i("MotrixWeb", "rpc attached on port ${eng.rpcPort}")
                    break
                }
                delay(300)
            }
        }
    }

    /** D-3B-05：回到原生界面。常规是从 MainActivity 进入（栈里有原生界面）→ finish 自然回去；
     *  若是被外部/独立拉起（本 Activity 为任务根）→ 显式拉起 MainActivity 再结束自己。 */
    private fun returnToNative() {
        try {
            if (isTaskRoot) {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
        } catch (e: Exception) {
            Log.e("MotrixWeb", "return to native failed: ${e.message}")
        }
        finish()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun normalizePath(path: String?): String {
        if (path.isNullOrBlank()) return "/"
        return if (path.startsWith("/")) path else "/$path"
    }

    private fun evalJs(js: String) {
        webView?.evaluateJavascript(js) { result -> Log.i("MTXJS", result ?: "null") }
    }

    /** 支持 --es mtx_js "<js>"（含空格需引号，adb shell 常被拆词）或 --es mtx_js_b64 "<base64>"（推荐）。 */
    private fun parseJs(intent: Intent?): String? {
        if (intent == null) return null
        intent.getStringExtra("mtx_js")?.let { if (it.isNotBlank()) return it }
        val b64 = intent.getStringExtra("mtx_js_b64") ?: return null
        return try {
            String(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("MotrixWeb", "bad mtx_js_b64: ${e.message}")
            null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val js = parseJs(intent)
        val path = intent.getStringExtra("mtx_path")
        if (path != null) {
            pendingJs = js
            webView?.loadUrl(baseUrl + normalizePath(path))
        } else if (js != null) {
            // 页面已加载，直接在当前页面执行
            evalJs(js)
        }
    }

    override fun onBackPressed() {
        val w = webView
        if (w == null) { super.onBackPressed(); return }
        // 智能返回：不在仪表板 → 一步回到仪表板；已在仪表板 → 退出应用（不再逐级回退，避免"一直点回到上一步的上一步"）
        w.evaluateJavascript("(location.hash||'')") { raw ->
            val h = raw?.trim()?.trim('"') ?: ""
            runOnUiThread {
                if (h.isEmpty() || h == "#" || h == "#/") {
                    finish()
                } else {
                    w.evaluateJavascript("location.hash='#/'", null)
                }
            }
        }
    }

    override fun onDestroy() {
        webView?.stopLoading()
        webView?.destroy()
        server?.stop()
        super.onDestroy()
    }
}
