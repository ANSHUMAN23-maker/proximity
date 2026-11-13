import * as THREE from 'three';

export interface ScreenRenderState {
  beat: number;
  theme: 'light' | 'dark';
  timeSec: number;
  reducedMotion: boolean;
}

interface ScreenPalette {
  bg: string;
  surface: string;
  surfaceVariant: string;
  surfaceGlass: string;
  surfaceGlassBorder: string;
  ink: string;
  inkSecondary: string;
  inkMuted: string;
  outline: string;
  primary: string;
  primaryDark: string;
  contour: string;
}

// Color tokens matched directly to app/ui/theme/{Theme.kt, Color.kt, GlassTokens.kt, AnimatedContourBackground.kt}
const LIGHT_PALETTE: ScreenPalette = {
  bg: '#F8FAFC',
  surface: '#FFFFFF',
  surfaceVariant: '#F1F5F9',
  surfaceGlass: 'rgba(255, 255, 255, 0.78)',
  surfaceGlassBorder: 'rgba(226, 232, 240, 0.85)',
  ink: '#0F172A',
  inkSecondary: '#334155',
  inkMuted: '#475569',
  outline: 'rgba(203, 213, 225, 0.75)',
  primary: '#008774',
  primaryDark: '#006D5D',
  contour: 'rgba(148, 163, 184, 0.35)'
};

const DARK_PALETTE: ScreenPalette = {
  bg: '#090D14',
  surface: '#101721',
  surfaceVariant: '#17202C',
  surfaceGlass: 'rgba(16, 23, 33, 0.76)',
  surfaceGlassBorder: 'rgba(255, 255, 255, 0.22)',
  ink: '#F8FAFC',
  inkSecondary: '#CBD5E1',
  inkMuted: '#94A3B8',
  outline: 'rgba(51, 67, 86, 0.65)',
  primary: '#008774',
  primaryDark: '#006D5D',
  contour: 'rgba(0, 135, 116, 0.16)'
};

const CANVAS_W = 720;
const CANVAS_H = 1560;

// Maps Android ~390dp phone layout width into 720px canvas safe zone cleanly inside Galaxy S25 Ultra UVs
const PAD_X = 56;
const CONTENT_W = CANVAS_W - PAD_X * 2;
const FONT_STACK = "'Plus Jakarta Sans', 'Segoe UI', system-ui, sans-serif";
const MONO_STACK = "'JetBrains Mono', 'Cascadia Code', monospace";

function roundRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number
): void {
  const radius = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.arcTo(x + w, y, x + w, y + h, radius);
  ctx.arcTo(x + w, y + h, x, y + h, radius);
  ctx.arcTo(x, y + h, x, y, radius);
  ctx.arcTo(x, y, x + w, y, radius);
  ctx.closePath();
}

function fillRounded(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
  fill: string | CanvasGradient,
  stroke?: string,
  lineWidth = 2
): void {
  roundRect(ctx, x, y, w, h, r);
  ctx.fillStyle = fill;
  ctx.fill();
  if (stroke) {
    ctx.lineWidth = lineWidth;
    ctx.strokeStyle = stroke;
    ctx.stroke();
  }
}

export class PhoneScreenRenderer {
  public readonly canvas: HTMLCanvasElement;
  public readonly texture: THREE.CanvasTexture;
  private readonly ctx: CanvasRenderingContext2D;
  private logoImg: HTMLImageElement | null = null;
  private logoLoaded = false;
  private lastKey = '';

  constructor() {
    this.canvas = document.createElement('canvas');
    this.canvas.width = CANVAS_W;
    this.canvas.height = CANVAS_H;
    const context = this.canvas.getContext('2d');
    if (!context) {
      throw new Error('2D canvas context unavailable for 3D phone screen');
    }
    this.ctx = context;

    this.texture = new THREE.CanvasTexture(this.canvas);
    this.texture.colorSpace = THREE.SRGBColorSpace;
    this.texture.flipY = false; // GLB Screen UV v=0 at top, v=1 at bottom
    this.texture.anisotropy = 8;
    this.texture.minFilter = THREE.LinearMipmapLinearFilter;
    this.texture.magFilter = THREE.LinearFilter;

    const img = new Image();
    img.src = '/proximity-mark.png';
    img.onload = () => {
      this.logoImg = img;
      this.logoLoaded = true;
      this.lastKey = '';
    };
  }

  public render(state: ScreenRenderState): void {
    const isAnimatedBeat = (state.beat === 2 || state.beat === 3) && !state.reducedMotion;
    const animBucket = isAnimatedBeat ? Math.floor(state.timeSec * 30) : 0;
    const key = `${state.beat}|${state.theme}|${this.logoLoaded ? 1 : 0}|${animBucket}`;
    if (key === this.lastKey) return;
    this.lastKey = key;

    const p = state.theme === 'dark' ? DARK_PALETTE : LIGHT_PALETTE;
    const ctx = this.ctx;

    ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);

    if (state.beat === 2) {
      this.drawQrScannerScreen(ctx, p, state.theme, state.timeSec, state.reducedMotion);
    } else if (state.beat === 1) {
      this.drawBackgroundAndContours(ctx, p);
      this.drawStatusBar(ctx, p.ink);
      this.drawSetupScreen(ctx, p, state.theme);
    } else if (state.beat === 3) {
      this.drawBackgroundAndContours(ctx, p);
      this.drawStatusBar(ctx, p.ink);
      this.drawSuccessScreen(ctx, p, state.theme, state.timeSec, state.reducedMotion);
    } else {
      this.drawBackgroundAndContours(ctx, p);
      this.drawStatusBar(ctx, p.ink);
      this.drawHomeScreen(ctx, p, state.theme);
    }

    // Android gesture navigation handle pill at bottom
    fillRounded(
      ctx,
      CANVAS_W / 2 - 96,
      CANVAS_H - 40,
      192,
      8,
      4,
      state.beat === 2 && state.theme === 'dark'
        ? 'rgba(241, 245, 249, 0.42)'
        : state.theme === 'dark'
          ? 'rgba(241, 245, 249, 0.34)'
          : 'rgba(15, 23, 42, 0.24)'
    );

    this.texture.needsUpdate = true;
  }

  private drawBackgroundAndContours(ctx: CanvasRenderingContext2D, p: ScreenPalette): void {
    ctx.fillStyle = p.bg;
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);

    // Exact AnimatedContourBackground diagonal wavy splines from AnimatedContourBackground.kt
    ctx.save();
    ctx.strokeStyle = p.contour;
    ctx.lineWidth = 2.8;
    ctx.lineCap = 'round';
    const w = CANVAS_W;
    const h = CANVAS_H;
    const splines: Array<[number, number, number, number, number, number, number, number]> = [
      [-w * 0.1, h * 0.05, w * 0.06, h * 0.24, w * 0.29, h * 0.27, w * 0.38, h * 0.55],
      [w * 0.38, h * 0.55, w * 0.46, h * 0.78, w * 0.54, h * 0.83, w * 0.65, h * 1.1],
      [-w * 0.1, h * 0.25, w * 0.09, h * 0.56, w * 0.41, h * 0.37, w * 0.5, h * 0.72],
      [w * 0.5, h * 0.72, w * 0.59, h * 1.05, w * 0.76, h * 0.85, w * 0.85, h * 1.1],
      [w * 0.1, -h * 0.05, w * 0.28, h * 0.24, w * 0.53, h * 0.18, w * 0.65, h * 0.48],
      [w * 0.65, h * 0.48, w * 0.77, h * 0.74, w * 0.89, h * 0.68, w * 1.05, h * 0.96],
      [w * 0.35, -h * 0.05, w * 0.51, h * 0.16, w * 0.74, h * 0.12, w * 0.88, h * 0.38]
    ];
    for (const [sx, sy, cx1, cy1, cx2, cy2, ex, ey] of splines) {
      ctx.beginPath();
      ctx.moveTo(sx, sy);
      ctx.bezierCurveTo(cx1, cy1, cx2, cy2, ex, ey);
      ctx.stroke();
    }
    ctx.restore();
  }

  private drawStatusBar(ctx: CanvasRenderingContext2D, inkColor: string): void {
    ctx.save();
    ctx.fillStyle = inkColor;
    ctx.font = `700 22px ${FONT_STACK}`;
    ctx.textBaseline = 'middle';
    ctx.textAlign = 'left';
    ctx.fillText('9:41', PAD_X, 66);

    const rx = CANVAS_W - PAD_X;
    ctx.font = `600 19px ${MONO_STACK}`;
    ctx.textAlign = 'right';
    ctx.fillText('NO DATA · 86%', rx, 66);
    ctx.restore();
  }

  // SCREEN 0: MainActivity.kt (ProximityBrandedHeader + ? glass + Teal GreetingCard + 3 LiquidGlass Tiles + Rewards + FloatingBottomNavBar)
  private drawHomeScreen(
    ctx: CanvasRenderingContext2D,
    p: ScreenPalette,
    theme: 'light' | 'dark'
  ): void {
    const x = PAD_X;
    let y = 148;

    // ProximityBrandedHeader (32dp logo + "Proximity" 24sp Bold) + 38dp Help Question Button
    if (this.logoImg && this.logoLoaded) {
      ctx.save();
      ctx.beginPath();
      ctx.arc(x + 30, y + 30, 30, 0, Math.PI * 2);
      ctx.clip();
      ctx.drawImage(this.logoImg, x, y, 60, 60);
      ctx.restore();
    } else {
      fillRounded(ctx, x, y, 60, 60, 30, p.primary);
    }

    ctx.fillStyle = p.ink;
    ctx.font = `700 42px ${FONT_STACK}`;
    ctx.textBaseline = 'middle';
    ctx.textAlign = 'left';
    ctx.fillText('Proximity', x + 82, y + 31);

    // 38dp liquidGlass Circle Help Icon Button tinted #008774
    fillRounded(ctx, x + CONTENT_W - 70, y - 4, 68, 68, 34, p.surfaceGlass, p.surfaceGlassBorder, 2);
    ctx.beginPath();
    ctx.arc(x + CONTENT_W - 36, y + 30, 17, 0, Math.PI * 2);
    ctx.strokeStyle = p.primary;
    ctx.lineWidth = 3.2;
    ctx.stroke();
    ctx.fillStyle = p.primary;
    ctx.font = `800 22px ${FONT_STACK}`;
    ctx.textAlign = 'center';
    ctx.fillText('?', x + CONTENT_W - 36, y + 31);

    y += 108;

    // GreetingCard (MainActivity.kt L391-482): Solid #008774 -> #006D5D gradient card
    const cardH = 228;
    const cardGrad = ctx.createLinearGradient(x, y, x + CONTENT_W, y + cardH);
    cardGrad.addColorStop(0, p.primary);
    cardGrad.addColorStop(1, p.primaryDark);
    const cardBorderColor =
      theme === 'dark' ? 'rgba(255, 255, 255, 0.24)' : 'rgba(255, 255, 255, 0.65)';
    fillRounded(ctx, x, y, CONTENT_W, cardH, 44, cardGrad, cardBorderColor, 2);

    // Subtle white vertical top glass sheen & decorative top-right 120dp translucent circle
    ctx.save();
    roundRect(ctx, x, y, CONTENT_W, cardH, 44);
    ctx.clip();

    const gloss = ctx.createLinearGradient(0, y, 0, y + cardH * 0.48);
    gloss.addColorStop(0, 'rgba(255, 255, 255, 0.22)');
    gloss.addColorStop(1, 'rgba(255, 255, 255, 0)');
    ctx.fillStyle = gloss;
    ctx.fillRect(x, y, CONTENT_W, cardH * 0.48);

    ctx.fillStyle = 'rgba(255, 255, 255, 0.09)';
    ctx.beginPath();
    ctx.arc(x + CONTENT_W - 22, y + 26, 108, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();

    // Bank row (white wallet icon + white/85% bank name)
    this.drawWalletGlyph(ctx, x + 56, y + 58, 'rgba(255, 255, 255, 0.88)');
    ctx.fillStyle = 'rgba(255, 255, 255, 0.88)';
    ctx.font = `600 24px ${FONT_STACK}`;
    ctx.textAlign = 'left';
    ctx.fillText('HDFC Bank', x + 84, y + 59);

    // ExtraBold White Greeting text (21sp ExtraBold lineHeight 28sp)
    ctx.fillStyle = '#FFFFFF';
    ctx.font = `800 39px ${FONT_STACK}`;
    ctx.fillText('Greetings,', x + 44, y + 118);
    ctx.fillText('the internetless one', x + 44, y + 168);

    y += cardH + 74; // Matches 16dp + 40dp spacing in MainActivity.kt

    // PaymentActionButtons: Three equal 110dp LiquidGlass tiles, 12dp gap, 24dp corners
    const gap = 22;
    const tileW = (CONTENT_W - gap * 2) / 3;
    const tileH = 204;
    const tiles: Array<{ label: string; drawIcon: (cx: number, cy: number) => void }> = [
      { label: 'Scan QR Code', drawIcon: (cx, cy) => this.drawQrGlyph(ctx, cx, cy, p.primary) },
      { label: 'Pay Contact', drawIcon: (cx, cy) => this.drawContactGlyph(ctx, cx, cy, p.primary) },
      { label: 'Recharge', drawIcon: (cx, cy) => this.drawRechargeGlyph(ctx, cx, cy, p.primary) }
    ];

    tiles.forEach((tile, idx) => {
      const tx = x + idx * (tileW + gap);
      fillRounded(ctx, tx, y, tileW, tileH, 42, p.surfaceGlass, p.surfaceGlassBorder, 2);
      tile.drawIcon(tx + tileW / 2, y + 78);
      ctx.fillStyle = p.ink;
      ctx.font = `600 21px ${FONT_STACK}`;
      ctx.textAlign = 'center';
      ctx.fillText(tile.label, tx + tileW / 2, y + 154);
    });

    y += tileH + 30;

    // RewardsSectionCard (MainActivity.kt L697-745): 20dp radius, 44dp icon circle, bold single-line title
    const rewardsH = 132;
    fillRounded(ctx, x, y, CONTENT_W, rewardsH, 36, p.surfaceGlass, p.surfaceGlassBorder, 2);
    fillRounded(ctx, x + 30, y + 26, 80, 80, 40, 'rgba(0, 135, 116, 0.14)');
    this.drawGiftGlyph(ctx, x + 70, y + 66, p.primary);

    ctx.fillStyle = p.ink;
    ctx.font = `700 27px ${FONT_STACK}`;
    ctx.textAlign = 'left';
    ctx.fillText('Rewards & Cashback', x + 134, y + 67);

    // FloatingBottomNavBar (FloatingBottomNavBar.kt L199-354): horizontal 64dp margin -> centered floating capsule
    const navW = CONTENT_W - 112;
    const navX = (CANVAS_W - navW) / 2;
    const navY = CANVAS_H - 188;
    const navH = 104;

    ctx.save();
    ctx.shadowColor = theme === 'dark' ? 'rgba(0, 0, 0, 0.45)' : 'rgba(15, 23, 42, 0.12)';
    ctx.shadowBlur = 24;
    ctx.shadowOffsetY = 8;
    fillRounded(ctx, navX, navY, navW, navH, 52, p.surfaceGlass, p.surfaceGlassBorder, 2);
    ctx.restore();

    const innerPad = 14;
    const tabW = (navW - innerPad * 2) / 3;
    // Active Liquid Indicator Puck over Home (FloatingBottomNavBar.kt L315-348)
    const puckGrad = ctx.createLinearGradient(navX + innerPad, navY + 12, navX + innerPad + tabW, navY + navH - 12);
    puckGrad.addColorStop(0, 'rgba(0, 135, 116, 0.24)');
    puckGrad.addColorStop(1, 'rgba(0, 135, 116, 0.11)');
    fillRounded(
      ctx,
      navX + innerPad + 4,
      navY + 13,
      tabW - 8,
      navH - 26,
      39,
      puckGrad,
      'rgba(0, 135, 116, 0.40)',
      2
    );

    const tabs = ['Home', 'History', 'Settings'];
    tabs.forEach((label, idx) => {
      const cx = navX + innerPad + tabW * idx + tabW / 2;
      const col = idx === 0 ? p.primary : p.inkMuted;
      if (idx === 0) this.drawHomeGlyph(ctx, cx, navY + 40, col);
      if (idx === 1) this.drawHistoryGlyph(ctx, cx, navY + 40, col);
      if (idx === 2) this.drawSettingsGlyph(ctx, cx, navY + 40, col);

      ctx.fillStyle = col;
      ctx.font = `${idx === 0 ? '700' : '500'} 19px ${FONT_STACK}`;
      ctx.textAlign = 'center';
      ctx.fillText(label, cx, navY + 72);
    });
  }

  // SCREEN 1: SetupActivity.kt (SetupStep.SIM -> 50% bar + Back + "Select your SIM" + Primary SIM + Brand Badges + Continue ->)
  private drawSetupScreen(
    ctx: CanvasRenderingContext2D,
    p: ScreenPalette,
    theme: 'light' | 'dark'
  ): void {
    const x = PAD_X;
    let y = 148;

    // Progress Bar: height 2dp (~4px), progress 0.50f
    const trackColor = theme === 'dark' ? '#1E293B' : '#CBD5E1';
    fillRounded(ctx, x, y, CONTENT_W, 5, 2.5, trackColor);
    fillRounded(ctx, x, y, CONTENT_W * 0.5, 5, 2.5, p.primary);

    y += 58;

    // Back Arrow + 32sp Bold Title "Select your SIM"
    ctx.strokeStyle = p.ink;
    ctx.lineWidth = 4.2;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(x + 28, y + 6);
    ctx.lineTo(x + 8, y + 6);
    ctx.moveTo(x + 17, y - 4);
    ctx.lineTo(x + 7, y + 6);
    ctx.lineTo(x + 17, y + 16);
    ctx.stroke();

    ctx.fillStyle = p.ink;
    ctx.font = `700 46px ${FONT_STACK}`;
    ctx.textBaseline = 'middle';
    ctx.textAlign = 'left';
    ctx.fillText('Select your SIM', x + 56, y + 7);

    y += 88;

    // SetupFieldLabel ("Primary SIM")
    ctx.fillStyle = p.inkMuted;
    ctx.font = `500 24px ${FONT_STACK}`;
    ctx.fillText('Primary SIM', x, y);

    y += 38;

    const carriers: Array<{
      key: 'airtel' | 'vi' | 'bsnl' | 'jio';
      title: string;
      sub: string;
      picked: boolean;
    }> = [
      { key: 'airtel', title: 'Airtel', sub: 'Airtel India', picked: true },
      { key: 'vi', title: 'Vodafone Idea', sub: 'Vi India', picked: false },
      { key: 'bsnl', title: 'BSNL', sub: 'Bharat Sanchar Nigam Limited', picked: false },
      { key: 'jio', title: 'Reliance Jio', sub: 'Jio 4G / 5G', picked: false }
    ];

    const unselectedFill = theme === 'dark' ? 'rgba(23, 32, 44, 0.65)' : 'rgba(241, 245, 249, 0.85)';

    for (const c of carriers) {
      const cardH = 148;
      fillRounded(
        ctx,
        x,
        y,
        CONTENT_W,
        cardH,
        30,
        c.picked ? 'rgba(0, 135, 116, 0.10)' : unselectedFill,
        c.picked ? p.primary : p.outline,
        c.picked ? 3.5 : 1.8
      );

      // Exact SimOperatorLogoBadge circles from SimOperatorLogoBadge.kt
      const bx = x + 66;
      const by = y + cardH / 2;
      this.drawSimBadge(ctx, c.key, bx, by);

      ctx.fillStyle = p.ink;
      ctx.font = `700 29px ${FONT_STACK}`;
      ctx.textAlign = 'left';
      ctx.fillText(c.title, x + 132, by - 17);

      ctx.fillStyle = p.inkMuted;
      ctx.font = `500 22px ${FONT_STACK}`;
      ctx.fillText(c.sub, x + 132, by + 18);

      // Selected check circle vs empty radio ring (SetupActivity.kt L464-484)
      const rx = x + CONTENT_W - 48;
      if (c.picked) {
        ctx.beginPath();
        ctx.arc(rx, by, 22, 0, Math.PI * 2);
        ctx.fillStyle = p.primary;
        ctx.fill();

        ctx.strokeStyle = '#FFFFFF';
        ctx.lineWidth = 4;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        ctx.beginPath();
        ctx.moveTo(rx - 9, by + 1);
        ctx.lineTo(rx - 2, by + 8);
        ctx.lineTo(rx + 10, by - 6);
        ctx.stroke();
      } else {
        ctx.beginPath();
        ctx.arc(rx, by, 21, 0, Math.PI * 2);
        ctx.strokeStyle = p.outline;
        ctx.lineWidth = 3.2;
        ctx.stroke();
      }

      y += cardH + 22;
    }

    y += 34;

    // Dual SIM checkbox row ("Enable Dual SIM support")
    ctx.beginPath();
    ctx.arc(x + 22, y + 20, 19, 0, Math.PI * 2);
    ctx.strokeStyle = p.inkMuted;
    ctx.lineWidth = 3.2;
    ctx.stroke();

    ctx.fillStyle = p.ink;
    ctx.font = `500 25px ${FONT_STACK}`;
    ctx.textAlign = 'left';
    ctx.fillText('Enable Dual SIM support', x + 62, y + 21);

    // Bottom full-width CompleteSetupButton ("Continue ->")
    const btnY = CANVAS_H - 186;
    const btnH = 104;
    const btnGrad = ctx.createLinearGradient(x, btnY, x + CONTENT_W, btnY + btnH);
    btnGrad.addColorStop(0, p.primary);
    btnGrad.addColorStop(1, p.primaryDark);
    fillRounded(ctx, x, btnY, CONTENT_W, btnH, 28, btnGrad, 'rgba(255, 255, 255, 0.18)', 2);

    ctx.fillStyle = '#FFFFFF';
    ctx.font = `600 31px ${FONT_STACK}`;
    ctx.textAlign = 'center';
    ctx.fillText('Continue  →', CANVAS_W / 2, btnY + btnH / 2 + 1);
  }

  // SCREEN 2: QRScannerActivity.kt ScannerOverlay (Minimal back arrow + clear 260dp viewfinder cutout + laser gradient sheet + 2 bottom circles)
  private drawQrScannerScreen(
    ctx: CanvasRenderingContext2D,
    p: ScreenPalette,
    theme: 'light' | 'dark',
    timeSec: number,
    reducedMotion: boolean
  ): void {
    const isLight = theme === 'light';

    if (isLight) {
      // Light theme adaptation: bright surface contours & light frosted camera scrim around the clear viewfinder cutout
      this.drawBackgroundAndContours(ctx, p);
      ctx.fillStyle = 'rgba(241, 245, 249, 0.55)';
      ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);
    } else {
      const bg = ctx.createLinearGradient(0, 0, 0, CANVAS_H);
      bg.addColorStop(0, '#070B12');
      bg.addColorStop(0.5, '#0D1520');
      bg.addColorStop(1, '#070B12');
      ctx.fillStyle = bg;
      ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);
    }

    const boxSize = 480;
    const rx = (CANVAS_W - boxSize) / 2;
    const ry = (CANVAS_H - boxSize) / 2 - 20;

    // Clear viewfinder cutout window + outer scrim (matches Canvas BlendMode.Clear in QRScannerActivity.kt L1006-1012)
    if (isLight) {
      ctx.fillStyle = '#FFFFFF';
      ctx.fillRect(rx, ry, boxSize, boxSize);
    } else {
      ctx.fillStyle = 'rgba(0, 0, 0, 0.42)';
      ctx.fillRect(rx, ry, boxSize, boxSize);
    }

    // Simulated merchant UPI QR target aligned cleanly inside the clear viewfinder
    const qrCells = 11;
    const cell = 30;
    const qrDim = qrCells * cell;
    const qrX = (CANVAS_W - qrDim) / 2;
    const qrY = ry + (boxSize - qrDim) / 2;
    const qrColor = isLight ? 'rgba(15, 23, 42, 0.88)' : 'rgba(241, 245, 249, 0.88)';
    const pattern = [
      [1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1],
      [1, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1],
      [1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1],
      [0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0],
      [1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 1],
      [0, 1, 0, 1, 1, 0, 1, 1, 0, 1, 0],
      [1, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1],
      [0, 0, 0, 0, 1, 0, 1, 0, 0, 1, 0],
      [1, 1, 1, 0, 0, 1, 1, 0, 1, 0, 1],
      [1, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0],
      [1, 1, 1, 0, 1, 1, 1, 0, 1, 0, 1]
    ];
    ctx.fillStyle = qrColor;
    for (let r = 0; r < qrCells; r++) {
      for (let c = 0; c < qrCells; c++) {
        if (pattern[r][c]) {
          fillRounded(ctx, qrX + c * cell + 2, qrY + r * cell + 2, cell - 4, cell - 4, 4, qrColor);
        }
      }
    }

    // 28dp (~52px) Gradient Laser Sheet with bright leading bottom edge (QRScannerActivity.kt L1049-1082)
    const laserH = 52;
    const travel = boxSize - laserH;
    const progress = reducedMotion ? 0.52 : (Math.sin(timeSec * 2.4) + 1) * 0.5;
    const laserTop = ry + progress * travel;

    const sheetGrad = ctx.createLinearGradient(0, laserTop, 0, laserTop + laserH);
    sheetGrad.addColorStop(0, 'rgba(0, 135, 116, 0)');
    sheetGrad.addColorStop(0.55, 'rgba(0, 135, 116, 0.14)');
    sheetGrad.addColorStop(1, 'rgba(0, 135, 116, 0.42)');
    ctx.fillStyle = sheetGrad;
    ctx.fillRect(rx + 4, laserTop, boxSize - 8, laserH);

    const edgeGrad = ctx.createLinearGradient(rx, 0, rx + boxSize, 0);
    edgeGrad.addColorStop(0, 'rgba(0, 135, 116, 0)');
    edgeGrad.addColorStop(0.25, p.primary);
    edgeGrad.addColorStop(0.5, isLight ? '#006D5D' : '#FFFFFF');
    edgeGrad.addColorStop(0.75, p.primary);
    edgeGrad.addColorStop(1, 'rgba(0, 135, 116, 0)');
    ctx.fillStyle = edgeGrad;
    ctx.fillRect(rx + 4, laserTop + laserH - 5, boxSize - 8, 5);

    // Crisp square L-brackets (bracketLen = 32dp ~ 60px, strokeWidth = 4dp ~ 7.5px)
    const bracketColor = isLight ? p.primary : '#FFFFFF';
    const arm = 60;
    ctx.strokeStyle = bracketColor;
    ctx.lineWidth = 7.5;
    ctx.lineCap = 'square';
    const corners: Array<[number, number, number, number]> = [
      [rx, ry, 1, 1],
      [rx + boxSize, ry, -1, 1],
      [rx, ry + boxSize, 1, -1],
      [rx + boxSize, ry + boxSize, -1, -1]
    ];
    for (const [cx, cy, dx, dy] of corners) {
      ctx.beginPath();
      ctx.moveTo(cx, cy + dy * arm);
      ctx.lineTo(cx, cy);
      ctx.lineTo(cx + dx * arm, cy);
      ctx.stroke();
    }

    const fgColor = isLight ? p.ink : '#FFFFFF';
    this.drawStatusBar(ctx, fgColor);

    // Top bar: ONLY Back arrow icon on top-left (QRScannerActivity.kt L1087-1094)
    ctx.strokeStyle = fgColor;
    ctx.lineWidth = 4.6;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(PAD_X + 26, 166);
    ctx.lineTo(PAD_X + 4, 166);
    ctx.moveTo(PAD_X + 14, 155);
    ctx.lineTo(PAD_X + 3, 166);
    ctx.lineTo(PAD_X + 14, 177);
    ctx.stroke();

    // Bottom Actions: Two 56dp (~104px) circular buttons ONLY, SpaceEvenly (QRScannerActivity.kt L1116-1132)
    const btnY = CANVAS_H - 210;
    const btnCircleBg = isLight ? p.surfaceGlass : 'rgba(255, 255, 255, 0.20)';
    const btnCircleStroke = isLight ? p.surfaceGlassBorder : undefined;
    const offsets = [-144, 144];
    offsets.forEach((off, idx) => {
      const bx = CANVAS_W / 2 + off;
      fillRounded(ctx, bx - 52, btnY - 52, 104, 104, 52, btnCircleBg, btnCircleStroke, 2);
      if (idx === 0) {
        this.drawFlashGlyph(ctx, bx, btnY, fgColor);
      } else {
        this.drawGalleryGlyph(ctx, bx, btnY, fgColor);
      }
    });
  }

  // SCREEN 3: PaymentResultActivity.kt (Checkmark burst + "Payment Successful" + HUGE #008774 "₹48.00" + Horizontal Receipt Rows + 12dp "Done" button)
  private drawSuccessScreen(
    ctx: CanvasRenderingContext2D,
    p: ScreenPalette,
    theme: 'light' | 'dark',
    timeSec: number,
    reducedMotion: boolean
  ): void {
    const cx = CANVAS_W / 2;
    let y = 264;

    // Gentle expanding burst halo + confetti dots around SmoothCheckmarkAnimation
    const pulse = reducedMotion ? 0.5 : (Math.sin(timeSec * 3.0) + 1) * 0.5;
    const halo = ctx.createRadialGradient(cx, y, 30, cx, y, 130 + pulse * 16);
    halo.addColorStop(0, 'rgba(0, 135, 116, 0.24)');
    halo.addColorStop(1, 'rgba(0, 135, 116, 0)');
    ctx.fillStyle = halo;
    ctx.beginPath();
    ctx.arc(cx, y, 130 + pulse * 16, 0, Math.PI * 2);
    ctx.fill();

    // Crisp subtle celebration burst specks matching SmoothCheckmarkAnimation colour burst
    const dotAngles = [0.4, 1.15, 2.0, 2.75, 3.6, 4.35, 5.15, 5.9];
    dotAngles.forEach((ang, i) => {
      const r = 98 + (i % 2) * 14 + pulse * 6;
      ctx.beginPath();
      ctx.arc(cx + Math.cos(ang) * r, y + Math.sin(ang) * r, i % 2 === 0 ? 5 : 3.5, 0, Math.PI * 2);
      ctx.fillStyle = i % 3 === 0 ? p.primary : 'rgba(0, 163, 141, 0.55)';
      ctx.fill();
    });

    // Solid #008774 circle with crisp white checkmark
    ctx.beginPath();
    ctx.arc(cx, y, 76, 0, Math.PI * 2);
    ctx.fillStyle = p.primary;
    ctx.fill();

    ctx.strokeStyle = '#FFFFFF';
    ctx.lineWidth = 10.5;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(cx - 30, y + 3);
    ctx.lineTo(cx - 7, y + 26);
    ctx.lineTo(cx + 35, y - 22);
    ctx.stroke();

    // "Payment Successful" (24sp Bold)
    y += 126;
    ctx.fillStyle = p.ink;
    ctx.font = `700 42px ${FONT_STACK}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('Payment Successful', cx, y);

    // CurrencyText ("₹48.00", 40sp Bold, accent.primary #008774!)
    y += 88;
    ctx.fillStyle = p.primary;
    ctx.font = `700 74px ${FONT_STACK}`;
    ctx.fillText('₹48.00', cx, y);

    y += 96;

    // Receipt Card (surfaceVariant @ 60%, 20dp radius, 1dp outline @ 20%)
    const x = PAD_X;
    const cardFill = theme === 'dark' ? 'rgba(23, 32, 44, 0.68)' : 'rgba(241, 245, 249, 0.85)';
    const dividerCol = theme === 'dark' ? 'rgba(51, 67, 86, 0.45)' : 'rgba(203, 213, 225, 0.55)';
    const cardH = 428;
    fillRounded(ctx, x, y, CONTENT_W, cardH, 36, cardFill, p.outline, 1.8);

    // Horizontal DetailReceiptRow pairs (label LEFT, value RIGHT-aligned — PaymentResultActivity.kt L227-308)
    const pairs: Array<{ label: string; value: string; bold?: boolean }> = [
      { label: 'Paid To', value: 'Kiran Kirana Store' },
      { label: 'UPI ID', value: 'kirankirana@okaxis' },
      { label: 'Amount Paid', value: '₹48.00', bold: true }
    ];

    pairs.forEach((row, idx) => {
      const ry = y + 54 + idx * 96;
      ctx.fillStyle = p.inkMuted;
      ctx.font = `500 23px ${FONT_STACK}`;
      ctx.textAlign = 'left';
      ctx.fillText(row.label, x + 36, ry);

      ctx.fillStyle = p.ink;
      ctx.font = `${row.bold ? '700' : '600'} 25px ${FONT_STACK}`;
      ctx.textAlign = 'right';
      ctx.fillText(row.value, x + CONTENT_W - 36, ry);

      ctx.fillStyle = dividerCol;
      ctx.fillRect(x + 36, ry + 48, CONTENT_W - 72, 1.5);
    });

    // Final row: Stacked "Transaction ID" + "UPI4928173492" left, teal ContentCopy icon right
    const txnY = y + 344;
    ctx.fillStyle = p.inkMuted;
    ctx.font = `500 23px ${FONT_STACK}`;
    ctx.textAlign = 'left';
    ctx.fillText('Transaction ID', x + 36, txnY);

    ctx.fillStyle = p.ink;
    ctx.font = `600 24px ${MONO_STACK}`;
    ctx.fillText('UPI4928173492', x + 36, txnY + 38);

    this.drawCopyGlyph(ctx, x + CONTENT_W - 52, txnY + 18, p.primary);

    // Bottom 52dp button: RoundedCornerShape(12dp) -> ~24px corner radius, accent.primary fill, bold white "Done"
    const btnY = CANVAS_H - 186;
    fillRounded(ctx, x, btnY, CONTENT_W, 98, 22, p.primary);
    ctx.fillStyle = '#FFFFFF';
    ctx.font = `700 29px ${FONT_STACK}`;
    ctx.textAlign = 'center';
    ctx.fillText('Done', cx, btnY + 50);
  }

  private drawSimBadge(
    ctx: CanvasRenderingContext2D,
    carrier: 'airtel' | 'vi' | 'bsnl' | 'jio',
    cx: number,
    cy: number
  ): void {
    const r = 42;
    ctx.save();
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';

    if (carrier === 'airtel') {
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fillStyle = '#E10600';
      ctx.fill();
      ctx.fillStyle = '#FFFFFF';
      ctx.font = `700 24px ${FONT_STACK}`;
      ctx.fillText('airtel', cx, cy + 1);
    } else if (carrier === 'vi') {
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fillStyle = '#FFFFFF';
      ctx.fill();
      ctx.lineWidth = 2;
      ctx.strokeStyle = '#E0E0E0';
      ctx.stroke();
      ctx.fillStyle = '#E60000';
      ctx.font = `800 34px ${FONT_STACK}`;
      ctx.fillText('Vi', cx, cy + 1);
    } else if (carrier === 'bsnl') {
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fillStyle = '#FFFFFF';
      ctx.fill();
      ctx.lineWidth = 2;
      ctx.strokeStyle = '#E0E0E0';
      ctx.stroke();
      ctx.fillStyle = '#003399';
      ctx.font = `800 20px ${FONT_STACK}`;
      ctx.fillText('BSNL', cx, cy + 1);
    } else {
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fillStyle = '#0A2885';
      ctx.fill();
      ctx.fillStyle = '#FFFFFF';
      ctx.font = `italic 800 30px ${FONT_STACK}`;
      ctx.fillText('Jio', cx, cy + 1);
    }
    ctx.restore();
  }

  private drawWalletGlyph(ctx: CanvasRenderingContext2D, cx: number, cy: number, color: string): void {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 2.8;
    roundRect(ctx, cx - 13, cy - 10, 26, 20, 4);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(cx + 6, cy, 2.8, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();
    ctx.restore();
  }

  private drawQrGlyph(ctx: CanvasRenderingContext2D, cx: number, cy: number, color: string): void {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.fillStyle = color;
    ctx.lineWidth = 4.6;
    roundRect(ctx, cx - 32, cy - 32, 24, 24, 5);
    ctx.stroke();
    roundRect(ctx, cx + 8, cy - 32, 24, 24, 5);
    ctx.stroke();
    roundRect(ctx, cx - 32, cy + 8, 24, 24, 5);
    ctx.stroke();
    ctx.fillRect(cx + 10, cy + 10, 10, 10);
    ctx.fillRect(cx + 22, cy + 22, 10, 10);
    ctx.restore();
  }

  private drawContactGlyph(ctx: CanvasRenderingContext2D, cx: number, cy: number, color: string): void {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 4.6;
    ctx.beginPath();
    ctx.arc(cx, cy - 15, 14, 0, Math.PI * 2);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(cx, cy + 32, 26, Math.PI * 1.15, Math.PI * 1.85);
    ctx.stroke();
    ctx.restore();
  }

  private drawRechargeGlyph(ctx: CanvasRenderingContext2D, cx: number, cy: number, color: string): void {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 4.4;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    roundRect(ctx, cx - 19, cy - 31, 38, 62, 8);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(cx + 4, cy - 13);
    ctx.lineTo(cx - 6, cy + 1);
    ctx.lineTo(cx + 5, cy + 1);
    ctx.lineTo(cx - 3, cy + 15);
    ctx.stroke();
    ctx.restore();
  }

  private drawGiftGlyph(ctx: CanvasRenderingContext2D, cx: number, cy: number, color: string): void {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 3.8;
    roundRect(ctx, cx - 18, cy - 6, 36, 24, 5);
    ctx.stroke();
    roundRect(ctx, cx - 21, cy - 14, 42, 10, 4);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(cx, cy - 14);
    ctx.lineTo(cx, cy + 18);
    ctx.stroke();
    ctx.restore();
  }

  private drawHomeGlyph(ctx: CanvasRenderingContext2D, cx: number, cy: number, color: string): void {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 3.2;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(cx - 15, cy - 2);
    ctx.lineTo(cx, cy - 15);
    ctx.lineTo(cx + 15, cy - 2);
    ctx.lineTo(cx + 15, cy + 13);
    ctx.lineTo(cx + 5, cy + 13);
    ctx.lineTo(cx + 5, cy + 3);
    ctx.lineTo(cx - 5, cy + 3);
    ctx.lineTo(cx - 5, cy + 13);
    ctx.lineTo(cx - 15, cy + 13);
    ctx.closePath();
    ctx.stroke();
    ctx.restore();
  }

  private drawHistoryGlyph(ctx: CanvasRenderingContext2D, cx: number, cy: number, color: string): void {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 3.2;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.arc(cx, cy, 14, 0, Math.PI * 2);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(cx, cy - 7);
    ctx.lineTo(cx, cy + 1);
    ctx.lineTo(cx + 6, cy + 4);
    ctx.stroke();
    ctx.restore();
  }

  private drawSettingsGlyph(ctx: CanvasRenderingContext2D, cx: number, cy: number, color: string): void {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 3.0;
    ctx.beginPath();
    ctx.arc(cx, cy, 6, 0, Math.PI * 2);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(cx, cy, 12, 0, Math.PI * 2);
    ctx.setLineDash([4.5, 4.5]);
    ctx.stroke();
    ctx.restore();
  }

  private drawFlashGlyph(ctx: CanvasRenderingContext2D, cx: number, cy: number, color: string): void {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 3.6;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(cx + 4, cy - 18);
    ctx.lineTo(cx - 13, cy + 2);
    ctx.lineTo(cx - 1, cy + 2);
    ctx.lineTo(cx - 5, cy + 18);
    ctx.lineTo(cx + 13, cy - 2);
    ctx.lineTo(cx + 1, cy - 2);
    ctx.closePath();
    ctx.stroke();
    ctx.restore();
  }

  private drawGalleryGlyph(ctx: CanvasRenderingContext2D, cx: number, cy: number, color: string): void {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.fillStyle = color;
    ctx.lineWidth = 3.4;
    ctx.lineJoin = 'round';
    roundRect(ctx, cx - 18, cy - 15, 36, 30, 6);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(cx - 7, cy - 4, 3.2, 0, Math.PI * 2);
    ctx.fill();
    ctx.beginPath();
    ctx.moveTo(cx - 13, cy + 11);
    ctx.lineTo(cx - 2, cy);
    ctx.lineTo(cx + 6, cy + 8);
    ctx.lineTo(cx + 11, cy + 3);
    ctx.lineTo(cx + 15, cy + 11);
    ctx.stroke();
    ctx.restore();
  }

  private drawCopyGlyph(ctx: CanvasRenderingContext2D, cx: number, cy: number, color: string): void {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 3.2;
    ctx.lineJoin = 'round';
    roundRect(ctx, cx - 7, cy - 7, 18, 22, 3);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(cx - 13, cy + 9);
    ctx.lineTo(cx - 13, cy - 14);
    ctx.lineTo(cx + 5, cy - 14);
    ctx.stroke();
    ctx.restore();
  }

  public dispose(): void {
    this.texture.dispose();
  }
}
