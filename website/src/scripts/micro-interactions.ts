/**
 * Subtle magnetic cursor pull on key actions + tactile expanding click ripples
 * on buttons, theme toggles, and FAQ accordions.
 */
export function initMicroInteractions(reducedMotion: boolean): void {
  if (reducedMotion) return;

  const finePointer = window.matchMedia('(hover: hover) and (pointer: fine)').matches;

  if (finePointer) {
    const magneticEls = document.querySelectorAll<HTMLElement>(
      '.btn-primary, .btn-secondary, .animated-button, .nav-cta, .theme-toggle'
    );

    magneticEls.forEach((el) => {
      el.classList.add('is-magnetic');

      el.addEventListener(
        'pointermove',
        (e) => {
          const rect = el.getBoundingClientRect();
          const dx = (e.clientX - (rect.left + rect.width / 2)) / Math.max(1, rect.width / 2);
          const dy = (e.clientY - (rect.top + rect.height / 2)) / Math.max(1, rect.height / 2);
          const maxShift = el.classList.contains('theme-toggle') ? 3.5 : 5;
          el.style.setProperty('--mag-x', `${(dx * maxShift).toFixed(2)}px`);
          el.style.setProperty('--mag-y', `${(dy * maxShift).toFixed(2)}px`);
        },
        { passive: true }
      );

      el.addEventListener('pointerleave', () => {
        el.style.setProperty('--mag-x', '0px');
        el.style.setProperty('--mag-y', '0px');
      });
    });
  }

  document.addEventListener('pointerdown', (e) => {
    if (e.button !== 0) return;
    const target = (e.target as Element | null)?.closest<HTMLElement>(
      '.btn-primary, .btn-secondary, .nav-cta, .theme-toggle, .faq-trigger'
    );
    if (!target) return;

    const rect = target.getBoundingClientRect();
    const size = Math.max(rect.width, rect.height) * 1.45;
    const x = (e.clientX || rect.left + rect.width / 2) - rect.left - size / 2;
    const y = (e.clientY || rect.top + rect.height / 2) - rect.top - size / 2;

    const ripple = document.createElement('span');
    ripple.className = 'tap-ripple';
    ripple.setAttribute('aria-hidden', 'true');
    ripple.style.width = `${size.toFixed(0)}px`;
    ripple.style.height = `${size.toFixed(0)}px`;
    ripple.style.left = `${x.toFixed(1)}px`;
    ripple.style.top = `${y.toFixed(1)}px`;

    target.appendChild(ripple);
    ripple.addEventListener('animationend', () => ripple.remove(), { once: true });
    window.setTimeout(() => ripple.remove(), 650);
  });
}

function getOrCreatePulseLayer(): HTMLElement {
  let layer = document.getElementById('theme-pulse-layer');
  if (!layer) {
    layer = document.createElement('div');
    layer.id = 'theme-pulse-layer';
    layer.className = 'theme-pulse-layer';
    layer.setAttribute('aria-hidden', 'true');
    document.body.appendChild(layer);
  }
  return layer;
}

/**
 * Emits a slow dual-ring radial pulse expanding across the viewport from the clicked theme button.
 */
export function triggerThemePulse(
  originButton: HTMLElement,
  nextTheme: 'light' | 'dark',
  reducedMotion: boolean
): void {
  if (reducedMotion) return;

  originButton.classList.remove('is-pulsing');
  void originButton.offsetWidth;
  originButton.classList.add('is-pulsing');
  window.setTimeout(() => originButton.classList.remove('is-pulsing'), 1450);

  const rect = originButton.getBoundingClientRect();
  const cx = rect.left + rect.width / 2;
  const cy = rect.top + rect.height / 2;
  const vw = window.innerWidth || document.documentElement.clientWidth || 1280;
  const vh = window.innerHeight || document.documentElement.clientHeight || 800;
  const maxR = Math.hypot(Math.max(cx, vw - cx), Math.max(cy, vh - cy));
  const diameter = Math.max(240, Math.ceil(maxR * 2.15));

  const layer = getOrCreatePulseLayer();
  while (layer.children.length >= 4) {
    layer.firstElementChild?.remove();
  }

  const spawnRing = (className: string, ttlMs: number) => {
    const wave = document.createElement('span');
    wave.className = className;
    wave.setAttribute('data-theme-pulse', nextTheme);
    wave.style.width = `${diameter}px`;
    wave.style.height = `${diameter}px`;
    wave.style.left = `${cx.toFixed(1)}px`;
    wave.style.top = `${cy.toFixed(1)}px`;
    layer.appendChild(wave);
    wave.addEventListener('animationend', () => wave.remove(), { once: true });
    window.setTimeout(() => wave.remove(), ttlMs);
  };

  spawnRing('theme-pulse-wave', 1750);
  spawnRing('theme-pulse-echo', 2150);
}
