# Void Rush

<img src="art/rush-icon-512.png" width="160" align="right" alt="Void Rush emblem">

A side-scrolling endless runner for Android (13+), from the VOID family. You've stolen a prototype gravity thruster
from a corporate orbital foundry, and now you have to get out. Burst through the lab wall and run the foundry's
corridors for as long as you can last. The security grid is in your way.

It's written in Kotlin and Jetpack Compose, with a hand-written OpenGL ES 2 renderer. It uses no third-party
libraries and has **no telemetry, no ads, no accounts and no in-app purchases**. The game goes online only to check
this repository for its own updates. It plays in landscape. Every mesh is built in code and every icon is a neon line
drawing.

## Run
- **Thrust:** hold anywhere to rise, and let go to fall. That's the whole control scheme.
- **Hazards:**
  - **Zappers:** live wires between two nodes. Some spin and some slide.
  - **Missiles:** a seeker tracks you from the edge, locks on, then flies straight across.
  - **Lasers:** a beam across the whole corridor. It warns you first, then fires.
  - **Drones:** security drones hovering in your path.
- **Credit chips** are lined up in shapes along the way. Brushing past a hazard counts as a **near miss**.
- **Rig tokens** put you in a vehicle for a while. If you take a hit inside one, you lose the rig instead of the run.
- **Spin tokens** buy spins on the prize wheel after the run.
- **Caught?** You can revive and carry on. Each revive costs twice the last, and you have 10 seconds to decide.

## Rigs
Each one changes the controls:
- **Strider mech:** hold to jump, and it stomps drones flat.
- **Flip boots:** tap to flip between the floor and the ceiling.
- **Glider:** hold to climb, and let go to glide.
- **Blink drive:** tap to blink to the other side of the corridor.
- **Void eel:** hold to rise, let go to dive, and it pulls credits in.

## Shop
- **Packs (6):** jetpacks with their own exhaust and look, from the Prototype to the Void core.
- **Gadgets (8):** you can equip two at a time:
  - credit magnet
  - feather vanes
  - anvil boots
  - rig radar
  - lucky chip
  - Daredevil (near misses pay)
  - prism lens
  - second wind (a free first revive)
- **Upgrades:** magnet reach, rig armour, credit value and thruster tuning.
- **Boosts:** one-run items:
  - head start
  - shield bubble
  - mega head start

## Locker
- **Colours:** suit, trim and glow, each picked from 100 swatches.
- **Finishes (12):** from matte and chrome to holo, magma, prism and Void.
- **Decals (15):** stripes, circuits, scales, runes and more.
- **Extras:** trails, exhaust flames, sparkle bursts, titles and emblems.

You can try anything on before you buy it. Tap it again to buy it, or press "Back to my look".

## Progress
- **Credits** come from running.
- **Cores**, which are rarer, come from missions, medals, the prize wheel and the daily supply drop.
- **Shards** are a cosmetic currency.
- **Levels** come from XP and unlock gear and cosmetics.
- **Daily missions:** three new ones every day, with bonus cores when all three are done.
- **Medals:** 24 of them, each paying cores. Some also unlock a cosmetic.
- **Daily supply drop:** credits, a core and a shard every day. The drop grows with your streak and is bigger every seventh day.
- **Prize wheel:** spin tokens collected on a run turn into spins at the end of it.

## VOID look
- The seven VOID themes are built in, and the app icon can follow the theme.
- Graphics quality offers Battery saver, Balanced and Beautiful.
- There is a 60 fps cap, a left-handed layout, and a haptics and sound toggle.
- Every sound and the synthwave music are synthesized in code.
- Play allocates almost nothing, because hazards, chips and particles live in preallocated pools.

## Updates
Settings › Updates shows the version and checks this repository's GitHub releases. Each download is checked against
the published SHA-256 before Android's installer takes it. Nothing else ever leaves the phone.

## Build
```
./build.sh testDebugUnitTest assembleRelease   # → VoidRush.apk
```
It needs `../void-ui` next to it. The unit tests do three things:
- run the world headless, checking that an idle runner gets caught and that the menu demo never dies
- check revives and scoring
- check the economy: buying, upgrades, revive costs, the wheel and levels

`./release.sh "What changed"` bumps the version and publishes. `art/gen_icon.py` regenerates the launcher icons for
every theme.

## Licence
GPL-3.0. See [LICENSE](LICENSE).
