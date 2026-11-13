interface KHRParallelShaderCompile {
  readonly COMPLETION_STATUS_KHR: number;
}

interface ProgramHandle {
  program: WebGLProgram;
  uniforms: Record<string, WebGLUniformLocation | null>;
}

interface LinkingHandle {
  program: WebGLProgram;
  vertex: WebGLShader;
  fragment: WebGLShader;
}

interface ParticleField {
  total: number;
  width: number;
  height: number;
  homeX: Float32Array;
  homeY: Float32Array;
  band: Float32Array;
  phase: Float32Array;
  agility: Float32Array;
  delay: Float32Array;
  orbit: Float32Array;
  x: Float32Array;
  y: Float32Array;
  vx: Float32Array;
  vy: Float32Array;
  particles: Float32Array;
  seeds: Float32Array;
}

interface ThemeTuning {
  palette: string[];
  opacity: number;
  ambient: number;
  light: boolean;
}

const PIXEL_BUDGET = 2.4e6;
const WORLD_HEIGHT = 31.5;
const PALETTE_SIZE = 256;

// Tuned strictly to Proximity's Deep Teal + Emblem Cyan/Blue/Mint tokens (no purple, no gold)
const LIGHT_PALETTE = ['#006D5D', '#008774', '#00A38D', '#2B73BF', '#7BB0CC'];
const DARK_PALETTE = ['#00A38D', '#A8D8D4', '#2B73BF', '#7BB0CC', '#97CAAF'];

const VERTEX_SHADER = `#version 300 es
precision highp float;
layout(location = 0) in vec2 aCorner;
layout(location = 1) in vec4 aParticle;
layout(location = 2) in vec4 aSeed;
uniform vec2 uSize;
uniform float uPixel;
uniform vec2 uCursor;
uniform float uRing;
uniform float uBand;
uniform float uParticle;
uniform float uAmbient;
uniform float uTime;
uniform float uPulse;
uniform float uVariance;
uniform float uDepth;
uniform float uHue;
uniform float uReveal;
uniform float uStreak;
uniform float uCharge;
out vec2 vLocal;
out vec2 vBounds;
out float vU;
out float vAlpha;
out float vSoft;

void main() {
  vec2 rel = aParticle.xy - uCursor;
  float dist = length(rel);
  vec2 radial = dist > 1e-3 ? rel / dist : vec2(1.0, 0.0);
  vec2 dir = radial;
  float speed = length(aParticle.zw);
  float streak = clamp(speed / uStreak, 0.0, 2.5);
  if (speed > 1e-3) {
    vec2 heading = aParticle.zw / speed * sign(dot(aParticle.zw, radial) + 1e-4);
    dir = normalize(mix(radial, heading, smoothstep(0.0, 1.0, streak)));
  }
  float ring = exp(-pow((dist - uRing) / uBand, 2.0));
  float z = aSeed.x;
  float depth = 1.0 + z * 0.32 * uDepth;
  float pulse = 1.0 + sin(uTime * uPulse + aSeed.y) * 0.22 * uVariance;
  float grow = uAmbient + (1.0 - uAmbient) * ring;
  float size = uParticle * depth * pulse * (0.85 + 0.3 * aSeed.w) * (1.0 + uCharge * 0.25 * ring);
  float width = size * 0.17 * mix(0.55, 1.0, ring);
  vec2 bounds = vec2(max(width, size * 0.5 * grow) * (1.0 + streak * 1.2), width);
  float soft = 1.0 + max(-z, 0.0) * 1.6 * uDepth;
  vec2 local = aCorner * (bounds + soft * 1.5 / uPixel);
  vec2 world = aParticle.xy + dir * local.x + vec2(-dir.y, dir.x) * local.y;
  gl_Position = vec4(world.x / uSize.x * 2.0 - 1.0, 1.0 - world.y / uSize.y * 2.0, 0.0, 1.0);
  vLocal = local * uPixel;
  vBounds = bounds * uPixel;
  vSoft = soft;
  vU = atan(radial.y, radial.x) / 6.28318530718 + uHue + (aSeed.z - 0.5) * 0.06;
  float fog = 1.0 - max(-z, 0.0) * 0.35 * uDepth;
  vAlpha = mix(uAmbient, 1.0, ring) * fog * uReveal;
}`;

const FRAGMENT_SHADER = `#version 300 es
precision highp float;
uniform sampler2D uPalette;
uniform float uOpacity;
in vec2 vLocal;
in vec2 vBounds;
in float vU;
in float vAlpha;
in float vSoft;
out vec4 fragColor;

void main() {
  vec2 p = vLocal;
  float r = vBounds.y;
  float d = length(vec2(max(abs(p.x) - max(vBounds.x - r, 0.0), 0.0), p.y)) - r;
  float alpha = clamp(0.5 - d / vSoft, 0.0, 1.0) * vAlpha * uOpacity;
  if (alpha < 0.002) discard;
  vec3 color = texture(uPalette, vec2(fract(vU), 0.25)).rgb;
  fragColor = vec4(color * alpha, alpha);
}`;

const clamp = (value: number, min: number, max: number): number =>
  Math.min(max, Math.max(min, value));

const parseHex = (value: string): [number, number, number] => {
  const clean = value.replace('#', '');
  return [
    parseInt(clean.slice(0, 2), 16) / 255,
    parseInt(clean.slice(2, 4), 16) / 255,
    parseInt(clean.slice(4, 6), 16) / 255
  ];
};

const toLinear = (c: number): number =>
  c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);

const toSrgb = (c: number): number =>
  c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(Math.max(c, 0), 1 / 2.4) - 0.055;

const toOklab = (color: [number, number, number]): [number, number, number] => {
  const r = toLinear(color[0]);
  const g = toLinear(color[1]);
  const b = toLinear(color[2]);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [
    0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
  ];
};

const fromOklab = ([L, a, b]: [number, number, number]): [number, number, number] => {
  const l = Math.pow(L + 0.3963377774 * a + 0.2158037573 * b, 3);
  const m = Math.pow(L - 0.1055613458 * a - 0.0638541728 * b, 3);
  const s = Math.pow(L - 0.0894841775 * a - 1.291485548 * b, 3);
  return [
    clamp(toSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s), 0, 1),
    clamp(toSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s), 0, 1),
    clamp(toSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s), 0, 1)
  ];
};

const buildPaletteData = (colors: string[], light: boolean): Uint8Array => {
  const stops: Array<[number, number, number]> = colors.map((hex) => {
    const lab = toOklab(parseHex(hex));
    const hue = (Math.atan2(lab[2], lab[1]) * 180) / Math.PI;
    const warmth = clamp(1 - Math.abs(hue - 85) / 40, 0, 1);
    const ceiling = 0.62 + 0.14 * warmth * warmth * (3 - 2 * warmth);
    if (light && lab[0] > ceiling) {
      const boost = 1 + (lab[0] - ceiling) * 1.6;
      return [ceiling, lab[1] * boost, lab[2] * boost];
    }
    return lab;
  });

  const data = new Uint8Array(PALETTE_SIZE * 2 * 4);
  const rows: Array<Array<[number, number, number]>> = [
    [...stops, stops[0]],
    stops.length > 1 ? stops : [stops[0], stops[0]]
  ];

  rows.forEach((row, rowIndex) => {
    for (let x = 0; x < PALETTE_SIZE; x++) {
      const t = (x / (PALETTE_SIZE - (rowIndex === 0 ? 0 : 1))) * (row.length - 1);
      const from = Math.min(Math.floor(t), row.length - 2);
      const k = t - from;
      const eased = k * k * (3 - 2 * k);
      const start = row[from];
      const end = row[from + 1];
      const chroma =
        Math.hypot(start[1], start[2]) * (1 - eased) + Math.hypot(end[1], end[2]) * eased;
      const a = start[1] + (end[1] - start[1]) * eased;
      const b = start[2] + (end[2] - start[2]) * eased;
      const len = Math.hypot(a, b);
      const scale = len > 1e-4 ? chroma / len : 0;
      const rgb = fromOklab([
        start[0] + (end[0] - start[0]) * eased,
        a * scale,
        b * scale
      ]);
      const base = (rowIndex * PALETTE_SIZE + x) * 4;
      data[base] = Math.round(rgb[0] * 255);
      data[base + 1] = Math.round(rgb[1] * 255);
      data[base + 2] = Math.round(rgb[2] * 255);
      data[base + 3] = 255;
    }
  });

  return data;
};

const seededRandom = (seed: number) => () => {
  seed |= 0;
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};

const getThemeTuning = (): ThemeTuning => {
  const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
  return isDark
    ? { palette: DARK_PALETTE, opacity: 0.34, ambient: 0.14, light: false }
    : { palette: LIGHT_PALETTE, opacity: 0.28, ambient: 0.14, light: true };
};

const createField = (
  total: number,
  width: number,
  height: number,
  previous: ParticleField | null,
  startFromBelow: boolean
): ParticleField => {
  const rand = seededRandom(1337);
  const margin = 0.06;
  const spanX = width * (1 + margin * 2);
  const spanY = height * (1 + margin * 2);
  const cols = Math.max(1, Math.ceil(Math.sqrt((total * spanX) / spanY)));
  const rows = Math.max(1, Math.ceil(total / cols));
  const cells = Array.from({ length: cols * rows }, (_, i) => i);
  for (let i = cells.length - 1; i > 0; i--) {
    const swap = Math.floor(rand() * (i + 1));
    [cells[i], cells[swap]] = [cells[swap], cells[i]];
  }
  const depths = Array.from({ length: total }, () => rand() * 2 - 1).sort((a, b) => a - b);

  const next: ParticleField = {
    total,
    width,
    height,
    homeX: new Float32Array(total),
    homeY: new Float32Array(total),
    band: new Float32Array(total),
    phase: new Float32Array(total),
    agility: new Float32Array(total),
    delay: new Float32Array(total),
    orbit: new Float32Array(total),
    x: new Float32Array(total),
    y: new Float32Array(total),
    vx: new Float32Array(total),
    vy: new Float32Array(total),
    particles: new Float32Array(total * 4),
    seeds: new Float32Array(total * 4)
  };

  for (let i = 0; i < total; i++) {
    const cell = cells[i % cells.length];
    const hx = -width * margin + (((cell % cols) + 0.5 + (rand() - 0.5) * 0.85) / cols) * spanX;
    const hy =
      -height * margin + ((Math.floor(cell / cols) + 0.5 + (rand() - 0.5) * 0.85) / rows) * spanY;
    next.homeX[i] = hx;
    next.homeY[i] = hy;
    next.band[i] = rand() * 2 - 1;
    next.phase[i] = rand() * Math.PI * 2;
    next.agility[i] = 0.75 + rand() * 0.5;
    next.delay[i] = rand();

    if (previous && i < previous.total) {
      next.x[i] = (previous.x[i] / previous.width) * width;
      next.y[i] = (previous.y[i] / previous.height) * height;
      next.vx[i] = previous.vx[i];
      next.vy[i] = previous.vy[i];
      next.orbit[i] = previous.orbit[i];
    } else {
      next.x[i] = hx;
      next.y[i] = startFromBelow ? hy + height * (0.42 + next.delay[i] * 0.36) : hy;
    }

    next.seeds[i * 4] = depths[i];
    next.seeds[i * 4 + 1] = next.phase[i];
    next.seeds[i * 4 + 2] = rand();
    next.seeds[i * 4 + 3] = rand();
  }

  return next;
};

const simulateField = (
  field: ParticleField,
  cssWidth: number,
  cssHeight: number,
  cursorX: number,
  cursorY: number,
  time: number,
  dt: number,
  charge: number,
  introClock: number,
  reducedMotion: boolean
): void => {
  const unit = cssHeight / WORLD_HEIGHT;
  const ring = 6.2;
  const magnet = 7.4;
  const tightness = 5.5;
  const waveAmplitude = 0.55;
  const waveSpeed = 0.24;
  const waveCount = 3;
  const follow = 0.025;
  const depth = 0.7;

  const ringPx = ring * unit * (1 - 0.16 * charge);
  const magnetPx = Math.max(magnet * unit, 1);
  const spread = (5 / (tightness + 0.1)) * unit;
  const wavePx = waveAmplitude * unit * 0.5;
  const omegaBase = 1.6 + follow * 52;
  const spinRate = charge * 1.4 * (reducedMotion ? 0.3 : 1);
  const centerX = cssWidth / 2;
  const centerY = cssHeight / 2;
  const edge = magnetPx * 0.18;

  const { homeX, homeY, band, phase, agility, delay, orbit, x, y, vx, vy, particles, seeds, total } =
    field;

  for (let i = 0; i < total; i++) {
    const parallax = 1 - seeds[i * 4] * 0.12 * depth;
    const cx = centerX + (cursorX - centerX) * parallax;
    const cy = centerY + (cursorY - centerY) * parallax;
    const hx = homeX[i];
    const hy = homeY[i] + Math.sin(time * 0.7 + phase[i]) * unit * 0.12 * waveAmplitude;
    const dx = hx - cx;
    const dy = hy - cy;
    const dist = Math.hypot(dx, dy);
    const capture = 1 - clamp((dist - (magnetPx - edge)) / (edge * 2), 0, 1);
    const ease = capture * capture * (3 - 2 * capture);

    let turn = orbit[i] + spinRate * ease * dt;
    turn -= Math.atan2(Math.sin(turn), Math.cos(turn)) * (1 - ease) * Math.min(1, dt * 2.5);
    orbit[i] = turn;

    let tx = hx;
    let ty = hy;
    if (ease > 0 || Math.abs(turn) > 1e-4) {
      const angle = Math.atan2(dy, dx) + turn;
      const wave =
        wavePx *
        (0.6 * Math.sin(angle * waveCount - time * waveSpeed * 2) +
          0.4 * Math.sin(phase[i] + time * waveSpeed * 1.3));
      const radius = dist + (ringPx + wave + band[i] * spread - dist) * ease;
      tx = cx + Math.cos(angle) * radius;
      ty = cy + Math.sin(angle) * radius;
    }

    const omega = introClock < delay[i] * 0.9 ? 0 : omegaBase * agility[i];
    const damping = 1.64 * omega;
    const stiffness = omega * omega;
    const steps = omega > 0 ? Math.min(4, Math.max(1, Math.ceil(omega * dt * 2))) : 1;
    const h = dt / steps;
    for (let step = 0; step < steps; step++) {
      vx[i] += ((tx - x[i]) * stiffness - vx[i] * damping) * h;
      vy[i] += ((ty - y[i]) * stiffness - vy[i] * damping) * h;
      x[i] += vx[i] * h;
      y[i] += vy[i] * h;
    }

    particles[i * 4] = x[i];
    particles[i * 4 + 1] = y[i];
    particles[i * 4 + 2] = vx[i];
    particles[i * 4 + 3] = vy[i];
  }
};

const compileShader = (
  gl: WebGL2RenderingContext,
  type: number,
  source: string
): WebGLShader | null => {
  const shader = gl.createShader(type);
  if (!shader) return null;
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  return shader;
};

const linkProgram = (gl: WebGL2RenderingContext): LinkingHandle | null => {
  const vertex = compileShader(gl, gl.VERTEX_SHADER, VERTEX_SHADER);
  const fragment = compileShader(gl, gl.FRAGMENT_SHADER, FRAGMENT_SHADER);
  const program = vertex && fragment ? gl.createProgram() : null;
  if (!vertex || !fragment || !program) {
    if (vertex) gl.deleteShader(vertex);
    if (fragment) gl.deleteShader(fragment);
    return null;
  }
  gl.attachShader(program, vertex);
  gl.attachShader(program, fragment);
  gl.linkProgram(program);
  return { program, vertex, fragment };
};

const finishProgram = (
  gl: WebGL2RenderingContext,
  { program, vertex, fragment }: LinkingHandle
): ProgramHandle | null => {
  const linked = Boolean(gl.getProgramParameter(program, gl.LINK_STATUS));
  gl.deleteShader(vertex);
  gl.deleteShader(fragment);
  if (!linked) {
    gl.deleteProgram(program);
    return null;
  }
  const uniforms: ProgramHandle['uniforms'] = {};
  const count = gl.getProgramParameter(program, gl.ACTIVE_UNIFORMS) as number;
  for (let i = 0; i < count; i++) {
    const info = gl.getActiveUniform(program, i);
    if (info) uniforms[info.name] = gl.getUniformLocation(program, info.name);
  }
  return { program, uniforms };
};

const getDesiredCount = (width: number, height: number): number =>
  Math.round(clamp((width * height) / 1350, 360, 920));

/**
 * Replaces the old contour lines with an Antigravity-style interactive WebGL2 capsule ring
 * background (with a Canvas 2D fallback) tuned to Proximity's Deep Teal / Mark palette.
 */
export function initAntigravityBg(reducedMotion: boolean): void {
  const canvas = document.getElementById('antigravity-canvas') as HTMLCanvasElement | null;
  if (!canvas) return;

  const gl = canvas.getContext('webgl2', {
    alpha: true,
    premultipliedAlpha: true,
    antialias: false,
    depth: false,
    stencil: false,
    powerPreference: 'high-performance'
  });
  const ctx2d = !gl ? canvas.getContext('2d') : null;
  if (!gl && !ctx2d) return;

  const parallel = gl
    ? (gl.getExtension('KHR_parallel_shader_compile') as KHRParallelShaderCompile | null)
    : null;
  const linking = gl ? linkProgram(gl) : null;
  let shader = gl && linking && !parallel ? finishProgram(gl, linking) : null;
  if (gl && (!linking || (!parallel && !shader))) {
    // Fallback cleanly if shader compile fails unexpectedly
    return;
  }

  let vao: WebGLVertexArrayObject | null = null;
  let cornerBuffer: WebGLBuffer | null = null;
  let particleBuffer: WebGLBuffer | null = null;
  let seedBuffer: WebGLBuffer | null = null;
  let paletteTexture: WebGLTexture | null = null;

  if (gl) {
    vao = gl.createVertexArray();
    gl.bindVertexArray(vao);

    cornerBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, cornerBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    gl.enableVertexAttribArray(0);
    gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);

    particleBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, particleBuffer);
    gl.enableVertexAttribArray(1);
    gl.vertexAttribPointer(1, 4, gl.FLOAT, false, 0, 0);
    gl.vertexAttribDivisor(1, 1);

    seedBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, seedBuffer);
    gl.enableVertexAttribArray(2);
    gl.vertexAttribPointer(2, 4, gl.FLOAT, false, 0, 0);
    gl.vertexAttribDivisor(2, 1);

    paletteTexture = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, paletteTexture);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.REPEAT);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  }

  const size = { width: 1, height: 1, ratio: 1 };
  const cursor = { x: 0, y: 0, vx: 0, vy: 0, tx: 0, ty: 0, ready: false };
  const pointer = { x: 0, y: 0, inside: false, down: false, moved: -Infinity };

  let field: ParticleField | null = null;
  let lastPaletteKey = '';
  let time = 0;
  let hue = 0;
  let charge = 0;
  let introClock = reducedMotion ? Infinity : 0;
  let rafId = 0;
  let lastNow = 0;
  let pageVisible = document.visibilityState !== 'hidden';
  let lost = false;

  const syncPalette = (tuning: ThemeTuning) => {
    const key = `${tuning.palette.join(',')}|${tuning.light}`;
    if (key === lastPaletteKey) return;
    lastPaletteKey = key;
    if (gl && paletteTexture) {
      gl.bindTexture(gl.TEXTURE_2D, paletteTexture);
      gl.texImage2D(
        gl.TEXTURE_2D,
        0,
        gl.RGBA8,
        PALETTE_SIZE,
        2,
        0,
        gl.RGBA,
        gl.UNSIGNED_BYTE,
        buildPaletteData(tuning.palette, tuning.light)
      );
    }
  };

  const applyResize = () => {
    const cssWidth = Math.max(1, window.innerWidth);
    const cssHeight = Math.max(1, window.innerHeight);
    const ratio = Math.min(
      window.devicePixelRatio || 1,
      2,
      Math.sqrt(PIXEL_BUDGET / (cssWidth * cssHeight))
    );
    const pxWidth = Math.max(1, Math.round(cssWidth * ratio));
    const pxHeight = Math.max(1, Math.round(cssHeight * ratio));

    if (canvas.width !== pxWidth || canvas.height !== pxHeight) {
      canvas.width = pxWidth;
      canvas.height = pxHeight;
    }
    size.width = cssWidth;
    size.height = cssHeight;
    size.ratio = pxWidth / cssWidth;

    if (!cursor.ready) {
      cursor.x = cursor.tx = cssWidth / 2;
      cursor.y = cursor.ty = cssHeight / 2;
      cursor.ready = true;
    }

    const desiredCount = getDesiredCount(cssWidth, cssHeight);
    if (
      !field ||
      field.total !== desiredCount ||
      Math.abs(field.width - cssWidth) > 0.5 ||
      Math.abs(field.height - cssHeight) > 0.5
    ) {
      field = createField(desiredCount, cssWidth, cssHeight, field, !reducedMotion);
      if (reducedMotion && field) {
        // Settle the magnetic ring softly once for a calm static composition
        for (let i = 0; i < 36; i++) {
          simulateField(
            field,
            cssWidth,
            cssHeight,
            cursor.x,
            cursor.y,
            0,
            1 / 30,
            0,
            Infinity,
            true
          );
        }
        field.particles.forEach((_, idx) => {
          if (field && (idx % 4 === 2 || idx % 4 === 3)) field.particles[idx] = 0;
        });
      }
      if (gl && seedBuffer && particleBuffer && field) {
        gl.bindBuffer(gl.ARRAY_BUFFER, seedBuffer);
        gl.bufferData(gl.ARRAY_BUFFER, field.seeds, gl.STATIC_DRAW);
        gl.bindBuffer(gl.ARRAY_BUFFER, particleBuffer);
        gl.bufferData(gl.ARRAY_BUFFER, field.particles.byteLength, gl.DYNAMIC_DRAW);
      }
    }
  };

  const render2d = (tuning: ThemeTuning) => {
    if (!ctx2d || !field) return;
    ctx2d.setTransform(size.ratio, 0, 0, size.ratio, 0, 0);
    ctx2d.clearRect(0, 0, size.width, size.height);

    const unit = size.height / WORLD_HEIGHT;
    const ringPx = 6.2 * unit * (1 - 0.16 * charge);
    const bandPx = Math.max(unit * 5.6, 1);
    const particleUnit = 0.64 * unit * 0.6;
    const reveal = introClock < 3 ? clamp(introClock / 0.6, 0, 1) : 1;

    for (let i = 0; i < field.total; i++) {
      const px = field.particles[i * 4];
      const py = field.particles[i * 4 + 1];
      const pvx = field.particles[i * 4 + 2];
      const pvy = field.particles[i * 4 + 3];
      const relX = px - cursor.x;
      const relY = py - cursor.y;
      const dist = Math.hypot(relX, relY);
      const radX = dist > 1e-3 ? relX / dist : 1;
      const radY = dist > 1e-3 ? relY / dist : 0;
      const speed = Math.hypot(pvx, pvy);
      let angle = Math.atan2(radY, radX);
      if (speed > 1e-3) {
        const s = pvx * radX + pvy * radY >= 0 ? 1 : -1;
        angle = Math.atan2((pvy / speed) * s, (pvx / speed) * s);
      }

      const ring = Math.exp(-Math.pow((dist - ringPx) / bandPx, 2));
      const z = field.seeds[i * 4];
      const grow = tuning.ambient + (1 - tuning.ambient) * ring;
      const sizeBase = particleUnit * (1 + z * 0.32) * (0.85 + 0.3 * field.seeds[i * 4 + 3]);
      const halfThick = sizeBase * 0.17 * (0.55 + 0.45 * ring);
      const halfLen = Math.max(halfThick, sizeBase * 0.5 * grow);
      const fog = 1 - Math.max(-z, 0) * 0.35;
      const alpha = (tuning.ambient + (1 - tuning.ambient) * ring) * fog * reveal * tuning.opacity;
      if (alpha < 0.01) continue;

      const colorIdx =
        Math.floor(
          (((Math.atan2(radY, radX) / (Math.PI * 2) + hue + 10) % 1) * tuning.palette.length)
        ) % tuning.palette.length;

      ctx2d.save();
      ctx2d.translate(px, py);
      ctx2d.rotate(angle);
      ctx2d.globalAlpha = alpha;
      ctx2d.strokeStyle = tuning.palette[colorIdx];
      ctx2d.lineWidth = halfThick * 2;
      ctx2d.lineCap = 'round';
      ctx2d.beginPath();
      ctx2d.moveTo(-halfLen + halfThick, 0);
      ctx2d.lineTo(halfLen - halfThick, 0);
      ctx2d.stroke();
      ctx2d.restore();
    }
  };

  const drawCurrent = () => {
    if (!field || lost) return;
    const tuning = getThemeTuning();
    syncPalette(tuning);

    if (gl && shader && vao && particleBuffer && paletteTexture) {
      const unit = size.height / WORLD_HEIGHT;
      gl.viewport(0, 0, canvas.width, canvas.height);
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT);
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
      gl.useProgram(shader.program);
      gl.bindVertexArray(vao);
      gl.bindBuffer(gl.ARRAY_BUFFER, particleBuffer);
      gl.bufferSubData(gl.ARRAY_BUFFER, 0, field.particles);

      const u = shader.uniforms;
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, paletteTexture);
      gl.uniform1i(u.uPalette, 0);
      gl.uniform2f(u.uSize, size.width, size.height);
      gl.uniform1f(u.uPixel, size.ratio);
      gl.uniform2f(u.uCursor, cursor.x, cursor.y);
      gl.uniform1f(u.uRing, 6.2 * unit * (1 - 0.16 * charge));
      gl.uniform1f(u.uBand, Math.max(unit * 5.6, 1));
      gl.uniform1f(u.uParticle, 0.64 * unit * 0.6);
      gl.uniform1f(u.uAmbient, tuning.ambient);
      gl.uniform1f(u.uTime, time);
      gl.uniform1f(u.uPulse, 1.6);
      gl.uniform1f(u.uVariance, 0.65);
      gl.uniform1f(u.uDepth, 0.7);
      gl.uniform1f(u.uHue, hue);
      gl.uniform1f(u.uReveal, introClock < 3 ? clamp(introClock / 0.6, 0, 1) : 1);
      gl.uniform1f(u.uStreak, unit * 30);
      gl.uniform1f(u.uCharge, charge);
      gl.uniform1f(u.uOpacity, tuning.opacity);
      gl.drawArraysInstanced(gl.TRIANGLE_STRIP, 0, 4, field.total);
    } else {
      render2d(tuning);
    }
  };

  const burst = (strength: number) => {
    if (!field || reducedMotion) return;
    const unit = size.height / WORLD_HEIGHT;
    const reach = 7.4 * unit * 1.6;
    const { x, y, vx, vy, agility, total } = field;
    for (let i = 0; i < total; i++) {
      const dx = x[i] - cursor.x;
      const dy = y[i] - cursor.y;
      const dist = Math.hypot(dx, dy) + 1e-3;
      const kick = unit * 38 * strength * Math.exp(-Math.pow(dist / reach, 2)) * agility[i];
      vx[i] += (dx / dist) * kick;
      vy[i] += (dy / dist) * kick;
    }
  };

  const frame = (now: number) => {
    rafId = 0;
    if (gl && !shader) {
      if (lost || !parallel || !linking || gl.isContextLost()) return;
      if (!gl.getProgramParameter(linking.program, parallel.COMPLETION_STATUS_KHR)) {
        lastNow = Math.max(lastNow, now);
        if (pageVisible) rafId = requestAnimationFrame(frame);
        return;
      }
      shader = finishProgram(gl, linking);
      if (!shader) {
        lost = true;
        return;
      }
    }

    if (lost || !field) return;
    const dt = clamp((now - lastNow) / 1000 || 0, 0, 1 / 30);
    lastNow = Math.max(lastNow, now);
    time += dt;
    hue += dt * 0.02;
    if (introClock < 3) introClock += dt;
    charge = pointer.down ? Math.min(1, charge + dt / 1.1) : Math.max(0, charge - dt * 3.2);

    const idle = performance.now() - pointer.moved > 2600 || !pointer.inside;
    if (idle) {
      cursor.tx = size.width / 2 + Math.sin(time * 0.24) * (size.width * 0.14);
      cursor.ty = size.height / 2 + Math.cos(time * 0.36) * (size.height * 0.12);
    } else {
      cursor.tx = pointer.x;
      cursor.ty = pointer.y;
    }

    cursor.vx += ((cursor.tx - cursor.x) * 18 - cursor.vx * 8.5) * dt;
    cursor.vy += ((cursor.ty - cursor.y) * 18 - cursor.vy * 8.5) * dt;
    cursor.x += cursor.vx * dt;
    cursor.y += cursor.vy * dt;

    simulateField(
      field,
      size.width,
      size.height,
      cursor.x,
      cursor.y,
      time,
      dt,
      charge,
      introClock,
      false
    );
    drawCurrent();

    if (pageVisible && !reducedMotion) {
      rafId = requestAnimationFrame(frame);
    }
  };

  const wake = () => {
    if (reducedMotion) {
      drawCurrent();
      return;
    }
    if (rafId || lost || !pageVisible) return;
    lastNow = performance.now();
    rafId = requestAnimationFrame(frame);
  };

  applyResize();

  const themeObserver = new MutationObserver(() => {
    drawCurrent();
  });
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme']
  });

  window.addEventListener(
    'resize',
    () => {
      applyResize();
      drawCurrent();
    },
    { passive: true }
  );

  if (reducedMotion) {
    drawCurrent();
    return;
  }

  const onPointerMove = (e: PointerEvent) => {
    pointer.inside = true;
    pointer.x = e.clientX;
    pointer.y = e.clientY;
    pointer.moved = performance.now();
  };

  const onPointerDown = (e: PointerEvent) => {
    pointer.inside = true;
    pointer.x = e.clientX;
    pointer.y = e.clientY;
    pointer.moved = performance.now();
    pointer.down = true;
  };

  const onPointerUp = (e: PointerEvent) => {
    if (pointer.down) burst(0.22 + charge * 0.35);
    pointer.down = false;
    if (e.pointerType !== 'mouse') pointer.inside = false;
  };

  const onPointerLeave = () => {
    pointer.inside = false;
    pointer.down = false;
  };

  window.addEventListener('pointermove', onPointerMove, { passive: true });
  window.addEventListener('pointerdown', onPointerDown, { passive: true });
  window.addEventListener('pointerup', onPointerUp, { passive: true });
  window.addEventListener('pointercancel', onPointerUp, { passive: true });
  document.documentElement.addEventListener('pointerleave', onPointerLeave);

  document.addEventListener('visibilitychange', () => {
    pageVisible = document.visibilityState !== 'hidden';
    if (!pageVisible && rafId) {
      cancelAnimationFrame(rafId);
      rafId = 0;
    } else if (pageVisible) {
      wake();
    }
  });

  if (gl) {
    canvas.addEventListener('webglcontextlost', (e) => {
      e.preventDefault();
      lost = true;
      if (rafId) {
        cancelAnimationFrame(rafId);
        rafId = 0;
      }
    });
  }

  wake();
}
