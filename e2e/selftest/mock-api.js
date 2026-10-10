'use strict';
// In-process API model for provisioning self-tests; uses only loopback sockets and no database/network.
const http = require('http');
const crypto = require('crypto');
const QRCode = require('qrcode');
const { totp } = require('../support/totp');
const id = () => crypto.randomUUID();

async function start({ mfa = 'off', adminEnrolled = true, failTeamRole = '', failCleanup = false } = {}) {
  const adminSecret = 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP';
  const users = new Map([['admin@example.test', { id: id(), email: 'admin@example.test', name: 'Admin', role: 'ADMIN', password: 'Admin-Password-1', mfaSecret: adminEnrolled ? adminSecret : null, lastCounter: -1, active: true, mustChangePassword: false }]]);
  const tokens = new Map();
  const challenges = new Map();
  const events = new Map();
  const tickets = new Map();
  const scans = new Set();
  const calls = [];
  const mfaRoles = new Set(['ADMIN', 'EVENT_MANAGER']);
  const organizer = { id: id(), slug: 'lakhdatar-events', name: 'Lakhdatar Events' };
  const issueToken = (email) => { const t = 'tok-' + crypto.randomBytes(10).toString('hex'); tokens.set(t, email); return t; };

  const server = http.createServer(async (req, res) => {
    const chunks = []; for await (const c of req) chunks.push(c);
    let body = {};
    try { body = chunks.length ? JSON.parse(Buffer.concat(chunks)) : {}; } catch { body = {}; }
    const url = new URL(req.url, 'http://127.0.0.1');
    const path = url.pathname;
    calls.push(`${req.method} ${path}`);
    const send = (status, json) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(json === undefined ? '' : JSON.stringify(json)); };
    const actor = () => {
      const email = tokens.get((req.headers.authorization || '').replace(/^Bearer\s+/i, ''));
      const u = email && users.get(email);
      return u && u.active ? u : null;
    };
    const need = (...roles) => {
      const a = actor();
      if (!a) { send(401, { code: 'UNAUTHORIZED' }); return null; }
      if (!roles.includes(a.role)) { send(403, { code: 'FORBIDDEN' }); return null; }
      return a;
    };
    let match;

    if (req.method === 'POST' && path === '/api/v1/auth/login') {
      const email = String(body.email || '').toLowerCase(); const u = users.get(email);
      if (!u || !u.active || u.password !== body.password) return send(401, { code: 'BAD_CREDENTIALS' });
      if (mfa === 'on' && mfaRoles.has(u.role)) {
        const ch = 'challenge-' + crypto.randomBytes(32).toString('base64url'); challenges.set(ch, email);
        return send(200, { accessToken: '', mfaRequired: true, mfaSetupRequired: !u.mfaSecret, mfaChallengeToken: ch });
      }
      return send(200, { accessToken: issueToken(email), role: u.role, mustChangePassword: u.mustChangePassword });
    }
    if (req.method === 'POST' && path === '/api/v1/auth/mfa/enroll') {
      const email = challenges.get(body.challengeToken); const u = email && users.get(email);
      if (!u || u.mfaSecret) return send(401, { code: 'CHALLENGE' });
      u.mfaSecret = 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ'; return send(200, { secret: u.mfaSecret });
    }
    if (req.method === 'POST' && (path === '/api/v1/auth/mfa/confirm' || path === '/api/v1/auth/mfa/verify')) {
      const email = challenges.get(body.challengeToken); const u = email && users.get(email);
      if (!u || !u.mfaSecret) return send(401, { code: 'CHALLENGE' });
      const counter = Math.floor(Date.now() / 30_000);
      if (body.code !== totp(u.mfaSecret) || counter <= u.lastCounter) return send(401, { code: 'MFA_INVALID' });
      u.lastCounter = counter; challenges.delete(body.challengeToken);
      return send(200, { accessToken: issueToken(email), role: u.role });
    }
    if (req.method === 'POST' && path === '/api/v1/auth/change-password') {
      const a = need('STAFF', 'EVENT_MANAGER'); if (!a) return;
      if (a.password !== body.currentPassword || typeof body.newPassword !== 'string' || body.newPassword.length < 12) return send(400, { code: 'PASSWORD_INVALID' });
      a.password = body.newPassword; a.mustChangePassword = false; return send(200, { accessToken: issueToken(a.email), role: a.role });
    }

    if (req.method === 'GET' && path === '/api/v1/admin/organizers') { if (!need('ADMIN')) return; return send(200, { organizers: [organizer] }); }
    if (req.method === 'GET' && path === '/api/v1/admin/events') {
      if (!need('ADMIN')) return;
      return send(200, [...events.values()].map((e) => ({ id: e.id, slug: e.slug, status: e.status, name: e.name })));
    }
    if ((match = /^\/api\/v1\/admin\/organizers\/([^/]+)\/team(?:\/(staff|managers|([^/]+)))?$/.exec(path))) {
      const a = need('ADMIN'); if (!a) return;
      const slug = decodeURIComponent(match[1]); const op = match[2]; const userId = match[3];
      if (slug !== organizer.slug) return send(404, { code: 'ORGANIZER_NOT_FOUND' });
      if (req.method === 'GET' && !op) {
        const members = [...users.values()].filter((u) => u.role === 'STAFF' || u.role === 'EVENT_MANAGER');
        return send(200, { staff: members.filter((u) => u.role === 'STAFF').map((u) => ({ id: u.id, email: u.email, active: u.active })), managers: members.filter((u) => u.role === 'EVENT_MANAGER').map((u) => ({ id: u.id, email: u.email, active: u.active })) });
      }
      if (req.method === 'POST' && ['staff', 'managers'].includes(op)) {
        if (failTeamRole === op) return send(503, { code: 'MOCK_TEAM_FAILURE' });
        const email = String(body.email || '').toLowerCase();
        if (users.has(email)) return send(409, { code: 'USER_EXISTS' });
        const role = op === 'staff' ? 'STAFF' : 'EVENT_MANAGER';
        const u = { id: id(), email, name: body.name, role, password: body.password, mfaSecret: null, lastCounter: -1, active: true, mustChangePassword: true, organizerSlug: slug };
        users.set(email, u);
        return send(201, { member: { id: u.id, email: u.email, name: u.name, active: true }, delivery: 'PASSWORD_SET' });
      }
      if (req.method === 'PATCH' && userId) {
        const u = [...users.values()].find((item) => item.id === userId);
        if (!u || u.organizerSlug !== slug) return send(404, { code: 'MEMBER_NOT_FOUND' });
        if (typeof body.active === 'boolean') u.active = body.active;
        return send(200, { id: u.id, email: u.email, active: u.active });
      }
    }
    if ((match = /^\/api\/v1\/admin\/events\/([^/]+)(\/.*)?$/.exec(path))) {
      const a = need('ADMIN'); if (!a) return;
      const event = events.get(match[1]); if (!event) return send(404, { code: 'EVENT_NOT_FOUND' });
      const sub = match[2] || '';
      if (req.method === 'GET' && sub === '') return send(200, { id: event.id, slug: event.slug, status: event.status, ticketTypes: event.ticketTypes });
      if (req.method === 'GET' && sub === '/publish-readiness') return send(200, { ready: true, blockers: [], warnings: [] });
      if (req.method === 'POST' && sub === '/publish') { event.status = 'PUBLISHED'; return send(204); }
      if (req.method === 'POST' && sub === '/cancel') {
        if (failCleanup) return send(503, { code: 'MOCK_CANCEL_FAILURE' });
        event.status = 'CANCELLED'; return send(204);
      }
      if (req.method === 'POST' && sub === '/staff') {
        const u = users.get(String(body.email || '').toLowerCase());
        if (!u || u.role !== 'STAFF' || !u.active) return send(404, { code: 'STAFF_NOT_FOUND' });
        event.staff.set(u.email, body.gate); return send(204);
      }
      if (req.method === 'DELETE' && sub === '/staff') {
        if (failCleanup) return send(503, { code: 'MOCK_UNASSIGN_FAILURE' });
        event.staff.delete(url.searchParams.get('email')); return send(204);
      }
      if (req.method === 'POST' && sub === '/managers') {
        const u = users.get(String(body.email || '').toLowerCase());
        if (!u || u.role !== 'EVENT_MANAGER' || !u.active) return send(404, { code: 'MANAGER_NOT_FOUND' });
        event.managers.add(u.email); return send(204);
      }
      if (req.method === 'DELETE' && sub === '/managers') {
        if (failCleanup) return send(503, { code: 'MOCK_UNASSIGN_FAILURE' });
        event.managers.delete(url.searchParams.get('email')); return send(204);
      }
    }
    if (req.method === 'POST' && path === '/api/v1/admin/events') {
      if (!need('ADMIN')) return;
      if ([...events.values()].some((e) => e.slug === body.slug)) return send(409, { code: 'EVENT_SLUG_EXISTS' });
      const e = { id: id(), slug: body.slug, name: body.name, status: 'DRAFT', paymentProvider: body.paymentProvider,
        ticketTypes: (body.ticketTypes || []).map((t) => ({ id: id(), ...t })), staff: new Map(), managers: new Set() };
      events.set(e.id, e); return send(201, { id: e.id, slug: e.slug, status: e.status });
    }
    if (req.method === 'POST' && path === '/api/v1/admin/manager-tickets/complimentary') {
      const a = need('EVENT_MANAGER'); if (!a) return;
      const event = events.get(body.eventId);
      if (!event || !event.managers.has(a.email) || !a.active) return send(403, { code: 'FORBIDDEN' });
      const issued = [];
      for (let i = 0; i < body.quantity; i++) {
        const ticket = { ticketId: id(), ticketNumber: `T-${i + 1}`, accessToken: crypto.randomBytes(24).toString('base64url'), eventId: event.id, qrToken: '' };
        tickets.set(ticket.ticketId, ticket); issued.push({ ticketId: ticket.ticketId, ticketNumber: ticket.ticketNumber, accessToken: ticket.accessToken });
      }
      return send(201, { orderPublicId: id(), tickets: issued, emailStatus: 'SENT' });
    }
    if (req.method === 'GET' && (match = /^\/api\/v1\/public\/tickets\/([^/]+)$/.exec(path))) {
      const ticket = tickets.get(match[1]);
      if (!ticket || req.headers['x-ticket-token'] !== ticket.accessToken) return send(401, { code: 'TICKET_TOKEN_INVALID' });
      if (!ticket.qrToken) ticket.qrToken = `LK1.${ticket.ticketId}.${crypto.randomBytes(20).toString('base64url')}`;
      return send(200, { ticketId: ticket.ticketId, qrDataUri: await QRCode.toDataURL(ticket.qrToken, { errorCorrectionLevel: 'H', width: 320 }) });
    }
    if (req.method === 'POST' && path === '/api/v1/checkin/scan') {
      const a = need('STAFF'); if (!a) return;
      const event = events.get(body.eventId);
      if (!event || !event.staff.has(a.email) || event.staff.get(a.email) !== body.gate) return send(403, { code: 'UNAUTHORIZED_GATE' });
      const ticket = [...tickets.values()].find((item) => item.qrToken === body.qrToken && item.eventId === event.id);
      if (!ticket) return send(200, { result: 'INVALID' });
      const first = !scans.has(ticket.ticketId); scans.add(ticket.ticketId);
      return send(200, { result: first ? 'ACCEPTED' : 'ALREADY_USED', ticketPosition: 2, orderTicketCount: 2 });
    }
    send(404, { code: 'NOT_FOUND', path });
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  return { url: `http://127.0.0.1:${server.address().port}`, adminSecret, events, calls, users,
    close: () => new Promise((resolve, reject) => server.close((err) => err ? reject(err) : resolve())) };
}

module.exports = { start };
