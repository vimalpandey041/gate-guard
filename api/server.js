const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
require('dotenv').config();

const path = require("path");
const app = express();
app.use(express.static(path.join(__dirname, "public")));
app.use(cors());
app.use(express.json());

// ── MongoDB Connection ──────────────────────────────────────────
mongoose.connect(process.env.MONGO_URI)
  .then(() => console.log('✅ MongoDB connected — gate_guard'))
  .catch(err => console.error('❌ MongoDB error:', err));

// ── Schema ──────────────────────────────────────────────────────
const configSchema = new mongoose.Schema({
  type: { type: String, required: true, unique: true },
  channels: [String],
  sites: [String],
  message: String,
  updatedAt: { type: Date, default: Date.now }
});
const Config = mongoose.model('Config', configSchema, 'config');

// ── Defaults ────────────────────────────────────────────────────
const DEFAULT_CHANNELS = [
  "pw-solutions","GOClassesforGATECS","GATEWallahbyPW",
  "gatewallah_cse_da","Gatecsit-dsai","UnacademyComputerScience",
  "GfG_GATE","AmitKhuranaSir","DreamMaths"
];
const DEFAULT_BLOCKED = [
  "reddit.com","x.com","twitter.com","instagram.com",
  "cricbuzz.com","jiohotstar.com","hotstar.com",
  "nextdns.io"
];

// ── Seed DB ─────────────────────────────────────────────────────
async function seedDB() {
  if (!await Config.findOne({ type: 'allowed_channels' }))
    await Config.create({ type: 'allowed_channels', channels: DEFAULT_CHANNELS });
  if (!await Config.findOne({ type: 'blocked_sites' }))
    await Config.create({ type: 'blocked_sites', sites: DEFAULT_BLOCKED });
  if (!await Config.findOne({ type: 'blocked_message' }))
    await Config.create({ type: 'blocked_message', message: 'This page is restricted by GATE Guard to keep you focused on GATE preparation.' });
  console.log('📦 DB seeded');
}
mongoose.connection.once('open', seedDB);

// ═══════════════════════════════════════════════════════════════
//  ROUTES
// ═══════════════════════════════════════════════════════════════

// All config (extension fetches this)
app.get('/api/config', async (req, res) => {
  try {
    const [ch, bl, msg] = await Promise.all([
      Config.findOne({ type: 'allowed_channels' }),
      Config.findOne({ type: 'blocked_sites' }),
      Config.findOne({ type: 'blocked_message' })
    ]);
    res.json({
      allowedChannels: ch?.channels || DEFAULT_CHANNELS,
      blockedSites: bl?.sites || DEFAULT_BLOCKED,
      blockedMessage: msg?.message || 'Blocked by GATE Guard',
      updatedAt: ch?.updatedAt || new Date()
    });
  } catch (err) { res.status(500).json({ error: err.message }); }
});

// Channels
app.get('/api/channels', async (req, res) => {
  const doc = await Config.findOne({ type: 'allowed_channels' });
  res.json({ channels: doc?.channels || DEFAULT_CHANNELS });
});

app.post('/api/channels/add', async (req, res) => {
  const { channel } = req.body;
  if (!channel) return res.status(400).json({ error: 'channel required' });
  const doc = await Config.findOneAndUpdate(
    { type: 'allowed_channels' },
    { $addToSet: { channels: channel }, updatedAt: new Date() },
    { new: true, upsert: true }
  );
  res.json({ message: `✅ "${channel}" added!`, channels: doc.channels });
});

app.post('/api/channels/remove', async (req, res) => {
  const { channel } = req.body;
  if (!channel) return res.status(400).json({ error: 'channel required' });
  const doc = await Config.findOneAndUpdate(
    { type: 'allowed_channels' },
    { $pull: { channels: channel }, updatedAt: new Date() },
    { new: true }
  );
  res.json({ message: `❌ "${channel}" removed!`, channels: doc.channels });
});

// Blocked sites
app.get('/api/blocked', async (req, res) => {
  const doc = await Config.findOne({ type: 'blocked_sites' });
  res.json({ sites: doc?.sites || DEFAULT_BLOCKED });
});

app.post('/api/blocked/add', async (req, res) => {
  const { site } = req.body;
  if (!site) return res.status(400).json({ error: 'site required' });
  const doc = await Config.findOneAndUpdate(
    { type: 'blocked_sites' },
    { $addToSet: { sites: site }, updatedAt: new Date() },
    { new: true, upsert: true }
  );
  res.json({ message: `🚫 "${site}" blocked!`, sites: doc.sites });
});

app.post('/api/blocked/remove', async (req, res) => {
  const { site } = req.body;
  if (!site) return res.status(400).json({ error: 'site required' });
  const doc = await Config.findOneAndUpdate(
    { type: 'blocked_sites' },
    { $pull: { sites: site }, updatedAt: new Date() },
    { new: true }
  );
  res.json({ message: `✅ "${site}" unblocked!`, sites: doc.sites });
});

// Message
app.post('/api/message', async (req, res) => {
  const { message } = req.body;
  if (!message) return res.status(400).json({ error: 'message required' });
  const doc = await Config.findOneAndUpdate(
    { type: 'blocked_message' },
    { message, updatedAt: new Date() },
    { new: true, upsert: true }
  );
  res.json({ message: `✅ Updated!`, blockedMessage: doc.message });
});

// Health
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', service: 'gate-guard-api' });
});

const PORT = process.env.PORT || 4100;
app.listen(PORT, () => console.log(`🛡️  GATE Guard API on port ${PORT}`));
