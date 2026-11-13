import '../styles/tokens.css';
import '../styles/main.css';
import { initAntigravityBg } from './antigravity-bg';
import { initMicroInteractions, triggerThemePulse } from './micro-interactions';

const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

function initTheme(): void {
  const root = document.documentElement;
  const themeMeta = document.querySelector('meta[name="theme-color"]');
  const toggleBtn = document.getElementById('theme-toggle');

  const sync = (theme: 'light' | 'dark') => {
    root.setAttribute('data-theme', theme);
    if (themeMeta) {
      themeMeta.setAttribute('content', theme === 'dark' ? '#090D14' : '#F8FAFC');
    }
    if (toggleBtn) {
      toggleBtn.setAttribute('aria-pressed', String(theme === 'dark'));
      toggleBtn.setAttribute('aria-label', theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
    }
  };

  sync(root.getAttribute('data-theme') === 'dark' ? 'dark' : 'light');

  toggleBtn?.addEventListener('click', () => {
    const next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    sync(next);
    triggerThemePulse(toggleBtn, next, reducedMotion);
    try {
      localStorage.setItem('proximity-theme', next);
    } catch {
      // Ignore storage errors
    }
  });
}

document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  initAntigravityBg(reducedMotion);
  initMicroInteractions(reducedMotion);
});
