// opencomputer-agent-runtime:api
var DOCUMENT_MEMORY_TOOLS = Object.freeze([
  "save",
  "read",
  "list"
]);
var MANIFEST_RESULT_TOOL = null;
function hooks() {
  const value = globalThis[/* @__PURE__ */ Symbol.for("opencomputer.agent-hooks")];
  if (!value) throw new Error("OpenComputer hooks can only run while rendering an agent");
  return value;
}
function id(value, kind) {
  const normalized = String(value).trim();
  if (!normalized) throw new Error(kind + " requires a non-empty id");
  return normalized;
}
var defineTool = (input) => {
  const toolId = id(input.name, "defineTool");
  if (!/^[a-zA-Z0-9_-]+$/.test(toolId)) throw new Error("Invalid tool id " + JSON.stringify(toolId));
  if (!String(input.description).trim()) throw new Error("defineTool requires a non-empty description");
  if (input.input && typeof input.input !== "object") throw new Error("defineTool input must be a JSON Schema object");
  if (input.output && typeof input.output !== "object") throw new Error("defineTool output must be a JSON Schema object");
  const gated = typeof input.preview === "function" || typeof input.apply === "function";
  if (!gated) {
    if (typeof input.run !== "function") throw new Error("defineTool requires run(), or preview() and apply() for a tool that waits for approval");
    if (input.result !== void 0 && typeof input.result !== "boolean") throw new Error("defineTool result must be true or false");
    if (input.result === true && !input.output) throw new Error("defineTool result tools require an output schema; the host validates every result against it before committing");
    if (input.result === true && toolId !== MANIFEST_RESULT_TOOL) throw new Error("Tool " + toolId + " declares result: true, but the deployment records " + (MANIFEST_RESULT_TOOL ? MANIFEST_RESULT_TOOL + " as its result tool" : "no result tool") + "; the declaration was not readable at build time. Write result: true as a literal property of defineTool() and rebuild");
    if (toolId === MANIFEST_RESULT_TOOL && input.result !== true) throw new Error("Tool " + toolId + " is the deployment's result tool, but the module does not declare result: true; the build and the module disagree, rebuild the deployment");
    const { result, ...definition } = input;
    return Object.freeze({ kind: "tool", version: 1, ...definition, ...result === true ? { result: true } : {}, id: toolId, name: toolId });
  }
  if ("result" in input) throw new Error("A tool that waits for approval cannot be the result tool");
  if (typeof input.preview !== "function") throw new Error("A tool with apply() also requires preview()");
  if (typeof input.apply !== "function") throw new Error("A tool with preview() also requires apply()");
  if (typeof input.run === "function") throw new Error("A tool has either run(), or preview() and apply() - not both; run() is written for you when the tool waits for approval");
  return Object.freeze({
    kind: "gated-tool",
    version: 1,
    ...input,
    id: toolId,
    name: toolId,
    async run(context) {
      const preview = approvalPreview(await input.preview(context), toolId);
      const result = await publishApproval(toolId, {
        input: context.input,
        preview,
        idempotencyKey: context.messageId
      });
      return result.message;
    }
  });
};
var approvalPreview = (value, toolId) => {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("Tool " + toolId + " preview must return an object");
  if (typeof value.title !== "string" || !value.title.trim()) throw new Error("Tool " + toolId + " preview requires a non-empty title");
  if (value.summary !== void 0 && typeof value.summary !== "string") throw new Error("Tool " + toolId + " preview summary must be a string");
  if (value.facts !== void 0) {
    if (!Array.isArray(value.facts)) throw new Error("Tool " + toolId + " preview facts must be an array");
    if (value.facts.length > 20) throw new Error("Tool " + toolId + " preview may carry at most 20 facts");
    for (const fact of value.facts) {
      if (!fact || typeof fact.label !== "string" || typeof fact.value !== "string") throw new Error("Tool " + toolId + " preview facts must each have a label and a value");
    }
  }
  return value;
};
var publishApproval = async (tool, input) => {
  const toolId = id(typeof tool === "string" ? tool : tool.id, "publishApproval");
  const idempotencyKey = String(input.idempotencyKey).trim();
  if (!idempotencyKey || idempotencyKey.length > 256) throw new Error("Approval idempotency keys must contain 1 to 256 characters");
  const base = globalThis.process?.env?.OPENCOMPUTER_APPROVAL_URL;
  const token = globalThis.process?.env?.OPENCOMPUTER_APPROVAL_TOKEN;
  if (!base || !token) throw new Error("OpenComputer approvals are unavailable");
  const response = await fetch(base.replace(/\/$/, ""), {
    method: "POST",
    headers: { authorization: "Bearer " + token, "content-type": "application/json" },
    body: JSON.stringify({ toolId, input: input.input, preview: input.preview, idempotencyKey })
  });
  if (!response.ok) {
    const detail = await response.text().catch(() => "");
    let message = "";
    try {
      message = JSON.parse(detail)?.error?.message ?? "";
    } catch {
    }
    throw new Error(message || "Recording the approval failed with status " + response.status);
  }
  return await response.json();
};
var useInput = () => hooks().useInput();
var useModel = (model) => hooks().useModel(model);
var useTool = (tool) => hooks().useTool(tool);

// tools/scene.ts
import { copyFile, mkdir as mkdir2 } from "node:fs/promises";
import { join as join2 } from "node:path";

// tools/renderer.ts
import { execFile } from "node:child_process";
import { existsSync } from "node:fs";
import { mkdir, writeFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
var WORK = join(homedir(), ".shipvideo");
var WORKSPACE = "/workspace";
var MARKER = join(WORK, ".ready");
var WIDTH = 1920;
var HEIGHT = 1080;
function run(command, options = {}) {
  return new Promise((resolve) => {
    execFile(
      "/bin/bash",
      ["-lc", command],
      {
        cwd: options.cwd ?? WORK,
        env: { ...process.env, NODE_ENV: "development", npm_config_include: "dev", CI: "1" },
        timeout: options.timeoutMs ?? 3e5,
        maxBuffer: 16e6,
        signal: options.signal
      },
      (error, stdout, stderr) => {
        const err = error;
        resolve({ exitCode: typeof err?.code === "number" ? err.code : err ? 1 : 0, stdout: String(stdout ?? ""), stderr: String(stderr ?? "") });
      }
    );
  });
}
var SYSTEM_LIBS = "nss nspr atk at-spi2-atk at-spi2-core cups-libs libdrm libxkbcommon libXcomposite libXdamage libXrandr libXfixes libXext libX11 libxcb mesa-libgbm pango cairo alsa-lib libxshmfence expat google-noto-sans-fonts google-noto-emoji-fonts dejavu-sans-fonts liberation-fonts";
var bootstrapped = null;
function bootstrap(progress) {
  bootstrapped ??= (async () => {
    if (existsSync(MARKER)) return;
    await mkdir(WORK, { recursive: true });
    progress?.("installing renderer (chromium + ffmpeg)");
    const [npm, libs] = await Promise.all([
      run("npm init -y >/dev/null 2>&1; npm i playwright-core@1 ffmpeg-static --no-audit --no-fund 2>&1 | tail -2 && npx playwright-core install chromium-headless-shell 2>&1 | tail -1", { timeoutMs: 6e5 }),
      run(`command -v dnf >/dev/null && dnf install -y -q ${SYSTEM_LIBS} >/dev/null 2>&1; echo dnf=$?`, { timeoutMs: 6e5 })
    ]);
    if (npm.exitCode !== 0) throw new Error(`renderer install failed: ${npm.stdout.slice(-800)} ${npm.stderr.slice(-800)}`);
    await writeFile(MARKER, `${(/* @__PURE__ */ new Date()).toISOString()} ${libs.stdout.trim()}
`);
  })().catch((error) => {
    bootstrapped = null;
    throw error;
  });
  return bootstrapped;
}
async function load(pkg) {
  const { createRequire } = await import("node:module");
  const req = createRequire(join(WORK, "package.json"));
  const mod = req(pkg);
  const isEsmShim = mod && typeof mod === "object" && mod.__esModule && "default" in mod && Object.keys(mod).length === 1;
  return isEsmShim ? mod.default : mod;
}
var VIRTUAL_CLOCK = `(() => {
  const EPOCH = Date.now(); let now = 0; let rafs = new Map(); let rafId = 0; const timers = new Map(); let timerId = 0;
  const RealDate = Date;
  performance.now = () => now;
  const FakeDate = function (...args) { return args.length ? new RealDate(...args) : new RealDate(EPOCH + now); };
  FakeDate.now = () => EPOCH + now; FakeDate.UTC = RealDate.UTC; FakeDate.parse = RealDate.parse; FakeDate.prototype = RealDate.prototype;
  window.Date = FakeDate;
  window.requestAnimationFrame = (cb) => { rafs.set(++rafId, cb); return rafId; };
  window.cancelAnimationFrame = (id) => { rafs.delete(id); };
  window.setTimeout = (cb, ms = 0, ...args) => { const id = ++timerId; timers.set(id, { at: now + Math.max(0, +ms || 0), cb, args, every: null }); return id; };
  window.setInterval = (cb, ms = 0, ...args) => { const id = ++timerId; const every = Math.max(1, +ms || 1); timers.set(id, { at: now + every, cb, args, every }); return id; };
  window.clearTimeout = window.clearInterval = (id) => { timers.delete(id); };
  const seen = new WeakMap();
  const step = 1000 / 60;
  window.__seek = (t) => {
    while (now < t - 1e-6) {
      now = Math.min(t, now + step);
      for (;;) {
        let next = null;
        for (const [id, tm] of timers) if (tm.at <= now + 1e-6 && (!next || tm.at < next[1].at)) next = [id, tm];
        if (!next) break;
        const [id, tm] = next;
        if (tm.every) tm.at += tm.every; else timers.delete(id);
        try { typeof tm.cb === "function" ? tm.cb(...tm.args) : (0, eval)(String(tm.cb)); } catch (e) { console.error("timer callback threw: " + (e && e.message)); }
      }
      const cbs = [...rafs.values()]; rafs = new Map();
      for (const cb of cbs) { try { cb(now); } catch (e) { console.error("requestAnimationFrame callback threw: " + (e && e.message)); } }
    }
    for (const a of document.getAnimations({ subtree: true })) {
      try { let s = seen.get(a); if (s === undefined) { s = now; seen.set(a, s); } a.pause(); a.currentTime = Math.max(0, now - s); } catch (e) {}
    }
    return now;
  };
})();`;
var browser = null;
async function getBrowser() {
  if (browser) return browser;
  const { chromium } = await load("playwright-core");
  browser = await chromium.launch({
    args: ["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage", "--font-render-hinting=none", "--hide-scrollbars", "--force-color-profile=srgb"]
  });
  return browser;
}
async function loadScene(html) {
  const b = await getBrowser();
  const context = await b.newContext({ viewport: { width: WIDTH, height: HEIGHT }, deviceScaleFactor: 1, colorScheme: "light" });
  await context.addInitScript(VIRTUAL_CLOCK);
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", (e) => errors.push(`pageerror: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error" || m.type() === "warning") errors.push(`console.${m.type()}: ${m.text()}`);
  });
  page.on("requestfailed", (r) => errors.push(`request failed: ${r.url()} ${r.failure()?.errorText ?? ""}`));
  await page.setContent(html, { waitUntil: "load", timeout: 3e4 });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForLoadState("networkidle", { timeout: 1e4 }).catch(() => void 0);
  await page.evaluate(() => window.__seek(0));
  return {
    page,
    errors,
    close: async () => {
      await context.close();
    }
  };
}
async function renderMp4(input) {
  const { spawn } = await import("node:child_process");
  const ffmpegPath = await load("ffmpeg-static");
  if (typeof ffmpegPath !== "string") throw new Error("ffmpeg-static did not resolve to a binary path");
  const scene = await loadScene(input.html);
  const total = Math.round(input.durationSeconds * input.fps);
  const started = Date.now();
  const ff = spawn(ffmpegPath, [
    "-y",
    "-loglevel",
    "error",
    "-f",
    "image2pipe",
    "-framerate",
    String(input.fps),
    "-i",
    "-",
    "-c:v",
    "libx264",
    "-preset",
    "medium",
    "-crf",
    "18",
    "-pix_fmt",
    "yuv420p",
    "-movflags",
    "+faststart",
    input.outPath
  ]);
  let ffErr = "";
  ff.stderr.on("data", (d) => {
    ffErr += String(d);
  });
  const closed = new Promise((resolve) => ff.on("close", (code2) => resolve(code2 ?? 1)));
  try {
    for (let i = 0; i < total; i++) {
      if (input.signal?.aborted) throw new Error("render aborted");
      await scene.page.evaluate((t) => window.__seek(t), i * 1e3 / input.fps);
      const frame = await scene.page.screenshot({ type: "jpeg", quality: 92 });
      if (!ff.stdin.write(frame)) await new Promise((r) => ff.stdin.once("drain", r));
      if (i % Math.max(1, Math.round(input.fps * 3)) === 0) input.onProgress?.(i, total);
    }
  } finally {
    ff.stdin.end();
  }
  const code = await closed;
  await scene.close();
  if (code !== 0) throw new Error(`ffmpeg exited ${code}: ${ffErr.slice(-1500)}`);
  return { frames: total, renderMs: Date.now() - started, errors: scene.errors };
}

// tools/scene.ts
var MAX_SECONDS = 90;
function videoWorkspacePath(jobId) {
  return `videos/${jobId}.mp4`;
}
function sceneHtml(input) {
  const html = String(input.html ?? "");
  if (html.length < 200) throw new Error("html is too short to be a scene.");
  if (!/<html[\s>]/i.test(html) && !/<body[\s>]/i.test(html)) throw new Error("html must be a complete document (include <html> and <body>).");
  if (/<(video|audio|iframe)[\s>]/i.test(html)) throw new Error("Scenes cannot use <video>, <audio>, or <iframe>; draw everything with HTML, CSS, SVG, and canvas.");
  return html;
}
var checkScene = defineTool({
  name: "check_scene",
  description: `Load a scene (a complete HTML document, ${WIDTH}x${HEIGHT}) in the renderer's headless browser, seek to the given timestamps under the virtual clock, and report JavaScript errors, failed network requests, and what text is visible at each timestamp. Cheap (a few seconds). Call it before render_video and fix anything it reports.`,
  input: {
    type: "object",
    properties: {
      html: { type: "string", description: "The full HTML document." },
      timestamps: { type: "array", items: { type: "number" }, description: "Seconds to inspect, e.g. [0, 2.5, 6, 12]. Default: every 3 s up to durationSeconds." },
      durationSeconds: { type: "number", minimum: 1, maximum: MAX_SECONDS }
    },
    required: ["html"],
    additionalProperties: false
  },
  async run({ input, reportProgress }) {
    const html = sceneHtml(input);
    await bootstrap((step) => void reportProgress({ step }));
    const duration = Number(input.durationSeconds ?? 30);
    const stamps = Array.isArray(input.timestamps) && input.timestamps.length ? input.timestamps.map(Number).filter((t) => t >= 0 && t <= MAX_SECONDS) : Array.from({ length: Math.floor(duration / 3) + 1 }, (_, i) => i * 3);
    const scene = await loadScene(html);
    const frames = [];
    try {
      for (const t of stamps) {
        await scene.page.evaluate((ms) => window.__seek(ms), t * 1e3);
        const snapshot = await scene.page.evaluate(() => {
          const vw = window.innerWidth, vh = window.innerHeight;
          const texts = [];
          const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
          let n;
          while (n = walker.nextNode()) {
            const s = (n.textContent ?? "").replace(/\s+/g, " ").trim();
            if (!s) continue;
            const el = n.parentElement;
            if (!el) continue;
            const cs = getComputedStyle(el);
            if (cs.display === "none" || cs.visibility === "hidden" || Number(cs.opacity) < 0.05) continue;
            const r = el.getBoundingClientRect();
            if (r.width === 0 || r.height === 0 || r.right < 0 || r.bottom < 0 || r.left > vw || r.top > vh) continue;
            texts.push(s.slice(0, 80));
          }
          return { visibleText: [...new Set(texts)].join(" | ").slice(0, 1200), bodyBackground: getComputedStyle(document.body).backgroundColor };
        });
        frames.push({ t, ...snapshot });
      }
    } finally {
      await scene.close();
    }
    return { ok: scene.errors.length === 0, errors: scene.errors.slice(0, 20), frames };
  }
});
var renderVideo = defineTool({
  name: "render_video",
  description: `Render a scene (a complete HTML document, ${WIDTH}x${HEIGHT}) to an H.264 MP4 under the virtual clock and save it to the session workspace (/workspace). Takes about as long as the video is. Returns the workspace path. Call this once the scene passes check_scene.`,
  input: {
    type: "object",
    properties: {
      html: { type: "string", description: "The full HTML document." },
      durationSeconds: { type: "number", minimum: 3, maximum: MAX_SECONDS, description: "Total length of the video." },
      fps: { type: "integer", enum: [24, 30], description: "Default 30." },
      jobId: { type: "string", description: "job_id from the JOB block; any short name works when there is none." }
    },
    required: ["html", "durationSeconds"],
    additionalProperties: false
  },
  async run({ input, signal, reportProgress }) {
    const html = sceneHtml(input);
    const jobId = String(input.jobId ?? "video").replace(/[^a-zA-Z0-9_-]/g, "");
    if (!jobId) throw new Error("jobId is required.");
    await bootstrap((step) => void reportProgress({ step }));
    const fps = input.fps === 24 ? 24 : 30;
    const durationSeconds = Math.min(Math.max(Number(input.durationSeconds), 3), MAX_SECONDS);
    const dir = join2(WORK, "out");
    await mkdir2(dir, { recursive: true });
    const outPath = join2(dir, `${jobId}.mp4`);
    const rendered = await renderMp4({
      html,
      durationSeconds,
      fps,
      outPath,
      signal,
      onProgress: (frame, total) => void reportProgress({ step: `rendering ${frame}/${total}` })
    });
    await reportProgress({ step: "saving to the workspace" });
    const workspacePath = videoWorkspacePath(jobId);
    const workspaceFile = join2(WORKSPACE, workspacePath);
    await mkdir2(join2(WORKSPACE, "videos"), { recursive: true });
    await copyFile(outPath, workspaceFile);
    const { stat } = await import("node:fs/promises");
    const info = await stat(workspaceFile);
    return {
      path: workspacePath,
      localPath: workspaceFile,
      bytes: info.size,
      durationSeconds,
      fps,
      frames: rendered.frames,
      renderSeconds: Math.round(rendered.renderMs / 1e3),
      errors: rendered.errors.slice(0, 20),
      note: `Saved to the session workspace at /workspace/${workspacePath}; download it from the session's workspace files.`
    };
  }
});

// tools/web.ts
function htmlToText(html) {
  return html.replace(/<script[\s\S]*?<\/script>/gi, " ").replace(/<style[\s\S]*?<\/style>/gi, " ").replace(/<noscript[\s\S]*?<\/noscript>/gi, " ").replace(/<br\s*\/?>|<\/(p|div|li|h[1-6]|tr|section|article)>/gi, "\n").replace(/<[^>]+>/g, " ").replace(/&nbsp;/g, " ").replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/[ \t]+/g, " ").replace(/\n\s*\n+/g, "\n").trim();
}
function extractMeta(html) {
  const pick = (re) => html.match(re)?.[1]?.trim() ?? null;
  const title = pick(/<title[^>]*>([\s\S]*?)<\/title>/i);
  const description = pick(/<meta[^>]+name=["']description["'][^>]+content=["']([^"']*)["']/i) ?? pick(/<meta[^>]+content=["']([^"']*)["'][^>]+name=["']description["']/i);
  const ogTitle = pick(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']*)["']/i);
  const ogDescription = pick(/<meta[^>]+property=["']og:description["'][^>]+content=["']([^"']*)["']/i);
  const themeColor = pick(/<meta[^>]+name=["']theme-color["'][^>]+content=["']([^"']*)["']/i);
  const headings = [...html.matchAll(/<h([1-3])[^>]*>([\s\S]*?)<\/h\1>/gi)].map((m) => htmlToText(m[2])).filter((h) => h && h.length < 160).slice(0, 25);
  const colorCounts = /* @__PURE__ */ new Map();
  for (const m of html.matchAll(/#(?:[0-9a-f]{6}|[0-9a-f]{3})\b/gi)) {
    const c = m[0].toLowerCase();
    colorCounts.set(c, (colorCounts.get(c) ?? 0) + 1);
  }
  const colors = [...colorCounts.entries()].filter(([c]) => !["#fff", "#ffffff", "#000", "#000000"].includes(c)).sort((a, b) => b[1] - a[1]).slice(0, 8).map(([c, n]) => `${c} (${n})`);
  const fonts = [...new Set([...html.matchAll(/fonts\.googleapis\.com\/css2?\?family=([^"'&]+)/gi)].map((m) => decodeURIComponent(m[1]).replace(/\+/g, " ")))].slice(0, 5);
  return { title, description, ogTitle, ogDescription, themeColor, headings, colors, fonts };
}
var webFetch = defineTool({
  name: "web_fetch",
  description: "Fetch a public URL. Returns the page text (HTML stripped) plus extracted metadata: title, description, headings, the hex colors the site uses most, and Google Fonts it loads. Use it to understand a product before writing its video. No authentication is sent.",
  input: {
    type: "object",
    properties: {
      url: { type: "string" },
      maxCharacters: { type: "integer", minimum: 500, maximum: 6e4, description: "Default 12000." }
    },
    required: ["url"],
    additionalProperties: false
  },
  async run({ input, signal }) {
    const url = new URL(String(input.url));
    if (!/^https?:$/.test(url.protocol)) throw new Error("Only http(s) URLs are allowed.");
    const response = await fetch(url, {
      signal,
      redirect: "follow",
      headers: {
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36 shipvideo/1.0",
        Accept: "text/html,application/xhtml+xml,application/json,text/plain,*/*"
      }
    });
    const type = response.headers.get("content-type") ?? "";
    const body = await response.text();
    const max = Math.min(Number(input.maxCharacters ?? 12e3), 6e4);
    const isHtml = /html/i.test(type);
    const text = isHtml ? htmlToText(body) : body;
    return {
      status: response.status,
      url: response.url,
      contentType: type,
      meta: isHtml ? extractMeta(body) : null,
      truncated: text.length > max,
      text: text.slice(0, max)
    };
  }
});

// agent.ts
function parseJob(text) {
  if (!/^\s*JOB\s*$/m.test(text)) return null;
  const field = (name) => text.match(new RegExp(`^${name}:\\s*(.+)$`, "m"))?.[1]?.trim() ?? null;
  const input = field("input");
  if (!input) return null;
  return {
    jobId: field("job_id"),
    mode: field("mode") === "url" ? "url" : "prompt",
    input
  };
}
function Agent() {
  const input = useInput();
  useModel("anthropic/claude-opus-5.5");
  useTool(webFetch);
  useTool(checkScene);
  useTool(renderVideo);
  const text = input.text ?? "";
  const job = parseJob(text);
  const craft = `You are a motion designer who writes code. You make short, punchy, instructional launch videos the way a top agency would, except you write them as a single HTML document that a headless browser renders frame by frame.

WHAT A GREAT VIDEO LOOKS LIKE
- 20 to 40 seconds. 6 to 10 beats. Each beat is one idea, 2 to 5 seconds, then a clean cut to the next.
- Kinetic typography first: huge words that land with weight, then tighten into a sentence. Never more than 8 words on screen at once.
- Show the mechanism, not stock imagery: token grids streaming, a request racing through boxes, a counter climbing, bars filling, a diff resolving, a chat thread appearing line by line. Build these from divs, SVG, and canvas.
- Rhythm: fast beats for the problem, a breath for the name, a confident hold for the payoff. Ease everything (cubic-bezier(.2,.8,.2,1) for entrances, ease-in for exits). Hold the final frame for 2 seconds.
- Structure that works: (1) hook on the pain or the promise, 2 to 3 beats; (2) reveal the product name; (3) how it works in 3 beats, one concrete visual each; (4) proof or numbers if the source gives any (never invent numbers); (5) end card: name, one-line tagline, URL or CTA.
- Typography: a geometric or grotesque display face for the big words (Inter, Space Grotesk, Manrope, Sora, Syne) and optionally a serif accent (Instrument Serif, Fraunces) or mono (JetBrains Mono, IBM Plex Mono) for labels. Load them from fonts.googleapis.com with a <link> tag. Tight letter-spacing on display sizes (-0.03em to -0.05em).
- Color: one bold background color per beat, two at most in the whole film, plus near-black and near-white. Take colors from the product's site when you have them, otherwise choose a confident palette. Keep contrast high; this plays on phones too.
- A thin editorial frame is a nice touch: small mono labels in the corners (project name, beat number, a timecode that actually counts), a progress hairline along the bottom.

HOW THE RENDERER WORKS (follow exactly)
- Output is a complete HTML document, 1920x1080, rendered at 30 fps. Set body { margin:0; width:1920px; height:1080px; overflow:hidden; position:relative }.
- Time is virtual. The page's clock only advances when a frame is requested, so choreograph with either:
  (a) CSS @keyframes with animation-delay and animation-fill-mode: both, one animation per element, or
  (b) a single requestAnimationFrame loop that treats its argument t (milliseconds since start) as the timeline and sets styles/canvas from t.
  Both work in the same document. Do NOT use CSS transitions, do not toggle classes on timers to start animations, do not read Date or performance.now to derive state (use the rAF t instead).
- Deterministic only: no Math.random (write a tiny seeded PRNG if you need noise), no <video>, <audio>, <iframe>, no external images or scripts. Inline SVG and canvas are fine. Fonts from Google Fonts are fine.
- Every element that should leave the screen must be animated out (opacity 0 with fill-mode) or hidden by the next beat's full-bleed background. Nothing may linger from a previous beat.
- Keep the file under 60 KB. Prefer many small elements over one giant script.

PROCESS
1. Understand the subject. In url mode, call web_fetch on the URL (and one more page such as /docs, /pricing, or /about if the home page is thin). Pull the product name, one-line value proposition, three concrete capabilities, any real numbers, and the brand colors and fonts reported in meta. In prompt mode, invent a tasteful name and palette if none is given, and never claim specific numbers.
2. Write a shot list in your head: beat, seconds, on-screen words, visual. Then write the whole HTML.
3. Call check_scene with the HTML and a few timestamps across the film. Fix every reported error and every beat whose visible text is wrong (leftover words from an earlier beat, a beat with nothing on screen). Iterate until it is clean.
4. Call render_video once with the final HTML, the total duration, and jobId copied exactly from the JOB block (or a short name of your choice when there is none).
5. Reply with the workspace path of the video on its own line, then one sentence on what the video says. Nothing else. If the render fails twice, say what failed in plain words.

You run unattended: never ask questions, make sensible choices, and finish.`;
  if (job) {
    return `${craft}

JOB
job_id: ${job.jobId ?? "none"}
mode: ${job.mode}
input: ${job.input}

Make the video now. ${job.mode === "url" ? "Start by fetching the URL." : "The input is the brief; follow it closely and fill gaps with taste."}`;
  }
  return `${craft}

There is no JOB block, so pick a short jobId yourself; render_video saves the MP4 to the session workspace (/workspace) and reports its path.

Request: ${text || "Make a 25-second launch video for a fictional inference startup."}`;
}
export {
  Agent as default
};
