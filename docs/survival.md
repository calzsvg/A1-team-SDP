# Survival

Choose **Play > Survival**. Survive as long as possible; upgrades reset each run.

- Equip an owned ship in Customize before entering Survival. Your chosen ship, shape and color skin are applied at the start of the run and preserved on retry. Locked ships use the existing shop ownership checks.
- Move with WASD or arrow keys. All ships fire automatically; level-up upgrades stack on their starting stats.

| Ship | Move speed (px/s) | Fire interval | Starting weapon |
| --- | ---: | ---: | --- |
| Standard | 220 | 0.18 s | 12 damage |
| Fast Move | 310 | 0.18 s | 12 damage |
| Fast Attack | 220 | 0.12 s | 12 damage |
| Two-Way Shot | 190 | 0.24 s | Two parallel 12-damage shots |
| Big Bullet | 220 | 0.24 s | Larger 20-damage shot with a wider hit area |

- Enemies spawn above the screen and descend. Tougher enemies and shooters join the run; health, speed and spawn frequency grow over time. The spawn interval starts at 0.85 s and shrinks by 0.5% every second, down to 0.12 s.
- Enemy kinds unlock over time: grunts and armored ships at the start, shooters after 20 s, rushers (fast, drift toward you) after 45 s, and splitters (break into two minis when destroyed) after 90 s. Every 60 s an elite arrives: a large, tough shooter with a three-way volley that always drops a repair kit and grants 60 XP. Difficulty tier rises every 30 s and adds to enemy health.
- Start with 100 HP. At the start enemy bullets cost 10 HP, contact costs 18 HP, and each enemy crossing the bottom green line costs 12 HP. Every enemy has a power of 1 + seconds survived / 240 when it spawns, and these damage values are multiplied by it (power 2 after 4 minutes: 20, 36 and 24 HP). Escaping enemies grant no XP.
- Kills immediately grant 6 XP (10 for armored, 8 rusher, 12 splitter, 60 elite), multiplied by the enemy's power, so stronger enemies grant proportionally more XP. The first level requires 100 XP; subsequent levels require 35 more each.
- Leveling pauses combat and offers three different random upgrades. Press 1/2/3 or use up/down and Enter to choose.
- There are 12 upgrades: damage, firing speed, multishot, maximum HP, movement speed, lifesteal, regeneration, immediate healing, critical hits (+10% chance of double damage, max 50%), plating (10% less damage taken, max 50%), scavenger (+20% XP, max +100%) and salvage rights (+3% special pickup drop chance, max +20%). Lifesteal heals a percentage of actual damage dealt, excluding overkill.
- Enemies have a 12% chance to drop a green repair pickup restoring 15 HP. Healing cannot exceed maximum HP.
- Special pickups (letter boxes) drop from kills (5% chance; elites always drop one): **S** shield (5 s invulnerable), **M** magnet (8 s, pulls pickups within 90 px toward the ship; pickups are otherwise only collected by touching them), **B** bomb (destroys normal enemies, deals 100 damage to elites), **X** double XP (15 s), **R** rapid fire (8 s, 40% faster fire). Active effects and their remaining seconds appear under the HUD.
- The HUD shows HP (the bar turns yellow below 50% and red below 25%), kills and survival time at the top, with the level and XP bar below.
- Score: each kill scores by enemy kind (grunt 10, mini 5, shooter 15, rusher 15, splitter 20, armored 25, elite 100), plus 5 points per full second survived. Escaping enemies score nothing.
- P or Escape pauses (also during a level-up choice). The pause menu offers **Resume**, **Retry**, **Game modes** and **Main menu**: choose with W/S or the arrows and Enter (or Space). P or Escape resumes.
- At zero HP the result screen shows the score, with the points from survival time and from kills, and the level reached. Choose **Retry** or **Main menu** with W/S or the arrows and Enter (or Space). R retries and Escape returns to mode selection.

Standalone checks (after compiling the game):

```powershell
javac --release 17 -encoding UTF-8 -cp bin -d bin tests/SurvivalTest.java
java -cp bin SurvivalTest
```
