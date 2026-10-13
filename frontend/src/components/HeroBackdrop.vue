<template>
  <div class="hero-backdrop" aria-hidden="true">
    <div class="hb-glow g1"></div>
    <div class="hb-glow g2"></div>
    <svg class="hb-map" viewBox="0 0 440 520" preserveAspectRatio="xMidYMid slice">
      <defs>
        <pattern id="hb-dots" width="22" height="22" patternUnits="userSpaceOnUse">
          <circle cx="1.2" cy="1.2" r="1.2" fill="rgba(255,255,255,0.09)" />
        </pattern>
        <linearGradient id="hb-route" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#FFD23F" />
          <stop offset="100%" stop-color="#FF6A00" />
        </linearGradient>
      </defs>
      <rect width="440" height="520" fill="url(#hb-dots)" />
      <g class="hb-streets">
        <path d="M-20 120 C 80 110, 140 160, 220 150 S 360 90, 470 110" />
        <path d="M-20 300 C 90 280, 160 330, 250 310 S 380 260, 470 280" />
        <path d="M110 -20 C 120 90, 90 200, 130 300 S 150 450, 120 540" />
        <path d="M320 -20 C 300 100, 340 220, 310 330 S 330 460, 350 540" />
        <path d="M-20 430 C 100 420, 200 470, 300 440 S 420 400, 470 420" />
      </g>
      <path id="hb-path" class="hb-route" d="M44 214 C 96 176, 140 200, 196 168 S 284 118, 322 104 S 364 76, 384 66" />
      <circle class="hb-node shop" cx="44" cy="214" r="7" />
      <circle class="hb-node user" cx="384" cy="66" r="7" />
      <circle class="hb-ping" cx="384" cy="66" r="7" />
      <g class="hb-rider">
        <circle r="10" fill="#FFB300" />
        <path d="M-1.5 -5 L-5 1 L-1 1 L-2 6 L4.5 -1 L0.5 -1 L1.5 -5 Z" fill="#1A1208" />
      </g>
    </svg>
    <div class="hb-fade"></div>
  </div>
</template>

<style scoped>
.hero-backdrop {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background: radial-gradient(120% 80% at 50% 0%, #2A1A0C 0%, #120D09 55%, #0B0907 100%);
}
.hb-glow { position: absolute; border-radius: 50%; filter: blur(60px); }
.hb-glow.g1 { width: 320px; height: 320px; right: -80px; top: -60px; background: rgba(255, 150, 20, 0.42); animation: float-y 9s ease-in-out infinite; }
.hb-glow.g2 { width: 260px; height: 260px; left: -100px; top: 240px; background: rgba(255, 90, 31, 0.22); animation: float-y 11s ease-in-out infinite reverse; }
.hb-map { position: absolute; left: 0; right: 0; top: 0; width: 100%; height: 62%; }
.hb-streets path { fill: none; stroke: rgba(255, 255, 255, 0.07); stroke-width: 10; stroke-linecap: round; }
.hb-route {
  fill: none;
  stroke: url(#hb-route);
  stroke-width: 3.5;
  stroke-linecap: round;
  stroke-dasharray: 8 10;
  filter: drop-shadow(0 0 6px rgba(255, 160, 0, 0.7));
  animation: dash-flow 1.4s linear infinite;
}
.hb-node { stroke: #fff; stroke-width: 3; }
.hb-node.shop { fill: #FF6A00; }
.hb-node.user { fill: #FFD23F; }
.hb-ping { fill: none; stroke: #FFD23F; stroke-width: 2; transform-origin: 384px 66px; animation: hb-ping 2.2s ease-out infinite; }
.hb-rider {
  offset-path: path("M44 214 C 96 176, 140 200, 196 168 S 284 118, 322 104 S 364 76, 384 66");
  offset-rotate: 0deg;
  animation: hb-ride 6.5s var(--ease-in-out) infinite;
  filter: drop-shadow(0 0 10px rgba(255, 179, 0, 0.9));
}
.hb-fade { position: absolute; left: 0; right: 0; bottom: 0; height: 60%; background: linear-gradient(180deg, transparent, #0B0907 70%); }
@keyframes hb-ride {
  0% { offset-distance: 0%; opacity: 0; }
  8% { opacity: 1; }
  88% { opacity: 1; }
  100% { offset-distance: 100%; opacity: 0; }
}
@keyframes hb-ping { from { transform: scale(1); opacity: 0.9; } to { transform: scale(3.2); opacity: 0; } }
@media (prefers-reduced-motion: reduce) {
  .hb-rider { offset-distance: 62%; animation: none; }
}
</style>
