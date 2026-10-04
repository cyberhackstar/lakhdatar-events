/** Shared look for the two single-card password pages (accept invite, change password). Matches the login screen. */
export const SET_PASSWORD_STYLES = `
:host{display:block;color-scheme:dark}
.page{min-height:100vh;background:radial-gradient(circle at 20% 20%,rgba(116,47,84,.35),transparent 45%),linear-gradient(145deg,#130c14,#08070b);color:#fff;display:grid;place-items:center;padding:24px}
.card{width:min(460px,100%);background:#100d14;border:1px solid rgba(255,255,255,.1);border-radius:20px;padding:clamp(22px,5vw,36px)}
.brand{font-size:24px;margin-bottom:22px}
.eyebrow{text-transform:uppercase;letter-spacing:.15em;color:#8b8390;font-size:10px;font-weight:800}
h1{font-size:clamp(30px,7vw,40px);letter-spacing:-.04em;line-height:1;margin:12px 0 10px}
p{color:#8b838f;font-size:13px;line-height:1.6;margin:0 0 20px}
form{display:grid;gap:16px}
label{display:grid;gap:8px;font-size:12px;font-weight:700;color:#cfc8d2}
input{height:54px;background:#08070b;border:1px solid rgba(255,255,255,.14);border-radius:13px;color:#fff;padding:0 14px;outline:none;font-size:16px;color-scheme:dark;width:100%;min-width:0}
input:focus{border-color:#d4a64e;box-shadow:0 0 0 4px rgba(212,166,78,.1)}
input.invalid{border-color:#b86a6a}
small{color:#7d7582;font-size:12px;font-weight:400}
.err{color:#e0a1a1;font-size:12px;font-weight:600}
.alert{padding:12px 14px;border-radius:12px;font-size:13px;line-height:1.5}
.alert.error{background:rgba(180,70,70,.15);border:1px solid rgba(220,140,140,.4);color:#f0b8b8}
.alert.info{background:rgba(212,166,78,.12);border:1px solid rgba(212,166,78,.35);color:#ecd59a;margin-bottom:16px}
button.go{height:56px;border:0;border-radius:14px;background:#fff;color:#171218;font-weight:800;font-size:15px;cursor:pointer}
button.go:disabled{opacity:.45;cursor:not-allowed}
a.back{display:inline-block;margin-top:18px;color:#746b78;text-decoration:none;font-size:12px}
`;
