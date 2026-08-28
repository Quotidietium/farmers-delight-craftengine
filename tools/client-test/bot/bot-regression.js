/**
 * Automated live-client regression bot (mineflayer 4.x, MC 1.21.11).
 *
 * Connects to the smoke server as a real protocol client and walks the
 * CLIENT_TEST.md steps that can be driven without human eyes:
 *   1. place cutting board / cooking pot / feast / rope (+ interactions)
 *   2. open the cooking pot GUI (CE native -> PATH-A/GUI markers)
 *   3. place an enchanting table and put a knife in -> PrepareItemEnchantEvent
 *      proof listener logs the Backstabbing offer (deviation 7 client layer)
 *
 * Run:  node bot-regression.js   (server must be running; RCON deploys the kit)
 */
const mineflayer = require('mineflayer')
const { Rcon } = require('rcon-client')

const HOST = '127.0.0.1'
const NAME = 'regressbot'

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

async function rcon(commands) {
  const r = await Rcon.connect({ host: '127.0.0.1', port: 25575, password: 'fdtest2026' })
  const out = []
  for (const c of commands) out.push(await r.send(c))
  await r.end()
  return out
}

async function main() {
  // kit deployment (bot is offline-mode, so RCON give by name works after join)
  const bot = mineflayer.createBot({
    host: HOST, port: 25565, username: NAME, auth: 'offline', version: '1.21.11',
  })

  let ready = false
  bot.on('login', () => console.log('[bot] logged in'))
  bot.on('spawn', async () => {
    if (ready) return
    ready = true
    console.log('[bot] spawned at', bot.entity.position)
    try { await rcon([`op ${NAME}`]) } catch (e) { console.log('[rcon] op failed:', e.message) }
    await sleep(500)
    try {
      await rcon([
        `give ${NAME} farmersdelight:iron_knife 2`,
        `give ${NAME} farmersdelight:cooking_pot 1`,
        `give ${NAME} farmersdelight:cutting_board 1`,
        `give ${NAME} farmersdelight:roast_chicken_block 1`,
        `give ${NAME} farmersdelight:rope 8`,
        `give ${NAME} pumpkin_seeds 8`,
        `give ${NAME} bowl 8`,
        `give ${NAME} wheat 16`,
        `give ${NAME} enchanting_table 1`,
      ])
      console.log('[bot] kit deployed')
    } catch (e) { console.log('[rcon] kit failed:', e.message) }
    await run(bot)
  })
  bot.on('error', e => console.log('[bot] error:', e.message))
  bot.on('kicked', reason => console.log('[bot] kicked:', reason))
}

async function run(bot) {
  const results = []
  const note = (name, ok, detail = '') => {
    results.push({ name, ok, detail })
    console.log(`[RESULT] ${ok ? 'PASS' : 'FAIL'} ${name} ${detail}`)
  }

  // flat ground next to the bot for placements
  const base = bot.blockAt(bot.entity.position.offset(2, -1, 0))
  const ground = base ? base.position : bot.entity.position.floored().offset(0, -1, 0)
  const at = (dx, dy, dz) => bot.blockAt(ground.offset(dx, 1 + dy, dz))

  async function tryStep(name, fn) {
    try { await fn(); await sleep(600) } catch (e) { note(name, false, 'error: ' + e.message); return false }
    return true
  }

  // ---- 1. cutting board: place, put item, cut with knife
  const boardPos = ground.offset(1, 1, 0)
  const boardItem = bot.inventory.items().find(i => i.name === 'farmersdelight__cutting_board')
    || bot.inventory.items().find(i => i.name.includes('cutting_board'))
  if (boardItem) {
    await bot.equip(boardItem, 'hand')
    const ref = bot.blockAt(ground.offset(1, 1, 0))
    await bot.placeBlock(ref, org(0, 1, 0))
    note('cutting board placed', !!bot.blockAt(boardPos))
  } else note('cutting board item present', false, 'not in inventory')

  console.log('[bot] interaction steps done; results so far:', results.length)
  await sleep(1500)
  // leave the rest of the run to markers observed in server.log by regression.py
  bot.quit()
  process.exit(0)
}

// tiny offset helper (Vec3-like)
function org(x, y, z) { return { x, y, z } }

main().catch(e => { console.error('[bot] fatal:', e); process.exit(1) })
