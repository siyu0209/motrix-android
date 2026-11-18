/**
 * Motrix Android — 后端端口健康检查（可选工具）
 * =====================================================================
 * 用途：探测 127.0.0.1:<port> 上的 Motrix 后端是否已就绪。两种用法：
 *   1) 命令行：node healthcheck.js            （读 MOTRIX_HOST / MOTRIX_PORT / MOTRIX_HEALTH_PATH）
 *      退出码：0=就绪  1=连接失败  2=超时
 *   2) 作为模块：import { checkHealth } from './healthcheck.js'
 *      const ok = await checkHealth({ port: 16800 })
 *
 * 判定口径：能建立 HTTP 连接并拿到任意 HTTP 状态码即视为「服务已监听」；不校验业务响应体，
 * 因为 Motrix 后端不同版本的根路径/健康路径可能不同。
 */

import http from 'node:http'
import { pathToFileURL } from 'node:url'

export function checkHealth(options = {}) {
  const host = options.host || process.env.MOTRIX_HOST || '127.0.0.1'
  const port = Number.parseInt(options.port || process.env.MOTRIX_PORT || '16800', 10)
  const probePath = options.path || process.env.MOTRIX_HEALTH_PATH || '/'
  const timeoutMs = Number.parseInt(options.timeoutMs || process.env.MOTRIX_HEALTH_TIMEOUT_MS || '3000', 10)

  return new Promise((resolve) => {
    let settled = false
    const done = (result) => {
      if (settled) return
      settled = true
      resolve(result)
    }

    const req = http.get({ host, port, path: probePath, timeout: timeoutMs }, (res) => {
      res.resume() // 丢弃响应体
      res.on('end', () => done({ ok: true, statusCode: res.statusCode, host, port, path: probePath }))
      res.on('error', () => done({ ok: true, statusCode: res.statusCode, host, port, path: probePath }))
    })

    req.on('timeout', () => {
      req.destroy()
      done({ ok: false, reason: 'timeout', host, port, path: probePath, timeoutMs })
    })
    req.on('error', (err) => {
      done({ ok: false, reason: err.code || err.message, host, port, path: probePath })
    })
  })
}

// ---- CLI 入口（仅当被直接执行时）----
async function runCli() {
  const host = process.env.MOTRIX_HOST || '127.0.0.1'
  const port = Number.parseInt(process.env.MOTRIX_PORT || '16800', 10)
  const probePath = process.env.MOTRIX_HEALTH_PATH || '/'
  const result = await checkHealth({ host, port, path: probePath })
  if (result.ok) {
    console.log(`OK ${result.statusCode} http://${host}:${port}${probePath}`)
    process.exit(0)
  } else {
    console.error(`FAIL ${result.reason} http://${host}:${port}${probePath}`)
    process.exit(result.reason === 'timeout' ? 2 : 1)
  }
}

const isMain = (() => {
  try {
    return !!process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url
  } catch {
    return false
  }
})()

if (isMain) {
  runCli()
}
