/**
 * Shared look for every admin page. Pages import this string in their `styles` array.
 * Form controls are forced to the light scheme: the public site sets `color-scheme: dark` globally, which made
 * unstyled inputs render white-on-white and browser-autofilled fields render as dark blue bars inside the light console.
 */
export const ADMIN_UI_STYLES = `
:host{display:block;--ink:#1a151b;--muted:#6f6573;--line:#e5dfd7;--soft:#faf8f5;--gold:#9a7424;color:var(--ink)}
.page-head{display:flex;justify-content:space-between;align-items:flex-end;gap:16px;flex-wrap:wrap;margin-bottom:24px}
.eyebrow{text-transform:uppercase;letter-spacing:.16em;font-size:11px;font-weight:800;color:var(--gold)}
.title{font-family:var(--display);font-size:clamp(30px,4.2vw,46px);letter-spacing:-.04em;line-height:1;margin:8px 0 8px;font-weight:600}
.sub{margin:0;color:var(--muted);font-size:14px;line-height:1.6;max-width:640px}
.card{background:#fff;border:1px solid var(--line);border-radius:20px;padding:24px;min-width:0}
.card+.card{margin-top:16px}
.card h2{font-family:var(--display);font-size:24px;letter-spacing:-.03em;margin:6px 0 6px;font-weight:600}
.card>p{margin:0 0 16px;color:var(--muted);font-size:13px;line-height:1.6}
.a-btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:44px;padding:0 18px;border-radius:12px;border:1px solid #d8d0c7;background:#fff;color:var(--ink);font-weight:700;font-size:13px;text-decoration:none;cursor:pointer;white-space:nowrap}
.a-btn:hover{border-color:#b9ad9d}
.a-btn.primary{background:#17121a;border-color:#17121a;color:#fff}.a-btn.primary:hover{background:#2b2230}
.a-btn.danger{color:#8f3e42;border-color:#ebcdcf}
.a-btn.sm{min-height:34px;padding:0 12px;font-size:12px;border-radius:9px}
.a-btn[disabled],.a-btn:disabled{opacity:.45;cursor:not-allowed}
.field{display:grid;gap:6px;font-size:13px;font-weight:700;color:#4a414d;min-width:0}
.field .req{color:#a24b4b}
.field small{font-weight:400;color:#8a8190;font-size:12px;line-height:1.4}
.field .err{font-weight:600;color:#a24b4b;font-size:12px}
input,select,textarea{color-scheme:light;color:var(--ink);background:#fff}
.field input,.field select,.field textarea,.cell-input{width:100%;min-width:0;min-height:46px;border:1px solid #d9d2c8;border-radius:12px;padding:10px 13px;background:#fff;color:var(--ink);font:inherit;font-size:16px;line-height:1.35;outline:0}
.field textarea{resize:vertical;min-height:96px}
.field input::placeholder,.field textarea::placeholder,.cell-input::placeholder{color:#a79fa9;opacity:1}
.field input:focus,.field select:focus,.field textarea:focus,.cell-input:focus{border-color:#9e8150;box-shadow:0 0 0 3px rgba(158,129,80,.14)}
.field input.invalid,.field select.invalid,.cell-input.invalid{border-color:#d49a9a;background:#fffafa}
input:-webkit-autofill,input:-webkit-autofill:hover,input:-webkit-autofill:focus,select:-webkit-autofill{-webkit-text-fill-color:#1a151b;box-shadow:0 0 0 1000px #fff inset;caret-color:#1a151b}
.alert{padding:12px 14px;border-radius:12px;font-size:13px;line-height:1.5;margin:14px 0}
.alert.error{background:#fdeeee;border:1px solid #efb9b9;color:#8c2f2f}
.alert.success{background:#eaf6ee;border:1px solid #b8dcc3;color:#23623a}
.alert.info{background:#fff7e0;border:1px solid #ecd59a;color:#6d5311}
.alert .a-btn{margin-left:10px}
.status{display:inline-block;font-size:10px;font-weight:800;letter-spacing:.08em;color:#6f6573;background:#f1edef;border-radius:999px;padding:5px 9px;text-transform:uppercase}
.status.published{background:#e4f2e7;color:#2f6a3d}.status.draft{background:#fff3d6;color:#7a5a12}.status.cancelled{background:#fde8e8;color:#8c2f2f}
.empty{text-align:center;color:#8a8190;padding:40px 20px;font-size:14px}
.skeleton-line{height:16px;border-radius:8px;background:linear-gradient(90deg,#eee9e1,#f7f3ec,#eee9e1);margin:10px 0}
`;
