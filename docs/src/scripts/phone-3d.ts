import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { PhoneScreenRenderer } from './phone-screen-renderer';

// 3D device asset: samsung_galaxy_s25_ultra.glb from quentin-pla/phone-mockup-studio (MIT)
const MODEL_URL = '/models/samsung_galaxy_s25_ultra.glb';
const DEG = Math.PI / 180;

function parseCssDeg(raw: string, fallback = 0): number {
  const num = Number.parseFloat(raw);
  return Number.isFinite(num) ? num : fallback;
}

export async function initPhone3d(): Promise<void> {
  const storyPhone = document.getElementById('story-phone');
  const stageInner = document.getElementById('phone-stage-inner');
  const mount = document.getElementById('phone-3d-stage');
  const flowSection = document.getElementById('flow');
  const dragHint = document.getElementById('phone-drag-hint');

  if (!storyPhone || !stageInner || !mount || !flowSection) return;

  // Guard against environments without WebGL support
  const testCanvas = document.createElement('canvas');
  const glProbe =
    testCanvas.getContext('webgl2', { alpha: true, antialias: true }) ||
    testCanvas.getContext('webgl', { alpha: true, antialias: true });
  if (!glProbe) return;

  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const renderer = new THREE.WebGLRenderer({
    alpha: true,
    antialias: true,
    powerPreference: 'high-performance'
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.08;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.domElement.className = 'phone-3d-canvas';
  renderer.domElement.setAttribute('aria-hidden', 'true');

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(26, 380 / 620, 0.1, 40);

  const pmrem = new THREE.PMREMGenerator(renderer);
  const envTex = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
  scene.environment = envTex;

  // Balanced studio & teal rim lighting so titanium rails + camera rings catch real speculars
  const keyLight = new THREE.DirectionalLight(0xffffff, 1.35);
  keyLight.position.set(2.4, 3.2, 4.5);
  scene.add(keyLight);

  const fillLight = new THREE.DirectionalLight(0xdff7f3, 0.72);
  fillLight.position.set(-3.2, -1.2, 3.0);
  scene.add(fillLight);

  const rimLight = new THREE.DirectionalLight(0x2dd4bf, 0.55);
  rimLight.position.set(0.0, 2.2, -3.5);
  scene.add(rimLight);

  const screenRenderer = new PhoneScreenRenderer();
  const pivot = new THREE.Group();
  scene.add(pivot);

  const loader = new GLTFLoader();
  let gltfScene: THREE.Group;
  try {
    const gltf = await loader.loadAsync(MODEL_URL);
    gltfScene = gltf.scene;
  } catch {
    pmrem.dispose();
    envTex.dispose();
    screenRenderer.dispose();
    renderer.dispose();
    return;
  }

  // Apply refined PBR materials matching Samsung Galaxy S25 Ultra titanium + glass optics
  gltfScene.traverse((child) => {
    if (!(child as THREE.Mesh).isMesh) return;
    const mesh = child as THREE.Mesh;
    const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material];

    materials.forEach((rawMat) => {
      if (!rawMat || !(rawMat instanceof THREE.MeshStandardMaterial)) return;
      const mat = rawMat;
      switch (mat.name) {
        case 'Screen':
          mat.map = null;
          mat.color.setHex(0x000000);
          mat.emissive.setHex(0xffffff);
          mat.emissiveMap = screenRenderer.texture;
          mat.emissiveIntensity = 1.0;
          mat.roughness = 1.0;
          mat.metalness = 0.0;
          mat.envMapIntensity = 0.0;
          mat.toneMapped = false;
          mat.needsUpdate = true;
          break;
        case 'Screen glass':
          mat.roughness = 0.32;
          mat.metalness = 0.0;
          mat.envMapIntensity = 0.25;
          mat.opacity = 0.02;
          mat.transparent = true;
          break;
        case 'Body':
          mat.color.setHex(0x94989e);
          mat.metalness = 1.0;
          mat.roughness = 0.36;
          mat.envMapIntensity = 0.95;
          break;
        case 'Body .001':
          mat.color.setHex(0x888c92);
          mat.metalness = 1.0;
          mat.roughness = 0.38;
          mat.envMapIntensity = 0.9;
          break;
        case 'Body .002':
          mat.color.setHex(0x7c8087);
          mat.metalness = 1.0;
          mat.roughness = 0.42;
          mat.envMapIntensity = 0.85;
          break;
        case 'Button':
          mat.color.setHex(0x9ea3aa);
          mat.metalness = 0.95;
          mat.roughness = 0.3;
          mat.envMapIntensity = 0.9;
          break;
        case 'Back glass':
          mat.color.setHex(0x232832);
          mat.metalness = 0.08;
          mat.roughness = 0.72;
          mat.envMapIntensity = 0.32;
          break;
        case 'Back glass flash':
          mat.color.setHex(0x000000);
          mat.emissive.setHex(0xf0dea0);
          mat.emissiveIntensity = 0.95;
          mat.metalness = 0.0;
          mat.roughness = 1.0;
          mat.envMapIntensity = 0.0;
          mat.toneMapped = false;
          break;
        case 'Camera body':
        case 'Camera_ring':
          mat.color.setHex(0x282c34);
          mat.metalness = 1.0;
          mat.roughness = 0.25;
          mat.envMapIntensity = 0.95;
          break;
        case 'Camera_ring_front':
        case 'Camera':
          mat.color.setHex(0x080a0f);
          mat.emissive.setHex(0x000000);
          mat.metalness = 0.4;
          mat.roughness = 0.35;
          mat.envMapIntensity = 0.5;
          break;
        case 'Camera glass':
          mat.color.setHex(0x000000);
          mat.metalness = 0.0;
          mat.roughness = 0.12;
          mat.envMapIntensity = 0.8;
          mat.transparent = true;
          mat.opacity = 0.08;
          break;
        case 'Dark':
          mat.color.setHex(0x0f1218);
          mat.metalness = 0.2;
          mat.roughness = 0.85;
          mat.envMapIntensity = 0.2;
          break;
        case 'Camera lens':
          mat.color.setHex(0x06161c);
          mat.emissive.setHex(0x072b34);
          mat.emissiveIntensity = 0.45;
          mat.metalness = 0.6;
          mat.roughness = 0.15;
          mat.envMapIntensity = 1.0;
          break;
      }
    });
  });

  // Normalize scale and center at pivot origin for clean orbital rotation
  const rawBox = new THREE.Box3().setFromObject(gltfScene);
  const rawSize = rawBox.getSize(new THREE.Vector3());
  const rawCenter = rawBox.getCenter(new THREE.Vector3());
  const normScale = 2.0 / Math.max(rawSize.y, 0.001);
  gltfScene.scale.multiplyScalar(normScale);
  gltfScene.position.sub(rawCenter.multiplyScalar(normScale));
  pivot.add(gltfScene);

  // Measure normalized bounds and size viewport cleanly
  const sizeBox = new THREE.Box3().setFromObject(pivot);
  const normHeight = sizeBox.getSize(new THREE.Vector3()).y;

  const updateViewportSize = () => {
    const w = Math.max(mount.clientWidth, 260);
    const h = Math.max(mount.clientHeight, 380);
    renderer.setSize(w, h, false);
    camera.aspect = w / h;

    // Leave generous breathing room so +/-32 deg yaw/drag tumbles never clip corners
    const halfFovRad = (camera.fov * DEG) / 2;
    const targetFill = w < 350 ? 0.84 : 0.88;
    camera.position.set(0, 0, (normHeight / 2 / targetFill) / Math.tan(halfFovRad));
    camera.updateProjectionMatrix();
  };

  mount.replaceChildren(renderer.domElement);
  updateViewportSize();

  const resizeObserver = new ResizeObserver(updateViewportSize);
  resizeObserver.observe(mount);

  // Interactive 360-degree drag / inspect state with smooth spring return
  let isDragging = false;
  let activePointerId: number | null = null;
  let lastPointerX = 0;
  let lastPointerY = 0;
  let dragYaw = 0;
  let dragPitch = 0;
  let releaseReturnAt = 0;

  let curRotX = 3 * DEG;
  let curRotY = -14 * DEG;
  let curPosY = 0;

  const getActiveBeat = (): number => {
    const activeScreen = storyPhone.querySelector('.stage-screen.is-active');
    const idx = Number.parseInt(activeScreen?.getAttribute('data-screen') || '0', 10);
    return Number.isFinite(idx) ? Math.max(0, Math.min(3, idx)) : 0;
  };

  const getTheme = (): 'light' | 'dark' =>
    document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';

  const renderFrame = (timeMs: number) => {
    const timeSec = timeMs * 0.001;
    const theme = getTheme();
    const beat = getActiveBeat();

    // Update rim lighting subtlety between Light and Dark mode
    rimLight.intensity = theme === 'dark' ? 0.82 : 0.42;
    fillLight.intensity = theme === 'dark' ? 0.62 : 0.85;

    screenRenderer.render({
      beat,
      theme,
      timeSec,
      reducedMotion
    });

    // Derive showcase angle from active scroll side & pointer tilt
    const sidePx = parseCssDeg(stageInner.style.getPropertyValue('--phone-side-x'), 0);
    const orbitRy = parseCssDeg(storyPhone.style.getPropertyValue('--orbit-ry'), 0);
    const orbitRx = parseCssDeg(storyPhone.style.getPropertyValue('--orbit-rx'), 0);
    const tiltRy = parseCssDeg(flowSection.style.getPropertyValue('--tilt-ry'), 0);
    const tiltRx = parseCssDeg(flowSection.style.getPropertyValue('--tilt-rx'), 0);

    // Side-directed showcase angle so user clearly sees metallic 3D rails & side buttons
    const showcaseYawDeg =
      Math.abs(sidePx) > 10 ? (sidePx > 0 ? -20 : 20) : beat % 2 === 0 ? -10 : 10;
    const showcasePitchDeg = 5.0;

    if (!isDragging && performance.now() > releaseReturnAt) {
      dragYaw *= 0.91;
      dragPitch *= 0.91;
      if (Math.abs(dragYaw) < 0.001) dragYaw = 0;
      if (Math.abs(dragPitch) < 0.001) dragPitch = 0;
    }

    const floatY = reducedMotion ? 0 : Math.sin(timeSec * 1.45) * 0.022;
    const breathRotY = reducedMotion ? 0 : Math.sin(timeSec * 0.9) * (2.2 * DEG);

    const targetRotY =
      (showcaseYawDeg + orbitRy * 1.2 + tiltRy * 1.35) * DEG + breathRotY + dragYaw;
    const targetRotX =
      THREE.MathUtils.clamp(
        (showcasePitchDeg + orbitRx + tiltRx * 1.25) * DEG + dragPitch,
        -0.95,
        0.95
      );

    const lerpRate = reducedMotion ? 1 : 0.12;
    curRotX += (targetRotX - curRotX) * lerpRate;
    curRotY += (targetRotY - curRotY) * lerpRate;
    curPosY += (floatY - curPosY) * lerpRate;

    pivot.rotation.set(curRotX, curRotY, 0);
    pivot.position.y = curPosY;

    renderer.render(scene, camera);
  };

  // Drag-to-spin interaction (supports full 360 rear camera inspection)
  mount.addEventListener('pointerdown', (e) => {
    if (reducedMotion || e.button !== 0) return;
    e.preventDefault();
    isDragging = true;
    activePointerId = e.pointerId;
    lastPointerX = e.clientX;
    lastPointerY = e.clientY;
    mount.setPointerCapture(e.pointerId);
    mount.classList.add('is-dragging');
    if (dragHint) dragHint.classList.add('is-used');
  });

  mount.addEventListener('pointermove', (e) => {
    if (!isDragging || e.pointerId !== activePointerId) return;
    const dx = e.clientX - lastPointerX;
    const dy = e.clientY - lastPointerY;
    lastPointerX = e.clientX;
    lastPointerY = e.clientY;
    dragYaw += dx * 0.0115;
    dragPitch = THREE.MathUtils.clamp(dragPitch + dy * 0.0065, -0.7, 0.7);
  });

  const endDrag = (e: PointerEvent) => {
    if (!isDragging || e.pointerId !== activePointerId) return;
    isDragging = false;
    activePointerId = null;
    releaseReturnAt = performance.now() + 950;
    mount.classList.remove('is-dragging');
    try {
      mount.releasePointerCapture(e.pointerId);
    } catch {
      // Ignore if capture was already released
    }
  };

  mount.addEventListener('pointerup', endDrag);
  mount.addEventListener('pointercancel', endDrag);

  // Paint first frame before crossfading out the 2D CSS fallback chassis
  renderFrame(performance.now());
  storyPhone.classList.add('is-3d-ready');

  let isInView = true;
  let rafId = 0;

  const tick = (now: number) => {
    rafId = 0;
    if (!isInView || document.hidden) return;
    renderFrame(now);
    rafId = requestAnimationFrame(tick);
  };

  const ensureRunning = () => {
    if (!rafId && isInView && !document.hidden) {
      rafId = requestAnimationFrame(tick);
    }
  };

  const observer = new IntersectionObserver(
    (entries) => {
      isInView = entries[0]?.isIntersecting ?? true;
      if (isInView) {
        renderFrame(performance.now());
        ensureRunning();
      } else if (rafId) {
        cancelAnimationFrame(rafId);
        rafId = 0;
      }
    },
    { rootMargin: '220px 0px' }
  );
  observer.observe(flowSection);

  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && isInView) {
      ensureRunning();
    }
  });

  ensureRunning();
}
