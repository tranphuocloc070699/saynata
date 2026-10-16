/**
 * The card's CSS, inside the Shadow DOM so the page cannot change it. Colours are the app's "Sage and Teal"
 * palette (docs/design/design-system.md, light mode only, like the app).
 */
export const STYLES = `
:host { all: initial; }
* { box-sizing: border-box; }
.layer { position: absolute; top: 0; left: 0; width: 0; height: 0; z-index: 2147483647;
  --bg: #F3F5F1; --surface: #FFFFFF; --meaning: #E8EEE9; --hairline: #D9E0DA; --chip: #DCEBE4;
  --text: #1F2A26; --text2: #4A5A53; --muted: #5F6E67; --accent: #2F6F62; --error: #B3402F;
  --serif: "New York", ui-serif, Georgia, "Times New Roman", serif;
  --sans: -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
  font-family: var(--sans); color: var(--text); font-size: 14px; line-height: 1.5; }
button { font: inherit; color: inherit; margin: 0; cursor: pointer; }
button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

.trigger { position: absolute; width: 28px; height: 28px; border-radius: 50%; border: 2px solid #fff; padding: 0;
  background: var(--accent); color: #fff; font: 600 14px/1 var(--serif); display: flex; align-items: center;
  justify-content: center; box-shadow: 0 2px 8px rgba(31,42,38,.28); animation: pop .12s ease-out; }
.trigger:hover { filter: brightness(1.1); }
@keyframes pop { from { transform: scale(.6); opacity: 0; } to { transform: scale(1); opacity: 1; } }

.card { position: absolute; width: 340px; max-width: calc(100vw - 16px); max-height: min(70vh, 560px); overflow-y: auto;
  background: var(--surface); border: 1px solid var(--hairline); border-radius: 12px;
  box-shadow: 0 8px 28px rgba(31,42,38,.18), 0 2px 6px rgba(31,42,38,.08); }
.card:focus { outline: none; }
.card.enter { animation: pop .12s ease-out; }
.head { display: flex; align-items: flex-start; gap: 8px; padding: 10px 10px 6px 14px; }
.head .title { flex: 1; min-width: 0; padding-top: 4px; }
.quote { font: italic 14px/1.45 var(--serif); color: var(--text2); overflow-wrap: anywhere;
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.headword { font-size: 20px; font-weight: 600; overflow-wrap: anywhere; }
.ipa { color: var(--muted); margin-left: 6px; }
.actions { display: flex; gap: 6px; flex: none; }
.icon { width: 30px; height: 30px; border-radius: 50%; border: 1px solid var(--hairline); background: var(--surface);
  color: var(--text2); display: inline-flex; align-items: center; justify-content: center; padding: 0; }
.icon:hover { background: var(--bg); }
.icon.active { color: var(--accent); border-color: var(--accent); }
.icon.prominent { width: 32px; height: 32px; background: var(--accent); border-color: var(--accent); color: #fff; }

.block { margin: 4px 14px 12px; background: var(--meaning); border-radius: 10px; padding: 10px 12px; }
.label { font-size: 11px; font-weight: 600; letter-spacing: .04em; text-transform: uppercase; color: var(--muted); }
.meaning { font: 15px/1.55 var(--serif); color: var(--text); margin-top: 2px; overflow-wrap: anywhere; }
.caret { display: inline-block; width: 2px; height: 15px; background: var(--accent); vertical-align: -2px; margin-left: 2px;
  animation: blink 1s steps(2) infinite; }
@keyframes blink { 50% { opacity: 0; } }
.message { display: flex; align-items: center; gap: 10px; }
.message .text { flex: 1; }
.message > svg { color: var(--accent); width: 20px; height: 20px; flex: none; }
.message.error > svg { color: var(--error); }

.section { padding: 0 14px 10px; }
.word-row { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 6px 0;
  border-bottom: 1px solid #EEF2EF; }
.word-row:last-child { border-bottom: 0; }
.word-row .word-text { flex: 1; min-width: 0; }
.word-row b { font-weight: 600; }
.icon.small { width: 26px; height: 26px; flex: none; color: var(--accent); }
.word-row .gloss { color: var(--muted); }
.chip { font-size: 11px; font-weight: 500; background: var(--chip); color: var(--accent); border-radius: 999px;
  padding: 1px 8px; white-space: nowrap; flex: none; }

.sense { display: flex; gap: 8px; align-items: flex-start; padding: 6px 14px 4px; }
.num { width: 20px; height: 20px; border-radius: 50%; background: var(--chip); color: var(--accent); font-size: 11px;
  font-weight: 600; display: inline-flex; align-items: center; justify-content: center; flex: none; margin-top: 2px; }
.definition { flex: 1; font: 15px/1.5 var(--serif); }
.media-row { padding: 2px 14px 8px 42px; }
.media { width: 240px; max-width: 100%; aspect-ratio: 16 / 9; border-radius: 10px; overflow: hidden;
  background: var(--meaning); border: 1px solid var(--hairline); }
.media img, .media video, .media iframe { width: 100%; height: 100%; object-fit: cover; display: block; border: 0;
  opacity: 0; transition: opacity .2s; }
.media.loaded img, .media.loaded video, .media.loaded iframe { opacity: 1; }
.example { padding: 0 14px 8px 42px; font: italic 13px/1.5 var(--serif); color: var(--text2); }

.fold { border-top: 1px solid var(--hairline); }
.fold-button { width: 100%; display: flex; align-items: center; justify-content: space-between; border: 0;
  background: none; padding: 10px 14px; font-size: 13px; font-weight: 600; color: var(--text2); text-align: left; }
.fold-button svg { transition: transform .15s; }
.fold-button[aria-expanded="true"] svg { transform: rotate(180deg); }
.fold-body { padding: 0 14px 12px; color: var(--text); overflow-wrap: anywhere; }
.fold-body p { margin: 0 0 4px; }

.skeleton { height: 12px; border-radius: 6px; background: #E3E9E4; margin: 8px 0; animation: pulse 1.2s ease-in-out infinite; }
@keyframes pulse { 50% { opacity: .5; } }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
`;
