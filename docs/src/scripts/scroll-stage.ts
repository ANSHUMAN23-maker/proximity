import { scrambleElement } from './scramble';

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v));
}

/**
 * Extended zigzag walkthrough controller for #flow & sticky #story-phone.
 * - Delays #story-phone entrance until scrolling past full-width #hero into #flow Step 01.
 * - Alternates #story-phone smoothly Right -> Left -> Right -> Left across generous 900px gaps.
 * - Sharpens active borderless beat text from blur and scrambles its monospace eyebrow.
 */
export function initScrollStages(reducedMotion: boolean): void {
  const flowSection = document.getElementById('flow');
  const zigzagStage = document.getElementById('flow-zigzag-stage');
  const stageInner = document.getElementById('phone-stage-inner');
  const storyPhone = document.getElementById('story-phone');

  const beatCards = Array.from(document.querySelectorAll<HTMLElement>('.story-beat[data-story-beat]'));
  const screens = Array.from(document.querySelectorAll<HTMLElement>('.stage-screen[data-screen]'));

  if (!flowSection || beatCards.length === 0 || screens.length === 0) return;

  let activeBeat = -1;
  let manualOverrideUntil = 0;

  const applyBeat = (next: number, animateScramble: boolean) => {
    const idx = clamp(next, 0, Math.min(beatCards.length, screens.length) - 1);
    const changed = idx !== activeBeat;
    activeBeat = idx;

    beatCards.forEach((card, i) => card.classList.toggle('is-active', i === idx));
    screens.forEach((screen, i) => screen.classList.toggle('is-active', i === idx));

    if (changed && animateScramble) {
      const eyebrow = beatCards[idx]?.querySelector('[data-beat-eyebrow]') ?? null;
      scrambleElement(eyebrow, undefined, 420, reducedMotion);
    }
  };

  beatCards.forEach((card) => {
    const idx = clamp(Number(card.dataset.storyBeat ?? 0), 0, screens.length - 1);

    const activate = () => {
      manualOverrideUntil = performance.now() + 900;
      applyBeat(idx, true);
      if (storyPhone) {
        storyPhone.style.setProperty('--phone-enter', '1');
      }
      if (window.innerWidth <= 960 && storyPhone) {
        storyPhone.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth', block: 'center' });
      } else {
        card.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth', block: 'center' });
      }
    };

    card.addEventListener('click', activate);
  });

  // Subtle fine-pointer 3D tilt when hovering #flow on desktop
  const finePointer = window.matchMedia('(hover: hover) and (pointer: fine)');
  if (!reducedMotion && finePointer.matches && storyPhone) {
    let targetRx = 2;
    let targetRy = -5;
    let currRx = 2;
    let currRy = -5;
    let hovering = false;

    const tickTilt = () => {
      currRx += (targetRx - currRx) * 0.14;
      currRy += (targetRy - currRy) * 0.14;
      storyPhone.style.setProperty('--tilt-rx', `${currRx.toFixed(2)}deg`);
      storyPhone.style.setProperty('--tilt-ry', `${currRy.toFixed(2)}deg`);

      if (hovering || Math.abs(targetRx - currRx) > 0.04 || Math.abs(targetRy - currRy) > 0.04) {
        requestAnimationFrame(tickTilt);
      }
    };

    flowSection.addEventListener(
      'pointermove',
      (e) => {
        const rect = flowSection.getBoundingClientRect();
        const nx = clamp((e.clientX - rect.left) / rect.width - 0.5, -0.5, 0.5);
        const ny = clamp((e.clientY - rect.top) / rect.height - 0.5, -0.5, 0.5);
        targetRx = 2 - ny * 8;
        targetRy = -5 + nx * 11;
        if (!hovering) {
          hovering = true;
          requestAnimationFrame(tickTilt);
        }
      },
      { passive: true }
    );

    flowSection.addEventListener('pointerleave', () => {
      hovering = false;
      targetRx = 2;
      targetRy = -5;
      requestAnimationFrame(tickTilt);
    });
  }

  if (reducedMotion) {
    if (storyPhone) {
      storyPhone.style.setProperty('--phone-enter', '1');
      storyPhone.style.setProperty('--tilt-rx', '0deg');
      storyPhone.style.setProperty('--tilt-ry', '0deg');
    }
    if (stageInner) {
      stageInner.style.setProperty('--phone-side-x', '0px');
    }
    applyBeat(0, false);
    return;
  }

  let rafId = 0;

  const updateFromScroll = () => {
    rafId = 0;
    const vh = window.innerHeight || 800;
    const vw = window.innerWidth || 1280;
    const flowRect = flowSection.getBoundingClientRect();
    const isDesktop = vw > 960;

    // 1. Delayed phone entrance: invisible over #hero, glides up as #flow enters view
    if (storyPhone) {
      if (!isDesktop) {
        storyPhone.style.setProperty('--phone-enter', '1');
      } else {
        const enterP = clamp((vh * 0.88 - flowRect.top) / (vh * 0.56), 0, 1);
        storyPhone.style.setProperty('--phone-enter', enterP.toFixed(3));
      }
    }

    // 2. Beat positions across the tall zigzag layout
    const beatCenters = beatCards.map((card) => {
      const r = card.getBoundingClientRect();
      return r.top + window.scrollY + r.height * 0.5;
    });

    const anchorDocY = window.scrollY + vh * 0.48;
    const firstCenter = beatCenters[0] ?? 0;
    const lastCenter = beatCenters[beatCenters.length - 1] ?? firstCenter + 1;
    const span = Math.max(1, lastCenter - firstCenter);
    const walkP = clamp((anchorDocY - firstCenter) / span, 0, 1);

    // 3. Determine active beat closest to viewport focus anchor
    if (performance.now() > manualOverrideUntil) {
      let bestIdx = 0;
      let bestDist = Infinity;
      beatCenters.forEach((center, i) => {
        const dist = Math.abs(center - anchorDocY);
        if (dist < bestDist) {
          bestDist = dist;
          bestIdx = i;
        }
      });
      if (bestIdx !== activeBeat) {
        applyBeat(bestIdx, true);
      }
    }

    // 4. Desktop Left <-> Right smooth glide between beats
    if (stageInner) {
      if (!isDesktop || beatCenters.length < 2) {
        stageInner.style.setProperty('--phone-side-x', '0px');
      } else {
        const stageW = zigzagStage?.clientWidth ?? Math.min(1132, vw - 48);
        const sideOffset = clamp(stageW * 0.24, 195, 268);

        let sideMult = 1; // Beat 0 = text left, phone right (+1)
        let glideVel = 0;

        if (anchorDocY <= beatCenters[0]) {
          sideMult = 1;
        } else if (anchorDocY >= beatCenters[beatCenters.length - 1]) {
          sideMult = (beatCenters.length - 1) % 2 === 0 ? 1 : -1;
        } else {
          for (let i = 0; i < beatCenters.length - 1; i++) {
            const c0 = beatCenters[i];
            const c1 = beatCenters[i + 1];
            if (anchorDocY >= c0 && anchorDocY <= c1) {
              const segT = clamp((anchorDocY - c0) / Math.max(1, c1 - c0), 0, 1);
              // Hold steady while reading each beat (first/last 14%), smoothly glide across the 900px gap
              const moveT = clamp((segT - 0.14) / 0.72, 0, 1);
              const eased = 0.5 - 0.5 * Math.cos(Math.PI * moveT);
              const fromSide = i % 2 === 0 ? 1 : -1;
              const toSide = (i + 1) % 2 === 0 ? 1 : -1;
              sideMult = fromSide + (toSide - fromSide) * eased;
              glideVel = (toSide - fromSide) * Math.sin(moveT * Math.PI);
              break;
            }
          }
        }

        const sideX = sideMult * sideOffset;
        stageInner.style.setProperty('--phone-side-x', `${sideX.toFixed(1)}px`);

        if (storyPhone) {
          // Subtle directional lean during cross-stage glide + gentle vertical depth breathing
          const orbitRy = -sideMult * 4.5 + glideVel * 6.5;
          const orbitRx = Math.cos(walkP * Math.PI * 2) * 2.2;
          storyPhone.style.setProperty('--orbit-ry', `${orbitRy.toFixed(2)}deg`);
          storyPhone.style.setProperty('--orbit-rx', `${orbitRx.toFixed(2)}deg`);
        }
      }
    }
  };

  const schedule = () => {
    if (!rafId) {
      rafId = requestAnimationFrame(updateFromScroll);
    }
  };

  window.addEventListener('scroll', schedule, { passive: true });
  window.addEventListener('resize', schedule, { passive: true });
  applyBeat(0, false);
  updateFromScroll();

  // Lazily mount the interactive Three.js 3D phone once #flow approaches the viewport or after initial idle
  let phone3dBooted = false;
  const bootPhone3d = () => {
    if (phone3dBooted) return;
    phone3dBooted = true;
    void import('./phone-3d')
      .then((mod) => mod.initPhone3d())
      .catch(() => {
        // Keep crisp 2D CSS chassis fallback silently if WebGL/asset loading fails
      });
  };

  if ('IntersectionObserver' in window) {
    const flowObserver = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          flowObserver.disconnect();
          bootPhone3d();
        }
      },
      { rootMargin: '850px 0px' }
    );
    flowObserver.observe(flowSection);
  }

  if (document.readyState === 'complete') {
    window.setTimeout(bootPhone3d, 900);
  } else {
    window.addEventListener(
      'load',
      () => {
        window.setTimeout(bootPhone3d, 900);
      },
      { once: true }
    );
  }
}
