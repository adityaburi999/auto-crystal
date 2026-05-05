# AutoCrystal – Human Pattern  
**Minecraft Java Edition 1.21.1 | Fabric**

A client-side Fabric mod that automates end-crystal PvP combat using a sophisticated human-behaviour simulation algorithm. Timing, rotation speed, position jitter, overshoot correction, reaction delays, and fatigue modelling are all tuned to be indistinguishable from a skilled human player.

---

## Requirements

| Requirement | Version |
|---|---|
| Minecraft Java Edition | **1.21.1** |
| Fabric Loader | ≥ 0.15.0 |
| Fabric API | 0.104.0+1.21.1 (or compatible) |
| Java | 21+ |

> **Version compatibility:** The mod is compiled for MC **1.21.1** only.  
> Running it on 1.21.4 or later causes a startup crash (`IllegalStateException: Can't getDevice() before it was initialized`) because Mojang changed the rendering backend in 1.21.4. If you see that crash, make sure you are on **exactly 1.21.1**.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for MC 1.21.1.
2. Drop **Fabric API** into your `mods/` folder.
3. Drop the `auto-crystal-*.jar` into your `mods/` folder.
4. Launch the game.

---

## How to use

### Step 1 – Prepare your inventory
Put **End Crystals** somewhere in your **hotbar** (slots 1–9). The mod scans all nine hotbar slots and switches to whichever slot contains a crystal automatically.

### Step 2 – Be in the right environment
End crystals can only be placed on **Obsidian** or **Bedrock**. PvP arenas on servers like Minemen Club use obsidian platforms — stand near one.

### Step 3 – Get close to an enemy
The mod targets the nearest enemy player within **6 blocks** (configurable). If no enemy is in range the mod idles silently.

### Step 4 – Toggle the mod on
Press **`X`** to enable/disable AutoCrystal. A message appears in your action bar:
- `[AutoCrystal] §aEnabled` – mod is active
- `[AutoCrystal] §cDisabled` – mod is off

### What happens automatically once enabled
1. The mod finds the closest enemy and the best obsidian block to place on.
2. It switches to the crystal slot in your hotbar.
3. It smoothly rotates toward the placement block (with human-like overshoot and micro-tremors).
4. It right-clicks the block to place the crystal.
5. It waits for the crystal entity to spawn (up to 1 second), then rotates toward it.
6. It left-clicks (attacks) the crystal after your attack cooldown reaches ≥ 90 %.
7. It enters a brief cooldown, then repeats.

All delays are sampled from a log-normal distribution centred on ~120 ms so that timing analysis looks human.

---

## Configuration

The config file is written to `.minecraft/config/autocrystal.json` on first launch.

| Key | Default | Description |
|---|---|---|
| `targetRange` | `6.0` | Blocks – max distance to search for enemy players |
| `placeRange` | `4.5` | Blocks – max reach for crystal placement |
| `minDamage` | `4.0` | Skip placement if predicted target damage is below this |
| `maxSelfDamage` | `10.0` | Skip placement if predicted self-damage exceeds this |
| `reactionTimeMs` | `120.0` | Base reaction time in ms (log-normal jitter applied on top) |
| `humanize` | `true` | Enable the full human-pattern algorithm |
| `fatigueEnabled` | `true` | Gradually slow reaction times over a long fight |

Edit the JSON file and restart the game (or rejoin a world) to apply changes.

---

## Building from source

```bash
./gradlew build
# Output: build/libs/auto-crystal-1.0.0.jar
```

Requires JDK 21. The project uses Fabric Loom 1.7.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Crash on launch: `Can't getDevice() before it was initialized` | Running on MC 1.21.4+ which changed the rendering backend | Downgrade to **MC 1.21.1** |
| Mod loads but does nothing | No enemy within 6 blocks, or no End Crystals in hotbar | Move closer to an enemy, add crystals to hotbar, press X |
| Crystals placed but not exploded | Attack cooldown not reaching threshold or crystal despawned | This is normal on high-ping servers; the mod retries automatically |
| Config changes have no effect | Old config cached in memory | Restart the game after editing `autocrystal.json` |
