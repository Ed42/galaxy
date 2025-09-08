(() => {
  const canvas = document.getElementById('canvas');
  const ctx = canvas.getContext('2d');
  ctx.imageSmoothingEnabled = false;

  const speedSlider = document.getElementById('speed');
  const speedLabel = document.getElementById('speedLabel');
  const pauseBtn = document.getElementById('pauseBtn');
  const resetBtn = document.getElementById('resetBtn');
  const ffBtn = document.getElementById('ffBtn');
  const barToggle = document.getElementById('barToggle');
  const qtToggle = document.getElementById('qtToggle');
  const simTimeEl = document.getElementById('simTime');
  const ptCountEl = document.getElementById('ptCount');
  const stepsEl = document.getElementById('steps');

  // years/sec slider in log10 space [10^2, 10^8]
  function ypsFromSlider(v){ return Math.pow(10, v); }
  function formatYears(y){
    if (y < 1e3) return y.toFixed(0) + ' yr';
    if (y < 1e6) return (y/1e3).toFixed(1) + ' kyr';
    if (y < 1e9) return (y/1e6).toFixed(1) + ' Myr';
    return (y/1e9).toFixed(1) + ' Gyr';
  }

  speedSlider.value = '6'; // 1e6 yr/s default
  speedLabel.textContent = '1.0e6';
  fetch('/api/speed?yps=' + ypsFromSlider(parseFloat(speedSlider.value)), { method: 'POST' });

  speedSlider.addEventListener('input', () => {
    const yps = ypsFromSlider(parseFloat(speedSlider.value));
    speedLabel.textContent = yps.toExponential(1);
    fetch('/api/speed?yps=' + yps, { method: 'POST' });
  });

  pauseBtn.addEventListener('click', () => fetch('/api/togglePause', { method: 'POST' }));
  resetBtn.addEventListener('click', () => fetch('/api/reset?n=10000', { method: 'POST' }));
  ffBtn.addEventListener('click', () => fetch('/api/fastForward', { method: 'POST' }));
  barToggle.addEventListener('change', () => fetch('/api/bar?enabled=' + (barToggle.checked ? 'true' : 'false'), { method: 'POST' }));

  let viewHalf = 1000; // pc, half-size (±1 kpc)
  let panX = 0, panY = 0; // pc
  let isPanning = false, lastMouseX = 0, lastMouseY = 0;

  canvas.addEventListener('wheel', (e) => {
    e.preventDefault();
    const scale = Math.pow(1.1, e.deltaY > 0 ? 1 : -1);
    viewHalf *= scale;
    viewHalf = Math.max(50, Math.min(2000, viewHalf));
  }, { passive: false });

  canvas.addEventListener('mousedown', (e) => {
    isPanning = true; lastMouseX = e.clientX; lastMouseY = e.clientY;
  });
  window.addEventListener('mouseup', () => { isPanning = false; });
  window.addEventListener('mousemove', (e) => {
    if (!isPanning) return;
    const dx = e.clientX - lastMouseX;
    const dy = e.clientY - lastMouseY;
    lastMouseX = e.clientX; lastMouseY = e.clientY;
    // pixels to pc: canvas half corresponds to viewHalf pc
    const px2pc = viewHalf / (canvas.width/2);
    panX -= dx * px2pc;
    panY += dy * px2pc;
  });

  function worldToScreen(x, y) {
    const sx = canvas.width/2 + (x - panX) * (canvas.width/2) / viewHalf;
    const sy = canvas.height/2 - (y - panY) * (canvas.height/2) / viewHalf;
    return [sx, sy];
  }

  function drawPoints(xs, ys, types) {
    const n = xs.length;
    ctx.globalCompositeOperation = 'lighter';
    for (let i = 0; i < n; i++) {
      const [sx, sy] = worldToScreen(xs[i], ys[i]);
      // type 1 = bulge (yellow), 0 = disk (blue)
      if (types[i] === 1) ctx.fillStyle = 'rgba(255, 240, 140, 0.4)';
      else ctx.fillStyle = 'rgba(120, 180, 255, 0.35)';
      ctx.fillRect(sx|0, sy|0, 2, 2);
    }
    ctx.globalCompositeOperation = 'source-over';
  }

  function drawSMBH() {
    const [sx, sy] = worldToScreen(0, 0);
    ctx.fillStyle = '#ff3030';
    ctx.beginPath(); ctx.arc(sx, sy, 3, 0, Math.PI*2); ctx.fill();
  }

  function clear() {
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
  }

  async function fetchSnapshot() {
    const res = await fetch('/api/snapshot');
    if (!res.ok) return null;
    return await res.json();
  }

  function updateStats(snap) {
    simTimeEl.textContent = formatYears(snap.simTimeYears);
    stepsEl.textContent = snap.steps.toString();
    ptCountEl.textContent = (snap.x.length * snap.stride).toString();
    barToggle.checked = snap.barEnabled;
  }

  async function loop() {
    const snap = await fetchSnapshot();
    if (snap) {
      clear();
      drawPoints(snap.x, snap.y, snap.type);
      drawSMBH();
      if (qtToggle.checked) {
        // client-only grid overlay for scale
        ctx.strokeStyle = 'rgba(255,255,255,0.1)';
        ctx.strokeRect(0, 0, canvas.width, canvas.height);
      }
      updateStats(snap);
    }
    setTimeout(() => requestAnimationFrame(loop), 1000/20); // ~20 FPS fetch cadence
  }

  requestAnimationFrame(loop);
})();

