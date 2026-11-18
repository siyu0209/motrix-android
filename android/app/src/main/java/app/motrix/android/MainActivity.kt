package app.motrix.android

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.EditText
import android.content.res.ColorStateList
import android.graphics.Color
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import app.motrix.android.engine.Aria2Service
import app.motrix.android.engine.DownloadTask
import app.motrix.android.engine.Engine
import app.motrix.android.ui.AddTaskDialog
import app.motrix.android.ui.SpeedChartView
import app.motrix.android.ui.TaskAdapter
import app.motrix.android.util.Fmt
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: TaskAdapter
    private lateinit var emptyView: View
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var txtDown: TextView
    private lateinit var txtUp: TextView
    private lateinit var txtCounts: TextView
    private lateinit var btnTurtle: MaterialButton
    private lateinit var btnNormal: MaterialButton
    private lateinit var btnHigh: MaterialButton
    private lateinit var chart: SpeedChartView

    /** 待导出的源文件（用户选好目标位置后复制）。 */
    private var pendingExport: File? = null

    /** 已记录过通知的 gid，避免重复。 */
    private val notifiedGids = HashSet<String>()

    private val exportFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            val src = pendingExport
            pendingExport = null
            if (uri != null && src != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        src.inputStream().use { it.copyTo(out) }
                    }
                    toast(getString(R.string.saved_ok))
                } catch (e: Exception) {
                    toast(getString(R.string.export_failed) + (e.message ?: ""))
                }
            }
        }

    private val pickTorrent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) toast("读取种子失败") else handleTorrentBytes(bytes)
            } catch (e: Exception) {
                toast("读取种子失败：${e.message}")
            }
        }
    }

    /** 解析种子文件：多文件弹文件勾选，单文件直接加入。 */
    private fun handleTorrentBytes(bytes: ByteArray) {
        val files = try { app.motrix.android.engine.TorrentParser.files(bytes) } catch (e: Exception) { emptyList() }
        if (files.size > 1) {
            val names = files.map { f -> f.path + "   (" + app.motrix.android.util.Fmt.size(f.size) + ")" }.toTypedArray()
            val checked = BooleanArray(files.size) { true }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_files_title)
                .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
                .setPositiveButton(R.string.download) { _, _ ->
                    val sel = files.filterIndexed { i, _ -> checked[i] }.map { it.index }
                    if (sel.isEmpty()) toast(getString(R.string.select_at_least_one))
                    else Engine.addTorrentBytes(bytes, sel) { ok, msg -> toast(if (ok) getString(R.string.torrent_added) else "失败：$msg") }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            Engine.addTorrentBytes(bytes) { ok, msg -> toast(if (ok) getString(R.string.torrent_added) else "失败：$msg") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        prefs = Prefs(this)
        notifiedGids.addAll(NotificationStore.gids(this))
        Engine.init(applicationContext)

        txtDown = findViewById(R.id.txtDownSpeed)
        txtUp = findViewById(R.id.txtUpSpeed)
        txtCounts = findViewById(R.id.txtCounts)
        emptyView = findViewById(R.id.emptyView)
        swipe = findViewById(R.id.swipe)

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        adapter = TaskAdapter(
            onToggle = { t: DownloadTask ->
                if (t.isActive || t.isWaiting) Engine.pause(t.gid) else Engine.resume(t.gid)
            },
            onRemove = { t: DownloadTask -> confirmRemove(t) },
            onItemClick = { t: DownloadTask -> showTaskDetail(t) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { showAddDialog() }

        btnTurtle = findViewById(R.id.btnSpeedTurtle)
        btnNormal = findViewById(R.id.btnSpeedNormal)
        btnHigh = findViewById(R.id.btnSpeedHigh)
        chart = findViewById(R.id.chartSpeeds)
        refreshSpeedButtons(prefs.speedMode())
        btnTurtle.setOnClickListener { setSpeedMode("turtle") }
        btnNormal.setOnClickListener { setSpeedMode("normal") }
        btnHigh.setOnClickListener { setSpeedMode("high") }
        // 启动后把已保存的限速档下发到 aria2（默认“高速”=不限速，无需下发）
        lifecycleScope.launch { delay(3500); applySpeedModeQuiet(prefs.speedMode()) }

        swipe.setOnRefreshListener { swipe.isRefreshing = false }

        requestNotifPermission()
        startEngineService()
        observeFlows()
        handleIntent(intent)
    }

    // ---------------- 供 adb / 外部调起（测试入口，也用于磁力/链接直接加任务） ----------------

    private fun showAddDialog() {
        AddTaskDialog.show(
            this,
            onAddUrl = { url -> addUrl(url) },
            onPickTorrent = { pickTorrent.launch("*/*") }
        )
    }

    private fun addUrl(url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        Engine.addUri(u) { ok, msg ->
            toast(if (ok) getString(R.string.task_added) else "失败：$msg")
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val act = intent.getStringExtra("mtx_action")
        val url = intent.getStringExtra("mtx_url")
        when {
            act == "open_add" -> showAddDialog()
            act == "open_classic" -> startActivity(Intent(this, MotrixWebActivity::class.java))
            act == "open_dir" -> showDownloadDir()
            act == "open_detail" -> {
                val t = Engine.tasks.value.firstOrNull()
                if (t != null) showTaskDetail(t) else toast("暂无任务")
            }
            act == "add_torrent_file" -> {
                val p = intent.getStringExtra("mtx_path")
                if (!p.isNullOrBlank()) {
                    try { handleTorrentBytes(java.io.File(p).readBytes()) } catch (e: Exception) { toast("读种子失败：${e.message}") }
                }
            }
            !url.isNullOrBlank() -> addUrl(url)
            else -> {
                val data = intent.data
                if (intent.action == Intent.ACTION_VIEW && data != null) {
                    val s = data.toString()
                    val isTorrent = data.scheme == "content" || s.endsWith(".torrent", true) ||
                        intent.type == "application/x-bittorrent"
                    if (isTorrent) {
                        try {
                            val bytes = contentResolver.openInputStream(data)?.use { it.readBytes() }
                            if (bytes != null) handleTorrentBytes(bytes) else toast("读种子失败")
                        } catch (e: Exception) {
                            toast("读种子失败：${e.message}")
                        }
                    } else if (s.startsWith("magnet:") || s.startsWith("http")) {
                        addUrl(s)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun startEngineService() {
        val intent = Intent(this, Aria2Service::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun observeFlows() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    Engine.tasks.collect { list ->
                        adapter.submit(list)
                        updateEmpty(list)
                        maybeNotify(list)
                    }
                }
                launch {
                    Engine.stat.collect { st ->
                        txtDown.text = "↓ " + Fmt.speed(st.downloadSpeed)
                        txtUp.text = "↑ " + Fmt.speed(st.uploadSpeed)
                        txtCounts.text = getString(R.string.counts_fmt, st.numActive, st.numWaiting, st.numStopped)
                        chart.push(st.downloadSpeed, st.uploadSpeed)
                    }
                }
                launch {
                    Engine.error.collect { err ->
                        if (!err.isNullOrBlank()) runOnUiThread { showDiag(err) }
                    }
                }
            }
        }
    }

    /** 引擎启动诊断弹窗：把 aria2 的真实退出码/输出显示出来，可复制。 */
    private fun showDiag(msg: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.diag_title))
            .setMessage(msg)
            .setPositiveButton(getString(R.string.diag_ok), null)
            .setNeutralButton(getString(R.string.diag_copy)) { _, _ ->
                copyToClipboard("motrix-diag", msg)
                toast(getString(R.string.diag_copied))
            }
            .show()
    }

    private fun updateEmpty(list: List<DownloadTask>) {
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmRemove(t: DownloadTask) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_task)
            .setMessage(t.name)
            .setPositiveButton(R.string.remove) { _, _ -> Engine.remove(t.gid) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- 任务详情 / 打开 / 导出 / 位置 ----------------

    private fun statusText(t: DownloadTask): String = when {
        t.isComplete -> getString(R.string.status_complete) + " · " + Fmt.size(t.totalBytes)
        t.isError -> getString(R.string.status_error) + " · " + t.errorMessage.ifBlank { "未知错误" }
        t.isPaused -> getString(R.string.status_paused) + " · " + Fmt.size(t.doneBytes) + " / " + Fmt.size(t.totalBytes)
        t.isWaiting -> getString(R.string.status_waiting) + " · " + Fmt.size(t.doneBytes) + " / " + Fmt.size(t.totalBytes)
        t.isActive -> getString(R.string.status_active) + " · " + Fmt.size(t.doneBytes) + " / " + Fmt.size(t.totalBytes) + " · " + t.progress + "%"
        else -> Fmt.size(t.doneBytes) + " / " + Fmt.size(t.totalBytes)
    }

    /** 从任务推导磁盘文件（若 aria2 未给出 path，则用 dir + name）。 */
    private fun taskFile(t: DownloadTask): File? {
        if (t.path.isNotBlank()) return File(t.path)
        if (t.dir.isNotBlank() && t.name.isNotBlank()) return File(t.dir, t.name)
        return null
    }

    private fun showTaskDetail(t: DownloadTask) {
        val v = layoutInflater.inflate(R.layout.dialog_task_detail, null)
        val f = taskFile(t)
        val loc = t.dir.ifBlank { f?.parent ?: "" }
        v.findViewById<TextView>(R.id.dName).text = t.name
        v.findViewById<TextView>(R.id.dStatus).text = statusText(t)
        v.findViewById<TextView>(R.id.dSize).text =
            getString(R.string.size_label) + "：" + Fmt.size(t.totalBytes) + "　已下载：" + Fmt.size(t.doneBytes)
        v.findViewById<TextView>(R.id.dPath).text =
            getString(R.string.location_label) + "：\n" + (f?.absolutePath ?: loc.ifBlank { "未知" })

        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.task_detail)
            .setView(v)
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.remove_task) { _, _ -> confirmRemove(t) }
            .create()

        v.findViewById<Button>(R.id.dOpen).setOnClickListener { openFile(t) }
        v.findViewById<Button>(R.id.dExport).setOnClickListener { exportFile(t) }
        v.findViewById<Button>(R.id.dCopy).setOnClickListener {
            val path = f?.absolutePath ?: loc
            copyToClipboard("path", path)
            toast(getString(R.string.path_copied))
        }
        v.findViewById<Button>(R.id.dRename).setOnClickListener { dlg.dismiss(); promptRename(t) }
        dlg.show()
    }

    private fun openFile(t: DownloadTask) {
        val f = taskFile(t)
        if (f == null) { toast(getString(R.string.file_not_found)); return }
        if (!f.exists()) { toast(getString(R.string.file_not_ready) + "：" + f.absolutePath); return }
        // 1) 优先用能处理该文件类型的 App 打开
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, guessMime(f.name))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return
            }
        } catch (_: Exception) { }
        // 2) 没有可打开的应用 → 打开所在文件夹定位
        if (openContainingFolder(f)) return
        // 3) 兜底：复制路径，让用户自己去文件管理器粘贴定位
        copyToClipboard("path", f.absolutePath)
        toast(getString(R.string.opened_copy_path))
    }

    /** 用文件管理器打开文件所在目录；成功返回 true。 */
    private fun openContainingFolder(f: File): Boolean {
        val dir = f.parentFile ?: return false
        if (!dir.exists()) return false
        try {
            val duri = FileProvider.getUriForFile(this, "$packageName.fileprovider", dir)
            for (mime in arrayOf("resource/folder", "vnd.android.document/directory", "*/*")) {
                val i = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(duri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (i.resolveActivity(packageManager) != null) {
                    startActivity(i)
                    return true
                }
            }
        } catch (_: Exception) { }
        return false
    }

    private fun exportFile(t: DownloadTask) {
        val f = taskFile(t)
        if (f == null) { toast(getString(R.string.file_not_found)); return }
        if (!f.exists()) { toast(getString(R.string.file_not_ready)); return }
        pendingExport = f
        try {
            exportFileLauncher.launch(f.name)
        } catch (e: Exception) {
            toast(getString(R.string.export_failed) + (e.message ?: ""))
        }
    }

    // ---------------- 重命名 ----------------

    private fun promptRename(t: DownloadTask) {
        val f = taskFile(t)
        if (f == null || !f.exists()) { toast(getString(R.string.file_not_found)); return }
        if (!t.isComplete) { toast(getString(R.string.rename_only_complete)); return }
        val et = EditText(this).apply {
            setText(f.name)
            setSelection(f.name.length)
            hint = getString(R.string.rename_hint)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_title)
            .setView(et)
            .setPositiveButton(R.string.save) { _, _ -> doRename(f, et.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun doRename(f: File, newName: String) {
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\')) {
            toast(getString(R.string.rename_failed)); return
        }
        val dest = File(f.parentFile, newName)
        if (dest.absolutePath == f.absolutePath) return
        if (dest.exists()) { toast(getString(R.string.rename_exists)); return }
        val ok = try {
            f.renameTo(dest) || run { f.copyTo(dest, true); f.delete() }
        } catch (e: Exception) {
            false
        }
        if (ok) {
            Engine.recordRename(f.absolutePath, dest.absolutePath)
            toast(getString(R.string.renamed_ok, newName))
        } else {
            toast(getString(R.string.rename_failed))
        }
    }

    // ---------------- Tracker 管理 ----------------

    private fun showTrackers() {
        Engine.queryTrackers { current ->
            val et = EditText(this).apply {
                setText(current)
                setSelection(current.length)
                hint = "每行一个 tracker（udp:// 或 https:// 开头）；留空表示不额外注入"
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                minLines = 6
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_trackers)
                .setView(et)
                .setPositiveButton(R.string.save) { _, _ ->
                    val csv = et.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
                    Engine.applyTrackers(csv) { ok, msg ->
                        toast(if (ok) getString(R.string.trackers_saved) else "失败：$msg")
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun showDownloadDir() {
        val dir = prefs.downloadDir()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.download_dir_title)
            .setMessage(dir)
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.copy_path) { _, _ ->
                copyToClipboard("dir", dir)
                toast(getString(R.string.download_dir_copied))
            }
            .show()
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                    .launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_trackers -> { showTrackers(); true }
            R.id.action_download_dir -> { showDownloadDir(); true }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)); true
            }
            R.id.action_open_classic_ui -> {
                startActivity(Intent(this, MotrixWebActivity::class.java)); true
            }
            R.id.action_pause_all -> { Engine.pauseAll(); true }
            R.id.action_resume_all -> { Engine.resumeAll(); true }
            R.id.action_purge -> { Engine.purge(); true }
            R.id.action_notifications -> {
                startActivity(Intent(this, NotificationCenterActivity::class.java)); true
            }
            R.id.action_appearance -> { showAppearance(); true }
            R.id.action_language -> { showLanguage(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** 记录下载完成/失败到通知中心（每个 gid 只记一次）。 */
    private fun maybeNotify(list: List<DownloadTask>) {
        for (t in list) {
            if ((t.isComplete || t.isError) && notifiedGids.add(t.gid)) {
                NotificationStore.add(
                    this,
                    NotificationStore.Entry(
                        gid = t.gid,
                        name = t.name,
                        status = if (t.isComplete) getString(R.string.notif_completed) else getString(R.string.notif_failed),
                        error = t.errorMessage.ifBlank { null },
                        time = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun showAppearance() {
        val labels = arrayOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))
        val keys = arrayOf("system", "light", "dark")
        val cur = keys.indexOf(prefs.themeMode()).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.appearance_title)
            .setSingleChoiceItems(labels, cur) { d, w ->
                prefs.setThemeMode(keys[w])
                AppCompatDelegate.setDefaultNightMode(
                    when (keys[w]) {
                        "light" -> AppCompatDelegate.MODE_NIGHT_NO
                        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
                d.dismiss()
            }
            .show()
    }

    private fun showLanguage() {
        val labels = arrayOf(getString(R.string.lang_system), getString(R.string.lang_zh), getString(R.string.lang_en))
        val keys = arrayOf("system", "zh", "en")
        val cur = keys.indexOf(prefs.langMode()).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(labels, cur) { d, w ->
                prefs.setLangMode(keys[w])
                val tag = when (keys[w]) {
                    "zh" -> "zh-CN"
                    "en" -> "en"
                    else -> ""
                }
                AppCompatDelegate.setApplicationLocales(
                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(tag)
                )
                d.dismiss()
            }
            .show()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    // ---------------- 限速三档快捷切换（龟速 / 常速 / 高速，默认高速=不限速） ----------------

    private fun limitsFor(mode: String): Pair<Int, Int> = when (mode) {
        "turtle" -> 256 to 64        // 256 KB/s 下 / 64 KB/s 上
        "normal" -> 2048 to 512      // 2 MB/s 下 / 512 KB/s 上
        else -> 0 to 0               // 高速：不限速
    }

    private fun refreshSpeedButtons(mode: String) {
        val accent = ContextCompat.getColor(this, R.color.mtx_accent)
        val surface = ContextCompat.getColor(this, R.color.mtx_surface)
        val onColor = Color.WHITE
        val offColor = ContextCompat.getColor(this, R.color.mtx_text)
        for ((b, m) in listOf(btnTurtle to "turtle", btnNormal to "normal", btnHigh to "high")) {
            val on = m == mode
            b.backgroundTintList = ColorStateList.valueOf(if (on) accent else surface)
            b.setTextColor(if (on) onColor else offColor)
            b.strokeWidth = if (on) 0 else (1 * resources.displayMetrics.density).toInt()
        }
    }

    private fun setSpeedMode(mode: String) {
        prefs.setSpeedMode(mode)
        refreshSpeedButtons(mode)
        applySpeedModeQuiet(mode)
        toast(getString(R.string.speed_switched, modeLabel(mode)))
    }

    private fun applySpeedModeQuiet(mode: String) {
        val (dl, ul) = limitsFor(mode)
        Engine.applyGlobalLimit(dl, ul, prefs.loadSettings().maxConcurrent)
    }

    private fun modeLabel(mode: String): String = when (mode) {
        "turtle" -> getString(R.string.speed_turtle)
        "normal" -> getString(R.string.speed_normal)
        else -> getString(R.string.speed_high)
    }

    private fun toast(msg: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } else {
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        }
    }
}
