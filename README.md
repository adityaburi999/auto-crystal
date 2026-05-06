# AutoCrystal – Human Pattern  
**Minecraft Java Edition 1.21.4 | Fabric**

A client-side Fabric mod that automates end-crystal PvP combat using a sophisticated human-behaviour simulation algorithm. Timing, rotation speed, position jitter, overshoot correction, reaction delays, fatigue modelling, and a ramp-up speed curve are all tuned to be indistinguishable from a skilled human player.

---

## Requirements

| Requirement | Version |
|---|---|
| Minecraft Java Edition | **1.21.4** |
| Fabric Loader | ≥ 0.15.0 |
| Fabric API | 0.114.0+1.21.4 (or compatible) |
| Java | 21+ |

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for MC 1.21.4.
2. Drop **Fabric API** into your `mods/` folder.
3. Drop the `auto-crystal-*.jar` into your `mods/` folder.
4. Launch the game.

---

## How to use

### Step 1 – Prepare your inventory
Put **End Crystals** in your **hotbar** (slots 1–9). **Manually select** the crystal slot before activating the trigger — the mod does **not** auto-switch items.

### Step 2 – Be in the right environment
End crystals can only be placed on **Obsidian** or **Bedrock**. PvP arenas on servers like Minemen Club use obsidian platforms — stand near one.

### Step 3 – Get close to an enemy
The mod only activates when an enemy player is within **6 blocks** (configurable). If no enemy is in range the trigger stays inactive.

### Step 4 – Arm the mod
Press **`X`** to arm/disarm AutoCrystal. A message appears in your action bar:
- `[AutoCrystal] Armed – hold right-click on obsidian/bedrock to crystal` – mod is armed
- `[AutoCrystal] Disarmed` – mod is off

### Step 5 – Trigger crystalling
While armed, **hold right-click on an obsidian or bedrock block**. As long as you hold right-click, all three conditions are met (crystal in main hand, valid block aimed, enemy nearby), the mod crystals automatically:

1. Finds the closest enemy player and the best obsidian/bedrock block to place on.
2. Smoothly rotates toward the placement block (human-like overshoot and micro-tremors).
3. Right-clicks the block to place the crystal.
4. Waits for the crystal entity to spawn (up to 1 second), then rotates toward it.
5. Checks the enemy's invulnerability timer (damage tick) — defers the attack until invulnerability expires so every hit lands for full damage.
6. Left-clicks (attacks) the crystal after your attack cooldown reaches ≥ 90 %.
7. Enters a brief cooldown, then repeats from step 1.

**Release right-click** at any time to immediately stop crystalling.

### Ramp-up speed
The first 15 cycles of each session are progressively faster (slow cautious start → full speed), mirroring a human warming up. The ramp resets each time you release right-click.

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
| Mod loads but does nothing | No enemy within 6 blocks, not holding an End Crystal, or not aiming at obsidian/bedrock | Move closer to an enemy, select the crystal hotbar slot, aim at a valid block, then hold right-click |
| Crystals placed but not exploded | Attack cooldown not reaching threshold or crystal despawned | Normal on high-ping servers; the mod retries automatically |
| Config changes have no effect | Old config cached in memory | Restart the game after editing `autocrystal.json` |
