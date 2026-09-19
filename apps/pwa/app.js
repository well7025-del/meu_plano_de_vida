/**
 * Vagas — aplicativo de teste de campo.
 *
 * Roda no navegador do celular e usa exatamente o mesmo motor de deteccao que
 * os testes deste repositorio (`/core` e o build de `packages/core`). Nada de
 * reimplementacao paralela: se o comportamento mudar aqui, mudou la.
 *
 * Limite conhecido e inevitavel no navegador: quando a tela apaga ou o app sai
 * da frente, o sistema congela o JavaScript e o rastreamento para. Por isso o
 * app pede a tela ligada e avisa quando percebe um buraco. A versao nativa
 * (apps/mobile) nao tem essa limitacao.
 */
import { ParkingDetector, distanceM } from '/core/index.js';

// ---------------------------------------------------------------- utilidades

const $ = (id) => document.getElementById(id);

/** localStorage que nunca derruba o app (aba anonima, cota cheia, iOS Lockdown). */
const store = {
  get(key, fallback) {
    try {
      const raw = localStorage.getItem(`vagas.${key}`);
      return raw === null ? fallback : JSON.parse(raw);
    } catch {
      return fallback;
    }
  },
  set(key, value) {
    try {
      localStorage.setItem(`vagas.${key}`, JSON.stringify(value));
    } catch {
      /* sem persistencia: o app continua funcionando na sessao */
    }
  },
};

const fmtDist = (m) => (m < 1000 ? `${Math.round(m)} m` : `${(m / 1000).toFixed(1)} km`);

function fmtAge(t, now = Date.now()) {
  const min = Math.max(0, Math.round((now - t) / 60000));
  if (min < 1) return 'agora mesmo';
  if (min === 1) return 'há 1 min';
  if (min < 60) return `há ${min} min`;
  const h = Math.floor(min / 60);
  return h === 1 ? 'há 1 h' : `há ${h} h`;
}

const fmtClock = (t) =>
  new Date(t).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });

/** Rumo de A para B, em graus (0 = norte). */
function bearing(from, to) {
  const rad = Math.PI / 180;
  const y = Math.sin((to.lon - from.lon) * rad) * Math.cos(to.lat * rad);
  const x =
    Math.cos(from.lat * rad) * Math.sin(to.lat * rad) -
    Math.sin(from.lat * rad) * Math.cos(to.lat * rad) * Math.cos((to.lon - from.lon) * rad);
  return (Math.atan2(y, x) / rad + 360) % 360;
}

/** Direcao relativa ao rumo do carro — o que serve para falar em voz alta. */
function relativeSide(bearingDeg, headingDeg) {
  if (headingDeg === null || headingDeg === undefined || Number.isNaN(headingDeg)) {
    const pontos = ['ao norte', 'a nordeste', 'a leste', 'a sudeste', 'ao sul', 'a sudoeste', 'a oeste', 'a noroeste'];
    return pontos[Math.round(bearingDeg / 45) % 8];
  }
  const delta = ((bearingDeg - headingDeg + 540) % 360) - 180;
  if (Math.abs(delta) < 25) return 'à frente';
  if (Math.abs(delta) > 155) return 'atrás de você';
  return delta > 0 ? 'à direita' : 'à esquerda';
}

// -------------------------------------------------------------------- estado

const state = {
  detector: null,
  watchId: null,
  position: null,       // {lat, lon, accuracy, heading, speed, t}
  speed: 0,             // m/s suavizado, só para exibição
  speedBuf: [],
  lastState: 'unknown',
  spots: [],
  outbox: store.get('outbox', []),
  myEvents: store.get('myEvents', []),
  parked: store.get('parked', null),
  log: [],
  searching: false,
  wakeLock: null,
  lastSpotFetch: 0,
  lastSample: 0,
  lastAnnounce: 0,
  announced: new Set(),
  tracking: false,
};

function addLog(kind, text) {
  state.log.unshift({ t: Date.now(), kind, text });
  if (state.log.length > 80) state.log.pop();
  renderLog();
}

// ---------------------------------------------------------------- permissões

async function start() {
  // iOS exige um gesto do usuário para liberar a voz. Este é o gesto.
  primeSpeech();

  if (!('geolocation' in navigator)) {
    banner('Este navegador não expõe localização. Use Chrome ou Safari.');
    return;
  }

  try {
    await new Promise((ok, fail) =>
      navigator.geolocation.getCurrentPosition(ok, fail, { enableHighAccuracy: true, timeout: 20000 }),
    );
  } catch (err) {
    banner(
      err && err.code === 1
        ? 'Permissão de localização negada. Libere nas configurações do navegador e recarregue.'
        : 'Não consegui obter sua localização. Saia de ambiente fechado e tente de novo.',
    );
    return;
  }

  store.set('consent', { at: Date.now(), version: 1 });
  $('welcome').hidden = true;
  $('app').hidden = false;

  state.detector = new ParkingDetector();
  state.tracking = true;

  state.watchId = navigator.geolocation.watchPosition(onPosition, onPositionError, {
    enableHighAccuracy: true,
    maximumAge: 0,
    timeout: 30000,
  });

  addLog('sys', 'rastreamento iniciado');
  requestWakeLock();
  flushOutbox();
  render();
}

function stop() {
  if (state.watchId !== null) navigator.geolocation.clearWatch(state.watchId);
  state.watchId = null;
  state.tracking = false;
  releaseWakeLock();
  addLog('sys', 'rastreamento parado');
  render();
}

// ------------------------------------------------------------------- posição

function onPositionError(err) {
  if (err.code === 3) return; // timeout isolado: o watch continua
  banner(err.code === 1 ? 'Permissão de localização revogada.' : 'Sinal de GPS indisponível no momento.');
}

function onPosition(pos) {
  const c = pos.coords;
  const now = pos.timestamp || Date.now();

  // Buraco longo entre amostras: o sistema congelou o app (tela apagada).
  if (state.lastSample && now - state.lastSample > 90_000) {
    addLog('sys', `${Math.round((now - state.lastSample) / 1000)} s sem amostra — o app ficou congelado`);
  }
  state.lastSample = now;

  state.position = {
    lat: c.latitude,
    lon: c.longitude,
    accuracy: c.accuracy,
    heading: Number.isFinite(c.heading) ? c.heading : null,
    speed: Number.isFinite(c.speed) ? c.speed : null,
    t: now,
  };

  // Velocidade de exibição: mediana curta, para o número não tremer na tela.
  const raw = Number.isFinite(c.speed) && c.speed >= 0 ? c.speed : state.speed;
  state.speedBuf.push(raw);
  if (state.speedBuf.length > 5) state.speedBuf.shift();
  const ordenado = [...state.speedBuf].sort((a, b) => a - b);
  state.speed = ordenado[ordenado.length >> 1] ?? 0;

  const eventos = state.detector.push({
    t: now,
    lat: c.latitude,
    lon: c.longitude,
    speed: Number.isFinite(c.speed) ? c.speed : null,
    accuracy: c.accuracy,
    activityHint: null,
  });

  if (state.detector.currentState !== state.lastState) {
    addLog('estado', `${nomeEstado(state.lastState)} → ${nomeEstado(state.detector.currentState)}`);
    state.lastState = state.detector.currentState;
  }

  for (const e of eventos) onEvent(e);

  maybeFetchSpots();
  render();
}

function onEvent(e) {
  const rotulo = e.kind === 'departure' ? 'vaga liberada' : 'vaga ocupada';
  addLog(e.kind, `${rotulo} · confiança ${Math.round(e.confidence * 100)}%`);

  state.myEvents.unshift({ kind: e.kind, t: e.t, confidence: e.confidence, lat: e.lat, lon: e.lon });
  state.myEvents = state.myEvents.slice(0, 200);
  store.set('myEvents', state.myEvents);

  if (e.kind === 'arrival') {
    state.parked = { lat: e.lat, lon: e.lon, t: e.t };
    store.set('parked', state.parked);
  }

  // Coordenada arredondada antes de sair do aparelho (~11 cm).
  enqueue({
    kind: e.kind,
    lat: Math.round(e.lat * 1e6) / 1e6,
    lon: Math.round(e.lon * 1e6) / 1e6,
    t: e.t,
    confidence: e.confidence,
  });

  if (navigator.vibrate) navigator.vibrate(e.kind === 'departure' ? [40, 60, 40] : 40);
}

// --------------------------------------------------------------- fila e rede

function enqueue(payload) {
  state.outbox.push(payload);
  store.set('outbox', state.outbox);
  flushOutbox();
}

async function flushOutbox() {
  if (state.outbox.length === 0 || !navigator.onLine) return;
  const lote = state.outbox.slice(0, 40);
  try {
    const res = await fetch('/v1/events', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ events: lote }),
    });
    // 400 também tira da fila: evento recusado não melhora com reenvio.
    if (!res.ok && res.status !== 400) throw new Error(`HTTP ${res.status}`);
    state.outbox = state.outbox.slice(lote.length);
    store.set('outbox', state.outbox);
  } catch {
    /* sem rede: tenta de novo na próxima amostra */
  }
}

async function maybeFetchSpots() {
  const agora = Date.now();
  const intervalo = state.searching ? 10_000 : 25_000;
  if (!state.position || agora - state.lastSpotFetch < intervalo) return;
  state.lastSpotFetch = agora;

  try {
    const { lat, lon } = state.position;
    const res = await fetch(`/v1/spots?lat=${lat}&lon=${lon}&radius=${RAIO_M}`);
    if (!res.ok) return;
    const body = await res.json();
    state.spots = (body.spots || []).filter((s) => s.tier !== 'baixa');
    render();
    if (state.searching) announceNearest();
  } catch {
    /* mantém o conjunto anterior na tela, com a idade visível */
  }
}

async function feedback(spot, achou) {
  state.spots = state.spots.filter((s) => s.cell !== spot.cell);
  render();
  addLog('sys', achou ? 'confirmou uma vaga' : 'desmentiu um ponto');
  try {
    await fetch('/v1/feedback', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ lat: spot.lat, lon: spot.lon, found: achou, shownProbability: spot.probability }),
    });
  } catch {
    /* o ponto já saiu da tela; o servidor sincroniza na próxima consulta */
  }
}

// ----------------------------------------------------------------- voz e tela

let speechReady = false;
function primeSpeech() {
  if (speechReady || !('speechSynthesis' in window)) return;
  try {
    const u = new SpeechSynthesisUtterance(' ');
    u.volume = 0;
    speechSynthesis.speak(u);
    speechReady = true;
  } catch {
    /* sem voz: o app segue funcionando em silêncio */
  }
}

function speak(texto) {
  if (!speechReady) return;
  try {
    const u = new SpeechSynthesisUtterance(texto);
    u.lang = 'pt-BR';
    u.rate = 1.05;
    speechSynthesis.speak(u);
  } catch {
    /* ignora */
  }
}

/**
 * Fala a vaga mais próxima enquanto o carro anda.
 *
 * Dirigindo, ler a tela não é opção. Por isso o modo de busca avisa por voz,
 * no máximo uma vez a cada 40 s e nunca duas vezes o mesmo ponto.
 */
function announceNearest() {
  if (!state.position || state.speed < 2.5) return;
  const agora = Date.now();
  if (agora - state.lastAnnounce < 40_000) return;

  const perto = nearbySpots()[0];
  if (!perto || perto.dist > 400 || state.announced.has(perto.cell)) return;

  state.announced.add(perto.cell);
  state.lastAnnounce = agora;
  speak(`Vaga provável a ${Math.round(perto.dist)} metros, ${perto.lado}.`);
}

async function requestWakeLock() {
  if (!('wakeLock' in navigator)) return;
  try {
    state.wakeLock = await navigator.wakeLock.request('screen');
    state.wakeLock.addEventListener('release', () => {
      state.wakeLock = null;
    });
  } catch {
    /* o sistema pode recusar com bateria baixa */
  }
}

function releaseWakeLock() {
  try {
    state.wakeLock?.release();
  } catch {
    /* ignora */
  }
  state.wakeLock = null;
}

// ------------------------------------------------------------------ desenho

function nomeEstado(s) {
  return { walking: 'a pé', in_vehicle: 'dirigindo', vehicle_stopped: 'parado no carro', unknown: 'indefinido' }[s] ?? s;
}

/** Raio consultado no servidor — e tambem o limite do que a tela mostra. */
const RAIO_M = 800;

function nearbySpots() {
  if (!state.position) return [];
  const eu = { lat: state.position.lat, lon: state.position.lon };
  return state.spots
    .filter((s) => distanceM(eu, { lat: s.lat, lon: s.lon }) <= RAIO_M)
    .map((s) => {
      const dist = distanceM(eu, { lat: s.lat, lon: s.lon });
      const brg = bearing(eu, { lat: s.lat, lon: s.lon });
      return { ...s, dist, brg, lado: relativeSide(brg, state.position.heading) };
    })
    .sort((a, b) => a.dist - b.dist);
}

function banner(texto) {
  const el = $('banner');
  el.textContent = texto;
  el.hidden = !texto;
}

function render() {
  if ($('app').hidden) return;
  const pos = state.position;

  // cabeçalho
  $('pillTrack').textContent = state.tracking ? (state.searching ? 'procurando' : 'ativo') : 'parado';
  $('pillTrack').className = `pill ${state.tracking ? 'on' : 'warn'}`;
  if (pos) {
    const boa = pos.accuracy <= 25;
    $('pillGps').textContent = `GPS ${Math.round(pos.accuracy)} m`;
    $('pillGps').className = `pill ${boa ? 'on' : 'warn'}`;
  }

  // estado do motor
  const st = state.detector ? state.detector.currentState : 'unknown';
  const icones = { walking: '🚶', in_vehicle: '🚗', vehicle_stopped: '🅿️', unknown: '📡' };
  const titulos = {
    walking: 'A pé',
    in_vehicle: 'Dirigindo',
    vehicle_stopped: 'Parado no carro',
    unknown: 'Aguardando movimento',
  };
  const subs = {
    walking: 'se você entrar num carro agora, uma vaga é sinalizada',
    in_vehicle: 'ao estacionar e sair a pé, a vaga é marcada como ocupada',
    vehicle_stopped: 'se você sair e caminhar, confirmo que estacionou',
    unknown: 'ande um pouco para o app se calibrar',
  };
  $('stIcon').textContent = icones[st];
  $('stNow').textContent = titulos[st];
  $('stSub').textContent = subs[st];
  $('gSpeed').textContent = pos ? (state.speed * 3.6).toFixed(0) : '—';
  $('gAcc').textContent = pos ? Math.round(pos.accuracy) : '—';
  $('gEvents').textContent = state.myEvents.length;

  // carro
  if (state.parked && pos) {
    $('cardCar').hidden = false;
    const d = distanceM(pos, state.parked);
    $('carDist').textContent = `a ${fmtDist(d)} de você`;
    $('carWhen').textContent = `estacionado ${fmtAge(state.parked.t)}`;
  } else {
    $('cardCar').hidden = !state.parked;
  }

  renderSpots();
  drawRadar();
  $('diagSummary').textContent = state.tracking
    ? `fila: ${state.outbox.length} · amostras: ${state.lastSample ? fmtClock(state.lastSample) : '—'} · tela travada: ${state.wakeLock ? 'sim' : 'não'}`
    : 'rastreamento parado';
}

function renderSpots() {
  const lista = nearbySpots();
  const ul = $('spotList');
  $('spotEmpty').hidden = lista.length > 0;
  ul.innerHTML = '';

  for (const s of lista.slice(0, 6)) {
    const li = document.createElement('li');
    li.className = s.tier === 'alta' ? 'alta' : '';

    const dot = document.createElement('span');
    dot.className = 'dot';
    dot.style.background = s.tier === 'alta' ? 'var(--free)' : 'var(--medium)';

    const info = document.createElement('span');
    info.className = 'spotinfo';
    const d = document.createElement('div');
    d.className = 'd';
    d.textContent = `${fmtDist(s.dist)} ${s.lado}`;
    const m = document.createElement('div');
    m.className = 'm';
    m.textContent = `${Math.round(s.probability * 100)}% · ${fmtAge(s.lastEventT)}`;
    info.append(d, m);

    const ok = document.createElement('button');
    ok.className = 'small';
    ok.textContent = '✓';
    ok.title = 'Achei a vaga';
    ok.onclick = () => feedback(s, true);

    const no = document.createElement('button');
    no.className = 'small ghost';
    no.textContent = '✗';
    no.title = 'Não tinha nada';
    no.onclick = () => feedback(s, false);

    li.append(dot, info, ok, no);
    ul.append(li);
  }
}

/**
 * Radar em vez de mapa.
 *
 * Um mapa de ruas exigiria baixar tiles de um provedor externo — mais uma
 * dependência, mais uma chave, e inútil justamente quando o carro anda. O
 * radar mostra o que o motorista precisa: a que distância e para que lado,
 * sempre com o seu rumo apontando para cima.
 */
function drawRadar() {
  const cv = $('radar');
  const ctx = cv.getContext('2d');
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  const css = cv.getBoundingClientRect().width;
  if (!css) return;
  if (cv.width !== Math.round(css * dpr)) {
    cv.width = Math.round(css * dpr);
    cv.height = Math.round(css * dpr);
  }
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

  const s = getComputedStyle(document.documentElement);
  const cor = (n) => s.getPropertyValue(n).trim();
  const W = css;
  const C = W / 2;

  ctx.clearRect(0, 0, W, W);
  ctx.fillStyle = cor('--surface-2');
  ctx.fillRect(0, 0, W, W);

  const lista = nearbySpots();
  const maisLonge = lista.length ? lista[lista.length - 1].dist : 150;
  const alcance = Math.min(RAIO_M, Math.max(150, maisLonge * 1.15));
  const escala = (C - 18) / alcance;

  const headingUp = state.position && state.position.heading !== null && state.speed > 2;
  const giro = headingUp ? -state.position.heading : 0;

  // anéis de distância
  ctx.strokeStyle = cor('--line');
  ctx.lineWidth = 1;
  const aneis = [0.33, 0.66, 1];
  for (const frac of aneis) {
    const r = (C - 18) * frac;
    ctx.beginPath();
    ctx.arc(C, C, r, 0, Math.PI * 2);
    ctx.stroke();
  }

  // pontos
  for (const sp of lista) {
    if (sp.dist > alcance) continue;
    const ang = ((sp.brg + giro - 90) * Math.PI) / 180;
    const x = C + Math.cos(ang) * sp.dist * escala;
    const y = C + Math.sin(ang) * sp.dist * escala;
    const c = sp.tier === 'alta' ? cor('--free') : cor('--medium');
    ctx.fillStyle = c;
    ctx.globalAlpha = 0.18;
    ctx.beginPath();
    ctx.arc(x, y, 18, 0, Math.PI * 2);
    ctx.fill();
    ctx.globalAlpha = 1;
    ctx.beginPath();
    ctx.arc(x, y, 6, 0, Math.PI * 2);
    ctx.fill();
  }

  // carro do usuário
  if (state.parked && state.position) {
    const d = distanceM(state.position, state.parked);
    if (d <= alcance) {
      const ang = ((bearing(state.position, state.parked) + giro - 90) * Math.PI) / 180;
      ctx.fillStyle = cor('--car');
      ctx.beginPath();
      ctx.arc(C + Math.cos(ang) * d * escala, C + Math.sin(ang) * d * escala, 5, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  // Rótulos por último, sobre tudo: um ponto verde em cima do número não pode
  // custar a leitura da distância.
  ctx.font = '11px ui-monospace, monospace';
  ctx.textBaseline = 'middle';
  for (const frac of aneis) {
    const r = (C - 18) * frac;
    const texto = `${Math.round((alcance * frac) / 10) * 10} m`;
    const w = ctx.measureText(texto).width;
    const x = C + 6;
    const y = C - r;
    ctx.fillStyle = cor('--surface-2');
    ctx.globalAlpha = 0.85;
    ctx.fillRect(x - 3, y - 8, w + 6, 16);
    ctx.globalAlpha = 1;
    ctx.fillStyle = cor('--muted');
    ctx.fillText(texto, x, y);
  }
  ctx.textBaseline = 'alphabetic';

  // você, no centro
  ctx.fillStyle = cor('--ink');
  ctx.beginPath();
  ctx.moveTo(C, C - 9);
  ctx.lineTo(C - 6, C + 7);
  ctx.lineTo(C + 6, C + 7);
  ctx.closePath();
  ctx.fill();

  ctx.fillStyle = cor('--muted');
  ctx.fillText(headingUp ? 'seu rumo ↑' : 'norte ↑', 10, W - 10);
}

function renderLog() {
  const ul = $('log');
  if (!ul) return;
  ul.innerHTML = '';
  for (const e of state.log.slice(0, 40)) {
    const li = document.createElement('li');
    const t = document.createElement('span');
    t.className = 't mono';
    t.textContent = fmtClock(e.t);
    const txt = document.createElement('span');
    txt.textContent = e.text;
    const k = document.createElement('span');
    k.className = 'k';
    k.textContent = e.kind === 'departure' ? '🟢' : e.kind === 'arrival' ? '🔴' : '';
    li.append(t, txt, k);
    ul.append(li);
  }
}

// -------------------------------------------------------------------- ligação

$('btnStart').onclick = start;

$('btnSearch').onclick = () => {
  state.searching = !state.searching;
  state.announced.clear();
  $('btnSearch').textContent = state.searching ? '🔎 Procurando…' : '🔎 Procurar vaga';
  $('btnSearch').className = state.searching ? 'primary' : '';
  addLog('sys', state.searching ? 'modo busca ligado' : 'modo busca desligado');
  if (state.searching) {
    requestWakeLock();
    state.lastSpotFetch = 0;
    maybeFetchSpots();
    speak('Modo de busca ligado. Aviso quando aparecer vaga.');
  }
  render();
};

$('btnDiag').onclick = () => {
  const card = $('cardDiag');
  card.hidden = !card.hidden;
  if (!card.hidden) card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
};

$('btnForget').onclick = () => {
  state.parked = null;
  store.set('parked', null);
  render();
};

$('btnCopy').onclick = async () => {
  const texto = [
    `Vagas — registro de ${new Date().toLocaleString('pt-BR')}`,
    `detecções: ${state.myEvents.length} · fila: ${state.outbox.length}`,
    '',
    ...state.log.map((e) => `${fmtClock(e.t)}  ${e.kind.padEnd(9)} ${e.text}`),
  ].join('\n');
  try {
    await navigator.clipboard.writeText(texto);
    $('btnCopy').textContent = 'Copiado!';
    setTimeout(() => ($('btnCopy').textContent = 'Copiar registro'), 1500);
  } catch {
    banner('Não consegui copiar. Tire um print desta tela.');
  }
};

$('btnReset').onclick = () => {
  state.log = [];
  state.myEvents = [];
  store.set('myEvents', []);
  renderLog();
  render();
};

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') {
    requestWakeLock();
    flushOutbox();
    state.lastSpotFetch = 0;
    maybeFetchSpots();
  }
});

window.addEventListener('online', flushOutbox);
window.addEventListener('resize', () => drawRadar());

// A idade dos pontos envelhece na tela mesmo sem amostra nova de GPS.
setInterval(() => {
  if (!$('app').hidden) render();
}, 15_000);

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(() => {});
}

// Quem já consentiu volta direto para o app.
if (store.get('consent', null)) start();
