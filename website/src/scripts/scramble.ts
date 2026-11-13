const GLYPHS = '0123456789*#UPIAYX';

interface TextSegment {
  node: Text;
  target: string;
}

const activeTokens = new WeakMap<Element, number>();

function collectTextNodes(root: Element): TextSegment[] {
  const segments: TextSegment[] = [];
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let current = walker.nextNode();
  while (current) {
    const value = current.nodeValue ?? '';
    if (value.trim().length > 0) {
      segments.push({ node: current as Text, target: value });
    }
    current = walker.nextNode();
  }
  return segments;
}

/**
 * Decodes an element's text nodes from cipher glyphs to its target text.
 * Cancels any overlapping scramble on the same element so rapid UI switching stays crisp.
 */
export function scrambleElement(
  el: Element | null,
  targetText?: string,
  duration = 460,
  reducedMotion = false
): void {
  if (!el) return;

  const nextToken = (activeTokens.get(el) ?? 0) + 1;
  activeTokens.set(el, nextToken);

  if (targetText !== undefined) {
    el.textContent = targetText;
  }

  const cleanText = (el.textContent ?? '').replace(/\s+/g, ' ').trim();
  if (cleanText) {
    el.setAttribute('aria-label', cleanText);
  }

  if (reducedMotion) return;

  const segments = collectTextNodes(el);
  if (segments.length === 0) return;

  const totalChars = segments.reduce((sum, seg) => sum + seg.target.length, 0);
  const start = performance.now();

  const frame = (now: number) => {
    if (activeTokens.get(el) !== nextToken) return;

    const progress = Math.min(1, (now - start) / duration);
    const lockedCount = Math.floor(progress * totalChars);
    let globalIdx = 0;

    for (const seg of segments) {
      let out = '';
      for (let i = 0; i < seg.target.length; i++) {
        const ch = seg.target[i];
        if (ch === ' ' || ch === '\n' || ch === '\t' || ch === '.' || globalIdx <= lockedCount) {
          out += ch;
        } else {
          out += GLYPHS[(globalIdx + Math.floor(now / 32)) % GLYPHS.length];
        }
        globalIdx++;
      }
      seg.node.nodeValue = out;
    }

    if (progress < 1) {
      requestAnimationFrame(frame);
    } else {
      for (const seg of segments) {
        seg.node.nodeValue = seg.target;
      }
    }
  };

  requestAnimationFrame(frame);
}

export function initScrambleReveals(reducedMotion: boolean): void {
  if (reducedMotion) return;

  const targets = document.querySelectorAll<HTMLElement>('[data-scramble]');
  targets.forEach((el) => {
    scrambleElement(el, undefined, 520, false);
  });
}
