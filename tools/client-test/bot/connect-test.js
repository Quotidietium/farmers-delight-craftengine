const mineflayer = require('mineflayer')
const bot = mineflayer.createBot({
  host: '127.0.0.1', port: 25565, username: 'regressbot', auth: 'offline',
  version: '1.21.11', skipValidation: true,
})
bot.on('login', () => console.log('[bot] LOGIN ok'))
bot.on('spawn', () => { console.log('[bot] SPAWN at', bot.entity.position); setTimeout(() => process.exit(0), 2000) })
bot.on('error', e => { console.log('[bot] ERROR:', e.message) })
bot.on('kicked', r => { console.log('[bot] KICKED:', r); process.exit(1) })
// raw packet trace to see where the flow stalls
const seen = {}
bot._client.on('packet', (data, meta) => {
  seen[meta.name] = (seen[meta.name] || 0) + 1
})
setInterval(() => {
  const top = Object.entries(seen).slice(-6).map(([k, v]) => `${k}:${v}`).join(', ')
  console.log('[pkts]', top)
}, 5000)
setTimeout(() => { console.log('[bot] timeout'); process.exit(1) }, 40000)
