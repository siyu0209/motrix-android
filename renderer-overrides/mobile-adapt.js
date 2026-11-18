/* =============================================================================
 * Motrix Android — 移动端运行时增强 (mobile-adapt.js)
 * -----------------------------------------------------------------------------
 * 纯原生 JS，无依赖，幂等（重复注入只安装一次）。
 * 职责：
 *   1) 注入 / 校正 viewport meta（width=device-width, viewport-fit=cover ...）
 *   2) 给根节点打移动端识别 class（mtx-mobile / mtx-portrait / mtx-landscape / mtx-narrow）
 *   3) 监听软键盘（visualViewport），写 --mtx-keyboard-height 并切 .mtx-keyboard-open
 *   4) 安卓返回键 / history 处理：优先关弹窗 → 再转发 mtx:back 事件 → 都不处理才允许退出
 *   5) 必要的 DOM 小补丁：外壳/滚动区/弹窗/底栏打 data-mtx-* 与 class，测量底栏高度
 * 所有副作用都可关，且不改变业务逻辑与 DOM 语义。
 *
 * 与 Android 端约定（可选）：
 *   window.MotrixAndroid.onBack()   —— 返回键无人接管时回调宿主
 *   window.MotrixAndroid.exitApp()  —— 可选，显式请求退出
 *   页面监听 document 上的 'mtx:back' 事件（可 preventDefault 表示已处理）
 * ========================================================================== */
(function () {
  'use strict';

  var KEY = '__MTX_MOBILE_ADAPT__';
  // 幂等保护：已安装则直接返回
  if (window[KEY] && window[KEY].installed) return;

  var CFG = Object.assign({
    injectViewport: true,   // 是否注入/校正 viewport meta
    backGuard: true,        // 是否接管返回键（history 哨兵）
    addClasses: true,       // 是否给根节点加 mtx-mobile 等 class
    keyboard: true,         // 是否监听软键盘
    bottomBarVar: true,     // 是否测量底栏并写 --mtx-bottom-bar
    narrowBreakpoint: 600,  // 窄屏阈值(px)，与 CSS @media 599.98 对齐
    keyboardThreshold: 120  // 判定键盘弹起的最小高度差(px)
  }, window.MTX_MOBILE_CONFIG || {});

  var root = document.documentElement;
  var doc = document;
  var raf = window.requestAnimationFrame || function (f) { return setTimeout(f, 16); };

  /* ---------------------------------------------------------------- 1. viewport */
  function ensureViewport() {
    if (!CFG.injectViewport) return;
    var wanted = 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover';
    var meta = doc.querySelector('meta[name="viewport"]');
    if (!meta) {
      meta = doc.createElement('meta');
      meta.setAttribute('name', 'viewport');
      (doc.head || root).appendChild(meta);
      meta.setAttribute('content', wanted);
      return;
    }
    // 已有 viewport：补齐 viewport-fit=cover，其余保留原值（不粗暴覆盖作者的设置）
    var c = meta.getAttribute('content') || '';
    if (c.indexOf('viewport-fit') === -1) {
      meta.setAttribute('content', c ? (c + ', viewport-fit=cover') : wanted);
    }
    if (c.indexOf('width=') === -1) {
      meta.setAttribute('content', 'width=device-width, ' + meta.getAttribute('content'));
    }
  }

  /* ------------------------------------------------------- 2. 根 class / 尺寸变量 */
  function setClass(name, on) {
    if (!CFG.addClasses) return;
    root.classList.toggle(name, !!on);
  }

  function updateMetrics() {
    var w = window.innerWidth || root.clientWidth || 0;
    var h = window.innerHeight || root.clientHeight || 0;
    setClass('mtx-mobile', true);
    setClass('mtx-portrait', h >= w);
    setClass('mtx-landscape', w > h);
    setClass('mtx-narrow', w < CFG.narrowBreakpoint);
    root.style.setProperty('--mtx-vh', h + 'px');
    // 供样式层按可视宽做更细判断（可选）
    root.style.setProperty('--mtx-vw', w + 'px');
  }

  var resizeScheduled = false;
  function onResize() {
    if (resizeScheduled) return;
    resizeScheduled = true;
    raf(function () {
      resizeScheduled = false;
      updateMetrics();
      measureBottomBar();
    });
  }

  /* ----------------------------------------------------------- 3. 软键盘监听 */
  function installKeyboard() {
    if (!CFG.keyboard) return;
    var vv = window.visualViewport;
    if (!vv) return;

    function apply() {
      // 键盘高度 ≈ 布局视口高 - 可视视口高（再减去页面滚动偏移）
      var kb = Math.max(0, (window.innerHeight - vv.height - vv.offsetTop));
      var open = kb > CFG.keyboardThreshold;
      root.classList.toggle('mtx-keyboard-open', open);
      root.style.setProperty('--mtx-keyboard-height', Math.round(kb) + 'px');
    }
    vv.addEventListener('resize', apply);
    vv.addEventListener('scroll', apply);
    apply();

    // 聚焦输入框时，键盘弹起后把元素滚到可视区中央，避免被键盘压住
    doc.addEventListener('focusin', function (e) {
      var t = e.target;
      if (!t || !/^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName)) return;
      setTimeout(function () {
        try {
          if (root.classList.contains('mtx-keyboard-open')) {
            t.scrollIntoView({ block: 'center', behavior: 'smooth' });
          }
        } catch (err) { /* 老 WebView 不支持 options 时忽略 */ }
      }, 300);
    }, true);
  }

  /* ------------------------------------------------------ 4. 返回键 / history */
  var guardPushed = false;

  function overlaySelectors() {
    return [
      '[role="dialog"]',
      'dialog[open]',
      '[data-mtx-dialog]',
      '.mtx-dialog',
      '[aria-modal="true"]'
    ].join(',');
  }

  function findOpenOverlay() {
    var list;
    try { list = doc.querySelectorAll(overlaySelectors()); } catch (e) { return null; }
    for (var i = list.length - 1; i >= 0; i--) {
      var el = list[i];
      if (el.hasAttribute('hidden')) continue;
      var st = window.getComputedStyle(el);
      if (st.display === 'none' || st.visibility === 'hidden' || st.opacity === '0') continue;
      return el;
    }
    return null;
  }

  function closeOverlay(el) {
    if (!el) return false;
    // 1) 原生 <dialog>
    if (el.tagName === 'DIALOG' && typeof el.close === 'function') {
      try { el.close(); return true; } catch (e) {}
    }
    // 2) 找关闭按钮点它（最不破坏 React 状态的方式）
    var btn = el.querySelector('[data-mtx-close],[aria-label*="close" i],[aria-label*="关闭"],.close,.mtx-close');
    if (btn) { try { btn.click(); return true; } catch (e) {} }
    // 3) 抛事件让业务自行关闭
    var ev = new CustomEvent('mtx:close-overlay', { cancelable: true, detail: { target: el } });
    doc.dispatchEvent(ev);
    return ev.defaultPrevented;
  }

  function armGuard() {
    try {
      history.pushState({ mtxGuard: true }, '');
      guardPushed = true;
    } catch (e) { /* 某些 WebView 限制 pushState，忽略 */ }
  }

  function onPopState() {
    // 优先关闭打开的弹窗
    var ov = findOpenOverlay();
    if (ov) {
      closeOverlay(ov);
      armGuard();          // 重新布哨兵，继续拦下一层
      return;
    }
    // 其次询问页面是否处理（cancelable 事件）
    var ev = new CustomEvent('mtx:back', { cancelable: true, detail: {} });
    var notCancelled = doc.dispatchEvent(ev);
    var handled = (!notCancelled) || ev.defaultPrevented;
    if (handled) { armGuard(); return; }

    // 再次交给宿主
    if (window.MotrixAndroid && typeof window.MotrixAndroid.onBack === 'function') {
      try { window.MotrixAndroid.onBack(); armGuard(); return; } catch (e) {}
    }
    // 都没人处理：不再布哨兵，让 WebView/宿主走默认退出
    guardPushed = false;
  }

  function installBackGuard() {
    if (!CFG.backGuard) return;
    window.addEventListener('popstate', onPopState);
    armGuard(); // 载入即布一层哨兵，保证第一次返回不秒退
  }

  /* ---------------------------------------------------------- 5. DOM 小补丁 */
  function hint(el, attr) {
    if (el && el.setAttribute && !el.hasAttribute(attr)) {
      try { el.setAttribute(attr, ''); } catch (e) {}
    }
  }

  function markShell() {
    // 应用外壳：通常 #root / #app / body>div 的第一个子节点
    var shell = doc.querySelector('#root, #app');
    if (shell) {
      shell.classList.add('mtx-app-shell');
      hint(shell, 'data-mtx-app-shell');
      // 外壳内若有 main 区，标记为滚动区
      var main = shell.querySelector('main');
      if (main) { main.classList.add('mtx-scroll'); hint(main, 'data-mtx-scroll'); }
    }
  }

  function markDialogs() {
    var sel = '[role="dialog"],dialog,.mtx-dialog,[aria-modal="true"]';
    var list;
    try { list = doc.querySelectorAll(sel); } catch (e) { return; }
    for (var i = 0; i < list.length; i++) {
      list[i].classList.add('mtx-dialog');
      hint(list[i], 'data-mtx-dialog');
    }
  }

  var lastBottomBarH = -1;
  function measureBottomBar() {
    if (!CFG.bottomBarVar) return;
    var bar = doc.querySelector('[data-mtx-bottom-bar], .mtx-bottom-bar');
    var h = bar ? Math.round(bar.getBoundingClientRect().height) : 0;
    if (h !== lastBottomBarH) {
      lastBottomBarH = h;
      root.style.setProperty('--mtx-bottom-bar', h + 'px');
    }
  }

  function patch() {
    markShell();
    markDialogs();
    measureBottomBar();
  }

  function installObserver() {
    if (!window.MutationObserver) return;
    var mo = new MutationObserver(function (mutations) {
      // 只关心新增节点（弹窗/底栏/外壳动态出现），合并到下一帧处理
      for (var i = 0; i < mutations.length; i++) {
        if (mutations[i].addedNodes && mutations[i].addedNodes.length) {
          raf(patch);
          return;
        }
      }
    });
    mo.observe(doc.body || root, { childList: true, subtree: true });
  }

  /* ------------------------------------------------------------- 公开 API */
  function enable() {
    if (CFG.addClasses) root.classList.add('mtx-mobile');
    updateMetrics();
  }
  function disable() {
    root.classList.remove('mtx-mobile', 'mtx-portrait', 'mtx-landscape', 'mtx-narrow', 'mtx-keyboard-open');
  }
  function setTheme(mode) {
    root.classList.remove('mtx-dark', 'mtx-light');
    if (mode === 'dark') root.classList.add('mtx-dark');
    else if (mode === 'light') root.classList.add('mtx-light');
  }

  /* --------------------------------------------------------------- 初始化 */
  function init() {
    ensureViewport();
    enable();
    installKeyboard();
    patch();            // 首次补丁
    installObserver();
    installBackGuard();
    window.addEventListener('resize', onResize, { passive: true });
    window.addEventListener('orientationchange', onResize, { passive: true });
    // 记录安装状态
    window[KEY] = { installed: true, version: '1.0.0', enable: enable, disable: disable, setTheme: setTheme, config: CFG };
  }

  if (doc.readyState === 'loading') {
    doc.addEventListener('DOMContentLoaded', init, { once: true });
    // 即便 DOM 未就绪，也先尽快加根 class，避免首帧闪桌面布局
    try { root.classList.add('mtx-mobile'); } catch (e) {}
  } else {
    init();
  }
})();
