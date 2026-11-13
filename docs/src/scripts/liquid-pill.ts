import { RAIL_SPECS } from './data';
import { scrambleElement } from './scramble';

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v));
}

const MAX_PEEK_PX = 5;

/**
 * Interactive Liquid-Glass capsule playground outside the phone.
 * Supports clicking/tapping any slot OR dragging/flicking with bounded 4-6px elastic peek,
 * and morphs the single horizontal rail banner card with a synchronized cipher scramble.
 */
export function initLiquidDock(reducedMotion: boolean): void {
  const dock = document.getElementById('liquid-dock');
  const puck = document.getElementById('liquid-dock-puck');
  const tabs = Array.from(document.querySelectorAll<HTMLButtonElement>('.dock-tab[data-rail-tab]'));
  const bannerCard = document.getElementById('rail-detail-card');
  const bannerIcon = document.getElementById('rail-banner-icon');
  const bannerEyebrow = document.getElementById('rail-banner-eyebrow');
  const bannerTitle = document.getElementById('rail-banner-title');
  const bannerDesc = document.getElementById('rail-banner-desc');
  const bannerSpec = document.getElementById('rail-banner-spec');

  if (!dock || !puck || tabs.length === 0) return;

  const maxIndex = tabs.length - 1;
  let currPos = 0;
  let targetPos = 0;
  let velPos = 0;
  let pressScale = 1;
  let targetPressScale = 1;
  let activeIndex = -1;

  let pointerDown = false;
  let hasCaptured = false;
  let activePointerId = -1;
  let dragStartClientX = 0;
  let dragStartPos = 0;
  let lastClientX = 0;
  let lastTime = 0;
  let dampedVelocity = 0;
  let movedDistance = 0;
  let suppressNextClick = false;
  let rafId = 0;

  const getSlotWidth = (): number => {
    const innerWidth = Math.max(120, dock.clientWidth - 12);
    return innerWidth / tabs.length;
  };

  const renderBanner = (index: number, animateScramble: boolean) => {
    const rail = RAIL_SPECS[index] ?? RAIL_SPECS[0];
    if (!rail) return;

    if (bannerCard) {
      bannerCard.setAttribute('data-active-rail', String(rail.id));
      bannerCard.classList.remove('is-morphing');
      if (animateScramble && !reducedMotion) {
        void bannerCard.offsetWidth;
        bannerCard.classList.add('is-morphing');
      }
    }

    if (bannerIcon) {
      bannerIcon.innerHTML = rail.iconSvg;
    }

    if (animateScramble) {
      scrambleElement(bannerEyebrow, rail.eyebrow, 280, reducedMotion);
      scrambleElement(bannerTitle, rail.title, 340, reducedMotion);
      scrambleElement(bannerDesc, rail.description, 380, reducedMotion);
      scrambleElement(bannerSpec, rail.spec, 320, reducedMotion);
    } else {
      if (bannerEyebrow) bannerEyebrow.textContent = rail.eyebrow;
      if (bannerTitle) bannerTitle.textContent = rail.title;
      if (bannerDesc) bannerDesc.textContent = rail.description;
      if (bannerSpec) bannerSpec.textContent = rail.spec;
    }
  };

  const setActiveSlot = (index: number) => {
    const next = clamp(Math.round(index), 0, maxIndex);
    const changed = next !== activeIndex;
    const isInitial = activeIndex === -1;
    activeIndex = next;

    tabs.forEach((tab, i) => {
      const selected = i === next;
      tab.classList.toggle('is-active', selected);
      tab.setAttribute('aria-selected', String(selected));
    });

    if (changed) {
      renderBanner(next, !isInitial);
    }
  };

  const applyVisuals = () => {
    const dockW = dock.clientWidth || 360;
    const slotW = getSlotWidth();
    const speedMag = Math.min(0.06, Math.abs(dampedVelocity) * 0.035);
    const stretchX = clamp(pressScale + speedMag, 0.96, 1.11);
    const stretchY = clamp(pressScale - speedMag * 0.45, 0.92, 1.06);

    // Compute pixel bounds relative to dock border and guarantee max +/-5px peek
    const minCenterFromLeft = slotW * (stretchX / 2) - MAX_PEEK_PX;
    const maxCenterFromLeft = dockW - slotW * (stretchX / 2) + MAX_PEEK_PX;
    const rawCenter = 6 + currPos * slotW + slotW / 2;
    const boundedCenter = clamp(rawCenter, minCenterFromLeft, maxCenterFromLeft);
    const boundedPos = (boundedCenter - 6 - slotW / 2) / slotW;

    const txPercent = boundedPos * 100;
    puck.style.transform = `translate3d(${txPercent.toFixed(2)}%, 0, 0) scale(${stretchX.toFixed(3)}, ${stretchY.toFixed(3)})`;

    tabs.forEach((tab, i) => {
      const dist = Math.abs(boundedPos - i);
      const scale = 1 + Math.max(0, 1 - dist) * 0.06;
      tab.style.setProperty('--tab-scale', scale.toFixed(3));
    });
  };

  const stepPhysics = (now: number) => {
    const dt = Math.min(0.032, Math.max(0.008, (now - lastTime) / 1000 || 0.016));
    lastTime = now;

    if (!pointerDown) {
      const force = (targetPos - currPos) * 240;
      const damp = velPos * 23;
      velPos += (force - damp) * dt;
      currPos += velPos * dt;
      dampedVelocity = velPos;
    } else {
      dampedVelocity *= 0.82;
    }

    pressScale += (targetPressScale - pressScale) * 0.25;
    applyVisuals();

    const settled =
      !pointerDown &&
      Math.abs(targetPos - currPos) < 0.002 &&
      Math.abs(velPos) < 0.01 &&
      Math.abs(targetPressScale - pressScale) < 0.003;

    if (!settled) {
      rafId = requestAnimationFrame(stepPhysics);
    } else {
      currPos = targetPos;
      velPos = 0;
      dampedVelocity = 0;
      pressScale = targetPressScale;
      applyVisuals();
      rafId = 0;
    }
  };

  const schedule = () => {
    if (reducedMotion) {
      currPos = targetPos;
      pressScale = 1;
      dampedVelocity = 0;
      applyVisuals();
      return;
    }
    if (!rafId) {
      lastTime = performance.now();
      rafId = requestAnimationFrame(stepPhysics);
    }
  };

  const getSlotIndexFromClientX = (clientX: number): number => {
    const rect = dock.getBoundingClientRect();
    const innerWidth = Math.max(120, rect.width - 12);
    const ratio = (clientX - rect.left - 6) / innerWidth;
    return clamp(Math.floor(ratio * tabs.length), 0, maxIndex);
  };

  dock.addEventListener('pointerdown', (e) => {
    if (e.button !== 0) return;
    pointerDown = true;
    hasCaptured = false;
    activePointerId = e.pointerId;
    movedDistance = 0;
    dragStartClientX = e.clientX;
    lastClientX = e.clientX;
    dragStartPos = currPos;
    targetPressScale = reducedMotion ? 1 : 1.06;
    schedule();
  });

  dock.addEventListener('pointermove', (e) => {
    if (!pointerDown || e.pointerId !== activePointerId) return;
    const dx = e.clientX - dragStartClientX;
    movedDistance = Math.max(movedDistance, Math.abs(dx));

    if (!hasCaptured && movedDistance >= 6) {
      hasCaptured = true;
      dock.classList.add('is-dragging');
      try {
        dock.setPointerCapture(e.pointerId);
      } catch {
        // Ignore synthetic environments
      }
    }

    if (!hasCaptured) return;

    const slotW = getSlotWidth();
    const travel = Math.max(120, slotW * maxIndex);
    const rawPos = dragStartPos + (dx / travel) * maxIndex;

    // Limit elastic peek past [0, maxIndex] to ~5px equivalent
    const peekSlots = MAX_PEEK_PX / slotW;
    let nextPos = rawPos;
    if (rawPos < 0) nextPos = Math.max(-peekSlots, rawPos * 0.22);
    else if (rawPos > maxIndex) nextPos = Math.min(maxIndex + peekSlots, maxIndex + (rawPos - maxIndex) * 0.22);

    const frameDx = e.clientX - lastClientX;
    lastClientX = e.clientX;
    dampedVelocity = clamp((frameDx / travel) * maxIndex * 2.4, -1.8, 1.8);

    currPos = nextPos;
    targetPos = clamp(Math.round(nextPos), 0, maxIndex);
    if (targetPos !== activeIndex) {
      setActiveSlot(targetPos);
    }
    applyVisuals();
  });

  const endPointer = (e: PointerEvent) => {
    if (!pointerDown || e.pointerId !== activePointerId) return;
    pointerDown = false;
    targetPressScale = 1;
    dock.classList.remove('is-dragging');

    if (hasCaptured) {
      try {
        if (dock.hasPointerCapture(e.pointerId)) {
          dock.releasePointerCapture(e.pointerId);
        }
      } catch {
        // Ignore synthetic events
      }
    }

    if (movedDistance < 6) {
      // Genuine click/tap anywhere inside the capsule jumps smoothly to that slot
      const clickedIdx = getSlotIndexFromClientX(e.clientX);
      targetPos = clickedIdx;
      setActiveSlot(clickedIdx);
      suppressNextClick = true;
      setTimeout(() => {
        suppressNextClick = false;
      }, 60);
      schedule();
      return;
    }

    const projected = currPos + dampedVelocity * 0.1;
    targetPos = clamp(Math.round(projected), 0, maxIndex);
    setActiveSlot(targetPos);
    suppressNextClick = true;
    setTimeout(() => {
      suppressNextClick = false;
    }, 60);
    schedule();
  };

  dock.addEventListener('pointerup', endPointer);
  dock.addEventListener('pointercancel', endPointer);

  // Explicit click support on tabs (covers keyboard activation, assistive tools, or direct programmatic clicks)
  tabs.forEach((tab, idx) => {
    tab.addEventListener('click', () => {
      if (suppressNextClick) return;
      targetPos = idx;
      setActiveSlot(idx);
      schedule();
    });

    tab.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
        e.preventDefault();
        const next = (idx + 1) % tabs.length;
        targetPos = next;
        setActiveSlot(next);
        tabs[next].focus();
        schedule();
      } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
        e.preventDefault();
        const prev = (idx - 1 + tabs.length) % tabs.length;
        targetPos = prev;
        setActiveSlot(prev);
        tabs[prev].focus();
        schedule();
      } else if (e.key === 'Home') {
        e.preventDefault();
        targetPos = 0;
        setActiveSlot(0);
        tabs[0].focus();
        schedule();
      } else if (e.key === 'End') {
        e.preventDefault();
        targetPos = maxIndex;
        setActiveSlot(maxIndex);
        tabs[maxIndex].focus();
        schedule();
      }
    });
  });

  window.addEventListener('resize', applyVisuals, { passive: true });
  setActiveSlot(0);
  applyVisuals();
}
