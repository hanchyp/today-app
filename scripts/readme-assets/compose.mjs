// Builds the README images from the raw phone screenshots in ./raw:
//   docs/images/banner.png, mobile.png and one composite per feature.
// Renders small HTML pages with Playwright and captures them at 2x.
// Uses the Microsoft Edge that ships with Windows (channel "msedge"), so no
// browser download is needed. Set BROWSER_CHANNEL=chrome to use Chrome instead.
import { chromium } from "playwright";
import { readFileSync, mkdirSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const raw = path.join(here, "raw");
const out = path.join(root, "docs/images");
mkdirSync(out, { recursive: true });

// Pages are rendered with setContent (about:blank), which cannot read file:// URLs,
// so fonts and screenshots are inlined as data URIs.
const dataUri = (p, mime) => `data:${mime};base64,${readFileSync(p).toString("base64")}`;
const fontUrl = dataUri(path.join(root, "app/src/main/res/font/inter_variable.ttf"), "font/ttf");
const logoSvg = readFileSync(path.join(root, "docs/today-logo.svg"), "utf8");
const shot = (name) => {
  const p = path.join(raw, `${name}.png`);
  if (!existsSync(p)) throw new Error(`Missing raw screenshot: ${p}. Run capture_screens.py first.`);
  return dataUri(p, "image/png");
};

// Brand tokens from app/src/main/java/com/farhan/today/ui/theme/Theme.kt
const C = {
  background: "#FDFCF8", navy: "#1A1A2E", yellow: "#FFD23F", yellowTint: "#FFF3C4",
  coral: "#FF5A36", muted: "#64625B", workout: "#F1FFD9", career: "#FFF0C7",
  finance: "#FCE6F3", tasks: "#E3ECFF", chip: "#F3F1EA",
};

const base = `
  @font-face { font-family: Inter; src: url("${fontUrl}"); font-weight: 100 900; }
  * { box-sizing: border-box; margin: 0; }
  html, body { width: 100%; height: 100%; }
  body { font-family: Inter, sans-serif; color: ${C.navy}; }
  .phone { display: block; border-radius: 34px; border: 1px solid rgba(26,26,46,.10);
           box-shadow: 0 24px 60px rgba(26,26,46,.14), 0 4px 14px rgba(26,26,46,.06); }
`;

// Feature composites: 1-3 phone screens on a soft module-colored background.
const features = {
  today:   { bg: C.yellowTint, shots: ["today", "calendar"] },
  workout: { bg: C.workout,    shots: ["workout_list", "workout_session", "exercise_animation"] },
  career:  { bg: C.career,     shots: ["career_list", "career_detail"] },
  finance: { bg: C.finance,    shots: ["finance", "finance_add"] },
  tasks:   { bg: C.tasks,      shots: ["tasks", "project"] },
  settings:{ bg: C.chip,       shots: ["settings", "settings_backup"] },
  mobile:  { bg: C.background, shots: ["today", "workout_list", "finance"] },
};

function featurePage({ bg, shots }) {
  const imgs = shots.map((s) => `<img class="phone" src="${shot(s)}" style="height:760px">`).join("");
  return `<!doctype html><html><head><style>${base}
    body { background: radial-gradient(circle at 50% 0%, #ffffff 0%, ${bg} 70%);
           display: flex; align-items: center; justify-content: center; gap: 64px; }
  </style></head><body>${imgs}</body></html>`;
}

function bannerPage() {
  return `<!doctype html><html><head><style>${base}
    body { background: ${C.background}; position: relative; overflow: hidden; }
    .sun { position: absolute; right: -120px; top: -160px; width: 620px; height: 620px;
           border-radius: 50%; background: ${C.yellow}; }
    .copy { position: absolute; left: 88px; top: 50%; transform: translateY(-50%); }
    .copy svg { width: 360px; height: auto; display: block; margin-left: -10px; }
    .tag { font-size: 34px; font-weight: 700; letter-spacing: -0.5px; margin-top: 18px; }
    .sub { font-size: 19px; color: ${C.muted}; margin-top: 10px; font-weight: 500; }
    .phone { position: absolute; right: 120px; top: 56px; width: 300px; }
  </style></head><body>
    <div class="sun"></div>
    <div class="copy">${logoSvg}
      <div class="tag">One screen. Whole day.</div>
      <div class="sub">Workouts, job applications, money and tasks, offline on Android.</div>
    </div>
    <img class="phone" src="${shot("today")}">
  </body></html>`;
}

const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL || "msedge" });
async function render(html, file, width, height) {
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: 2 });
  await page.setContent(html, { waitUntil: "load" });
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: path.join(out, file) });
  await page.close();
  console.log("wrote", file);
}

const only = process.argv.slice(2);
const want = (n) => only.length === 0 || only.includes(n);
if (want("banner")) await render(bannerPage(), "banner.png", 1280, 400);
for (const [name, spec] of Object.entries(features)) {
  if (!want(name)) continue;
  const width = spec.shots.length === 3 ? 1440 : 1200;
  await render(featurePage(spec), `${name}.png`, width, 900);
}
await browser.close();
