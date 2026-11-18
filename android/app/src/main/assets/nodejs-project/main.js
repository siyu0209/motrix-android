/**
 * Motrix Android — nodejs-mobile 引导入口（模式 B：端内全内嵌后端）
 * =====================================================================
 * 职责：
 *   1. 读取原生侧注入的环境变量（MOTRIX_PORT / MOTRIX_DOWNLOAD_DIR /
 *      MOTRIX_DATA_DIR / MOTRIX_SKIP_ELECTRON_REBUILD 等），规范化并写回 process.env；
 *   2. 解析一个「可写」的数据根目录，切换 process.cwd()，修正 TMPDIR/HOME/XDG_* 等
 *      nodejs-mobile 上常见的不可写默认值；
 *   3. 动态 import 真实的 Motrix 后端入口 ./dist-server/index.mjs（Fastify，监听
 *      127.0.0.1:<port>）；
 *   4. 端口占用时向上探测重试；启动完成后写 ready 握手文件 + 打日志，供 Android 侧
 *      判定「后端已就绪」并取到真实端口；
 *   5. 收到 SIGTERM/SIGINT 或原生调用时优雅退出，尽力关闭 server 并回收 aria2 子进程；
 *   6. 对 nodejs-mobile（内置 Node 版本较老 + Android 文件系统差异）做兼容垫片与
 *      原生模块（better-sqlite3）加载失败的降级提示。
 *
 * 说明：本文件只依赖 Node 内置模块，不做第三方依赖解析；dist-server 由构建脚本
 * stage 到同目录下（见 README.md）。所有端口/路径均来自环境变量，不硬编码。
 *
 * 约定给 Android 侧的握手接口（任选其一）：
 *   - 日志行：`[MOTRIX_READY] {"port":16800,...}`（可 grep logcat）
 *   - 握手文件：<MOTRIX_DATA_DIR>/motrix-ready.json
 *   - JS 全局：globalThis.motrixAndroid.getStatus() / .shutdown(reason)
 */

import fs from 'node:fs'
import path from 'node:path'
import net from 'node:net'
import http from 'node:http'
import os from 'node:os'
import cp from 'node:child_process'
import { fileURLToPath, pathToFileURL } from 'node:url'

// ---------------------------------------------------------------------------
// 0. 基础定位 & 日志
// ---------------------------------------------------------------------------
const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const TAG = '[motrix-android]'
function log(...args) {
  // 统一前缀，便于 logcat 过滤
  console.log(TAG, ...args)
}
function warn(...args) {
  console.warn(TAG, '[warn]', ...args)
}
function logError(...args) {
  console.error(TAG, '[error]', ...args)
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

// ---------------------------------------------------------------------------
// 1. 读取并规范化环境变量（全部走 env，绝不硬编码端口/路径）
// ---------------------------------------------------------------------------
function readInt(value, fallback) {
  const n = Number.parseInt(value, 10)
  return Number.isFinite(n) && n > 0 ? n : fallback
}

const HOST = process.env.MOTRIX_HOST || '127.0.0.1'
const PREFERRED_PORT = readInt(process.env.MOTRIX_PORT, 16800)
const PORT_SCAN_LIMIT = readInt(process.env.MOTRIX_PORT_SCAN_LIMIT, 50)
const MAX_START_ATTEMPTS = readInt(process.env.MOTRIX_START_ATTEMPTS, 5)
const READY_TIMEOUT_MS = readInt(process.env.MOTRIX_READY_TIMEOUT_MS, 30000)

// ---------------------------------------------------------------------------
// 2. 解析「可写」数据根目录（nodejs-mobile 上 cwd / tmp 往往不可写）
// ---------------------------------------------------------------------------
function isWritableDir(dir) {
  try {
    fs.mkdirSync(dir, { recursive: true })
    const probe = path.join(dir, `.wtest-${process.pid}-${Date.now()}`)
    fs.writeFileSync(probe, 'ok')
    fs.unlinkSync(probe)
    return true
  } catch {
    return false
  }
}

function resolveWritableDataDir() {
  // 候选顺序：原生注入的 MOTRIX_DATA_DIR → HOME → 系统临时目录下的 motrix 子目录
  const candidates = []
  if (process.env.MOTRIX_DATA_DIR && process.env.MOTRIX_DATA_DIR.trim()) {
    candidates.push(process.env.MOTRIX_DATA_DIR.trim())
  }
  if (process.env.HOME && process.env.HOME.trim()) {
    candidates.push(path.join(process.env.HOME.trim(), 'motrix'))
  }
  try {
    candidates.push(path.join(os.tmpdir(), 'motrix'))
  } catch {
    /* os.tmpdir 在个别环境可能抛错，忽略 */
  }
  for (const dir of candidates) {
    if (isWritableDir(dir)) return dir
  }
  // 最后兜底：进程当前目录（若也不可写，后续写入会再报错，但我们已尽力）
  return process.cwd()
}

const DATA_DIR = resolveWritableDataDir()
const TMP_DIR = path.join(DATA_DIR, 'tmp')
const LOG_DIR = path.join(DATA_DIR, 'logs')
const DOWNLOAD_DIR = (() => {
  const fromEnv = process.env.MOTRIX_DOWNLOAD_DIR && process.env.MOTRIX_DOWNLOAD_DIR.trim()
  const candidate = fromEnv || path.join(DATA_DIR, 'downloads')
  // 下载目录可能指向应用外部存储；若不可写则退回到数据根目录下（并在日志中告警）
  if (isWritableDir(candidate)) return candidate
  const fallback = path.join(DATA_DIR, 'downloads')
  isWritableDir(fallback)
  warn(`download dir not writable: ${candidate} -> fallback ${fallback}`)
  return fallback
})()

// 建齐必需目录
for (const d of [DATA_DIR, TMP_DIR, LOG_DIR, DOWNLOAD_DIR]) {
  try { fs.mkdirSync(d, { recursive: true }) } catch (e) { warn('mkdir failed', d, e.message) }
}

// 切换工作目录：nodejs-mobile 默认 cwd 常是 `/`（不可写），会导致上游写相对路径失败
try {
  process.chdir(DATA_DIR)
  log('cwd ->', process.cwd())
} catch (e) {
  warn('process.chdir failed:', e.message)
}

// 写回规范化后的 env，供 dist-server 读取
process.env.MOTRIX_DATA_DIR = DATA_DIR
process.env.MOTRIX_DOWNLOAD_DIR = DOWNLOAD_DIR
process.env.MOTRIX_HOST = HOST
process.env.MOTRIX_SKIP_ELECTRON_REBUILD = process.env.MOTRIX_SKIP_ELECTRON_REBUILD || '1'
process.env.NODE_ENV = process.env.NODE_ENV || 'production'

// 临时目录 & 用户目录垫片：os.tmpdir()/部分库在 Android 上会拿到不存在的 /tmp
process.env.TMPDIR = process.env.TMPDIR || TMP_DIR
process.env.TMP = process.env.TMP || TMP_DIR
process.env.TEMP = process.env.TEMP || TMP_DIR
if (!process.env.HOME) process.env.HOME = DATA_DIR
if (!process.env.XDG_CONFIG_HOME) process.env.XDG_CONFIG_HOME = path.join(DATA_DIR, 'config')
if (!process.env.XDG_CACHE_HOME) process.env.XDG_CACHE_HOME = path.join(DATA_DIR, 'cache')
if (!process.env.XDG_DATA_HOME) process.env.XDG_DATA_HOME = path.join(DATA_DIR, 'share')

// aria2 路径：由原生侧通过 MOTRIX_ARIA2_PATH 注入（jniLibs 里可执行的 libaria2c.so），
// 这里只做透传与提示，不覆盖。
if (process.env.MOTRIX_ARIA2_PATH) {
  log('aria2 path =', process.env.MOTRIX_ARIA2_PATH)
} else {
  warn('MOTRIX_ARIA2_PATH 未设置：若上游 start-engine 需要拉起 aria2c，请由原生侧注入该变量')
}

// ---------------------------------------------------------------------------
// 3. 端口探测（先找一个空闲端口，再让上游在这个端口监听，减少 EADDRINUSE 竞态）
// ---------------------------------------------------------------------------
function isPortFree(port, host) {
  return new Promise((resolve) => {
    const srv = net.createServer()
    srv.once('error', () => resolve(false))
    srv.once('listening', () => srv.close(() => resolve(true)))
    try {
      srv.listen(port, host)
    } catch {
      resolve(false)
    }
  })
}

async function findFreePort(startPort, host) {
  for (let i = 0; i < PORT_SCAN_LIMIT; i++) {
    const p = startPort + i
    // eslint-disable-next-line no-await-in-loop
    if (await isPortFree(p, host)) return p
  }
  throw new Error(`无可用的空闲端口：从 ${startPort} 起扫描 ${PORT_SCAN_LIMIT} 个均被占用`)
}

function isAddrInUseError(err) {
  const code = err && (err.code || (err.cause && err.cause.code))
  const msg = String((err && (err.message || err)) || '')
  return code === 'EADDRINUSE' || /EADDRINUSE|address already in use/i.test(msg)
}

// ---------------------------------------------------------------------------
// 4. HTTP 就绪探测（拿到任意 HTTP 状态即视为「端口已起服务」）
// ---------------------------------------------------------------------------
function httpProbe(port, host, probePath = '/') {
  return new Promise((resolve) => {
    const req = http.get({ host, port, path: probePath, timeout: 1500 }, (res) => {
      res.resume() // 丢弃 body，避免句柄悬挂
      resolve(true) // 任何状态码都说明服务已监听
    })
    req.on('timeout', () => { req.destroy(); resolve(false) })
    req.on('error', () => resolve(false))
  })
}

async function waitForHttpReady(port, host, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    // eslint-disable-next-line no-await-in-loop
    if (await httpProbe(port, host)) return true
    // eslint-disable-next-line no-await-in-loop
    await sleep(300)
  }
  return false
}

// ---------------------------------------------------------------------------
// 5. aria2 子进程追踪（尽力而为：拦截 child_process.spawn 记录 spawn 出来的子进程）
//    注意：只有通过默认命名空间 cp.spawn / require('child_process') 直接取属性的调用
//    才能被拦截；若上游是 `import { spawn } from 'node:child_process'` 的解构绑定，
//    拦截可能不生效 —— 此时依赖 dist-server 自身暴露的 shutdown 钩子（见下）。
// ---------------------------------------------------------------------------
const trackedChildren = new Set()
try {
  const origSpawn = cp.spawn
  cp.spawn = function patchedSpawn(...args) {
    const child = origSpawn.apply(this, args)
    try {
      if (child && typeof child.once === 'function') {
        trackedChildren.add(child)
        child.once('exit', () => trackedChildren.delete(child))
      }
    } catch { /* ignore */ }
    return child
  }
} catch (e) {
  warn('spawn patch failed:', e.message)
}

function killTrackedChildren() {
  for (const child of trackedChildren) {
    try {
      if (child && !child.killed && child.pid) {
        log('killing child pid', child.pid)
        child.kill('SIGTERM')
        // 给 500ms 优雅退出的窗口，再补 SIGKILL
        setTimeout(() => { try { child.kill('SIGKILL') } catch { /* ignore */ } }, 500).unref?.()
      }
    } catch { /* ignore */ }
  }
  trackedChildren.clear()
}

// ---------------------------------------------------------------------------
// 6. 启动 / 关闭
// ---------------------------------------------------------------------------
const SERVER_ENTRY = path.join(__dirname, 'dist-server', 'index.mjs')
let importedModule = null
let readyPort = 0
let isReady = false
let shuttingDown = false

function writeReadyMarker(port) {
  const payload = {
    ready: true,
    host: HOST,
    port,
    pid: process.pid,
    cwd: process.cwd(),
    dataDir: DATA_DIR,
    downloadDir: DOWNLOAD_DIR,
    nodeVersion: process.version,
    startedAt: new Date().toISOString(),
  }
  try {
    fs.writeFileSync(path.join(DATA_DIR, 'motrix-ready.json'), JSON.stringify(payload, null, 2))
  } catch (e) {
    warn('write ready marker failed:', e.message)
  }
  // 机器可读单行标记（供 logcat grep 判定 ready + 取真实端口）
  console.log(`[MOTRIX_READY] ${JSON.stringify(payload)}`)
  log(`服务就绪 ✓ http://${HOST}:${port}/  (data=${DATA_DIR}, dl=${DOWNLOAD_DIR})`)
}

function detectNativeModuleFailure(err) {
  const text = String((err && (err.message || err.stack || err)) || '')
  return /better-sqlite3|\.node\b|invalid ELF|not a valid Win32|dlopen|NODE_MODULE_VERSION|was compiled against a different Node\.js version|cannot open shared object/i.test(text)
}

async function startWithRetry() {
  if (!fs.existsSync(SERVER_ENTRY)) {
    throw new Error(`找不到后端入口：${SERVER_ENTRY}（应在构建阶段把 dist/server stage 到 dist-server/）`)
  }
  let port = PREFERRED_PORT
  let lastErr = null
  for (let attempt = 1; attempt <= MAX_START_ATTEMPTS; attempt++) {
    // 先找一个空闲端口，再让上游监听，尽量规避 EADDRINUSE
    port = await findFreePort(port, HOST)
    process.env.MOTRIX_PORT = String(port)
    try {
      log(`import server entry on ${HOST}:${port} (attempt ${attempt}/${MAX_START_ATTEMPTS})`)
      // 动态导入真实后端入口（ESM）。Node 在评估失败时会将其从模块缓存移除，可安全重试。
      importedModule = await import(pathToFileURL(SERVER_ENTRY).href)
      // 入口 import resolve 后，再轮询 HTTP 确认真正在监听
      const ok = await waitForHttpReady(port, HOST, READY_TIMEOUT_MS)
      if (!ok) {
        warn(`服务 import 已完成，但 ${READY_TIMEOUT_MS}ms 内 HTTP 未就绪（可能仍在初始化，如 sqlite/aria2）`)
      }
      readyPort = port
      isReady = true
      writeReadyMarker(port)
      return
    } catch (err) {
      lastErr = err
      if (isAddrInUseError(err)) {
        warn(`端口 ${port} 在监听阶段被占用，尝试下一个端口`)
        port = port + 1
        continue
      }
      // 原生模块（better-sqlite3 等）ABI 不匹配：给出可执行的降级建议后抛出
      if (detectNativeModuleFailure(err)) {
        logError('原生模块加载失败（很可能是 better-sqlite3 与内置 Node ABI 不匹配）：', err && err.message)
        logError('降级路线：① 设 MOTRIX_SQLITE_DRIVER=memory 走纯JS/内存库；② 设 MOTRIX_REMOTE=1 切远程模式 A；③ 用 NDK 为内置 Node 版本重编 better-sqlite3')
      }
      throw err
    }
  }
  throw lastErr || new Error('多次重试后仍无法启动后端服务')
}

async function shutdown(reason = 'unknown') {
  if (shuttingDown) return
  shuttingDown = true
  log('shutdown requested:', reason)
  try {
    // 1) 调用上游 dist-server 暴露的关闭钩子（若存在）
    const mod = importedModule || {}
    const fastifyLike = mod.server || mod.app || mod.default || null
    const hooks = [
      mod.shutdown,
      mod.stop,
      mod.close,
      mod.default && mod.default.shutdown,
      mod.default && mod.default.close,
      fastifyLike && fastifyLike.close,
    ]
    for (const fn of hooks) {
      if (typeof fn === 'function') {
        try {
          // eslint-disable-next-line no-await-in-loop
          await fn.call(fastifyLike || mod)
          log('server shutdown hook executed')
          break
        } catch (e) {
          warn('server shutdown hook error:', e.message)
        }
      }
    }
  } catch (e) {
    warn('shutdown step1 error:', e.message)
  }

  // 2) 回收我们追踪到的子进程（aria2 等）
  try { killTrackedChildren() } catch { /* ignore */ }

  // 3) 清理 ready 握手文件
  try { fs.unlinkSync(path.join(DATA_DIR, 'motrix-ready.json')) } catch { /* ignore */ }

  // 4) 给一点时间让句柄释放，然后退出
  setTimeout(() => process.exit(0), 150).unref?.()
  setTimeout(() => process.exit(0), 2000).unref?.()
}

// ---------------------------------------------------------------------------
// 7. 进程级错误兜底 & 信号处理
// ---------------------------------------------------------------------------
process.on('uncaughtException', (err) => {
  logError('uncaughtException:', err && (err.stack || err.message || err))
  // 不立即退出：尽量避免 WebView 侧因后端进程崩溃而整体不可用；保留进程可观测
})
process.on('unhandledRejection', (reason) => {
  logError('unhandledRejection:', (reason && (reason.stack || reason.message)) || reason)
})
process.on('SIGTERM', () => { shutdown('SIGTERM') })
process.on('SIGINT', () => { shutdown('SIGINT') })
process.on('SIGHUP', () => { shutdown('SIGHUP') })
process.on('exit', () => {
  try { killTrackedChildren() } catch { /* ignore */ }
  // 兜底：进程被硬退出（未走 shutdown）时也移除 ready 文件，避免残留误导 Android 侧
  try { fs.unlinkSync(path.join(DATA_DIR, 'motrix-ready.json')) } catch { /* ignore */ }
})

// ---------------------------------------------------------------------------
// 8. 暴露给原生侧（nodejs-mobile 可通过求值 JS 调用）的全局接口
// ---------------------------------------------------------------------------
globalThis.motrixAndroid = {
  getStatus() {
    return {
      ready: isReady,
      host: HOST,
      port: readyPort,
      pid: process.pid,
      dataDir: DATA_DIR,
      downloadDir: DOWNLOAD_DIR,
      nodeVersion: process.version,
    }
  },
  shutdown(reason) {
    return shutdown(reason || 'native-call')
  },
}

// ---------------------------------------------------------------------------
// 9. 主流程
// ---------------------------------------------------------------------------
;(async () => {
  log(`starting: node=${process.version} host=${HOST} preferredPort=${PREFERRED_PORT}`)
  log(`dirs: data=${DATA_DIR} tmp=${TMP_DIR} download=${DOWNLOAD_DIR}`)
  // 清理上一次可能残留的就绪文件，避免 Android 侧误判为已就绪
  try { fs.unlinkSync(path.join(DATA_DIR, 'motrix-ready.json')) } catch { /* ignore */ }
  try {
    await startWithRetry()
  } catch (err) {
    logError('启动失败:', err && (err.stack || err.message || err))
    // 启动失败也写一个可读的失败握手，Android 侧可据此切远程模式 A
    try {
      fs.writeFileSync(
        path.join(DATA_DIR, 'motrix-ready.json'),
        JSON.stringify({ ready: false, error: String((err && err.message) || err), at: new Date().toISOString() }, null, 2),
      )
    } catch { /* ignore */ }
    console.log(`[MOTRIX_FAILED] ${String((err && err.message) || err)}`)
    // 保留进程存活以便原生侧读取全局状态/日志；如需硬退出可改为 process.exit(1)
  }
})()
