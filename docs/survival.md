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

- Enemies spawn above the screen and descend. Tougher enemies and shooters join the run; health, speed and spawn frequency grow over time.
- Start with 100 HP. Enemy bullets cost 10 HP, contact costs 18 HP, and each enemy crossing the bottom green line costs 12 HP. Escaping enemies grant no XP.
- Kills immediately grant 6 XP (10 for armored enemies). The first level requires 20 XP; subsequent levels require 12 more each.
- Leveling pauses combat and offers three different random upgrades. Press 1/2/3 or use up/down and Enter to choose.
- Upgrades include damage, firing speed, multishot, maximum HP, movement speed, lifesteal, regeneration and immediate healing. Lifesteal heals a percentage of actual damage dealt, excluding overkill.
- Enemies have a 12% chance to drop a green repair pickup restoring 15 HP. Nearby pickups move toward the ship. Healing cannot exceed maximum HP.
- P pauses/resumes. Escape returns to mode selection. At zero HP, view run results and press R to retry.

Standalone checks (after compiling the game):

```powershell
javac --release 17 -encoding UTF-8 -cp bin -d bin tests/SurvivalTest.java
java -cp bin SurvivalTest
```
