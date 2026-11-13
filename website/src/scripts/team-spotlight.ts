interface FounderSpot {
  x: string;
  y: string;
}

const FOUNDER_IDS = ['1', '2', '3', '4'] as const;

const FOUNDER_SPOTS: Record<string, FounderSpot> = {
  '1': { x: '9.5%', y: '47%' },
  '2': { x: '29.8%', y: '49%' },
  '3': { x: '69.5%', y: '47%' },
  '4': { x: '88%', y: '48%' },
};

export function initTeamSection(reducedMotion: boolean): void {
  const stage = document.querySelector<HTMLElement>('[data-team-stage]');
  const photoFrame = stage?.querySelector<HTMLElement>('.team-photo-frame');
  if (!stage || !photoFrame) return;

  const mobileMq = window.matchMedia('(max-width: 899px)');
  const triggers = Array.from(stage.querySelectorAll<HTMLButtonElement>('[data-founder-trigger]'));
  const stepButtons = Array.from(stage.querySelectorAll<HTMLButtonElement>('[data-team-step]'));

  let userLockedFounder: string | null = null;
  let hoveredFounder: string | null = null;

  const getActiveFounder = (): string | null => {
    if (mobileMq.matches) {
      return userLockedFounder ?? '1';
    }
    return hoveredFounder ?? userLockedFounder;
  };

  const renderSpotlight = () => {
    const active = getActiveFounder();

    if (active && FOUNDER_SPOTS[active]) {
      stage.dataset.activeFounder = active;
      photoFrame.style.setProperty('--spot-x', FOUNDER_SPOTS[active].x);
      photoFrame.style.setProperty('--spot-y', FOUNDER_SPOTS[active].y);
    } else {
      delete stage.dataset.activeFounder;
    }

    triggers.forEach((btn) => {
      const id = btn.dataset.founderTrigger ?? '';
      const isActive = active === id;
      const isPressed = mobileMq.matches ? isActive : userLockedFounder === id;
      btn.classList.toggle('is-active', isActive);
      btn.setAttribute('aria-pressed', String(isPressed));
    });
  };

  triggers.forEach((btn) => {
    const id = btn.dataset.founderTrigger ?? '';
    if (!id) return;

    btn.addEventListener('pointerenter', (e) => {
      if (e.pointerType === 'touch' || mobileMq.matches) return;
      hoveredFounder = id;
      renderSpotlight();
    });

    btn.addEventListener('pointerleave', (e) => {
      if (e.pointerType === 'touch' || mobileMq.matches) return;
      if (hoveredFounder === id) {
        hoveredFounder = null;
        renderSpotlight();
      }
    });

    btn.addEventListener('focusin', () => {
      if (mobileMq.matches) return;
      hoveredFounder = id;
      renderSpotlight();
    });

    btn.addEventListener('focusout', () => {
      if (hoveredFounder === id) {
        hoveredFounder = null;
        renderSpotlight();
      }
    });

    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (mobileMq.matches) {
        userLockedFounder = id;
      } else {
        userLockedFounder = userLockedFounder === id ? null : id;
      }
      renderSpotlight();
    });
  });

  stepButtons.forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const delta = Number(btn.dataset.teamStep || '1');
      const current = getActiveFounder() ?? '1';
      const idx = FOUNDER_IDS.indexOf(current as (typeof FOUNDER_IDS)[number]);
      const nextIdx = (Math.max(0, idx) + delta + FOUNDER_IDS.length) % FOUNDER_IDS.length;
      userLockedFounder = FOUNDER_IDS[nextIdx];
      renderSpotlight();
    });
  });

  photoFrame.addEventListener('click', (e) => {
    if (mobileMq.matches) return;
    const target = e.target as HTMLElement | null;
    if (target?.closest('[data-founder-trigger]')) return;
    if (userLockedFounder !== null) {
      userLockedFounder = null;
      renderSpotlight();
    }
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !mobileMq.matches && (userLockedFounder || hoveredFounder)) {
      userLockedFounder = null;
      hoveredFounder = null;
      renderSpotlight();
    }
  });

  const handleViewportChange = () => {
    if (!mobileMq.matches) {
      hoveredFounder = null;
    }
    renderSpotlight();
  };

  if ('addEventListener' in mobileMq) {
    mobileMq.addEventListener('change', handleViewportChange);
  }

  renderSpotlight();

  if (reducedMotion || !('IntersectionObserver' in window)) {
    stage.classList.add('is-drawn');
    return;
  }

  const observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          stage.classList.add('is-drawn');
          observer.disconnect();
          break;
        }
      }
    },
    { threshold: 0.18, rootMargin: '0px 0px -8% 0px' }
  );

  observer.observe(stage);
}
