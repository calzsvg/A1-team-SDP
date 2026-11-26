import engine.SurvivalGame;
import engine.SurvivalGame.Enemy;
import engine.SurvivalGame.Shot;
import engine.SurvivalGame.Upgrade;
import java.util.HashSet;
import java.util.Random;
import entity.Ship.ShipType;

/** Deterministic combat and progression checks, without a window. */
public final class SurvivalTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static SurvivalGame game() { return new SurvivalGame(448, 520, new Random(7)); }
    private static SurvivalGame offering(Upgrade target) {
        return offering(target, ShipType.STANDARD);
    }
    private static SurvivalGame offering(Upgrade target, ShipType type) {
        for (int seed = 0; seed < 1000; seed++) {
            SurvivalGame game = new SurvivalGame(448, 520, new Random(seed), type);
            game.addExperience(game.xpRequired());
            if (game.choices().contains(target)) return game;
        }
        throw new AssertionError("Upgrade never offered: " + target);
    }
    private static void select(SurvivalGame game, Upgrade target) {
        game.choose(game.choices().indexOf(target));
    }
    public static void main(String[] args) {
        for (ShipType type : ShipType.values()) {
            SurvivalGame ship = new SurvivalGame(448, 520, new Random(7), type);
            check(ship.shipType == type && ship.hp == 100, "Ship selection preserves Survival HP rules");
            check(ship.fireInterval < type.getShootingInterval() / 1000.0,
                    "Every ship fires faster than its Classic version");
            ship.update(.016, 0, 0);
            check(ship.shots.size() == type.getBulletsPerShot(), "Starting barrels reflect equipped ship");
            if (type == ShipType.FAST_MOVE) check(ship.moveSpeed > 220, "Fast Move retains movement advantage");
            if (type == ShipType.FAST_ATTACK) check(ship.fireInterval < .18, "Fast Attack retains fire-rate advantage");
            if (type == ShipType.TWO_WAY) {
                check(ship.shots.get(0).x < ship.x && ship.shots.get(1).x > ship.x,
                        "Two-Way fires from separate barrels");
                check(ship.shots.get(0).vx == 0 && ship.shots.get(1).vx == 0,
                        "Two-Way barrels fire parallel shots");
            }
            if (type == ShipType.BIG_BULLET) {
                Shot cannon = ship.shots.get(0);
                check(cannon.width == 18 && cannon.height == 30 && cannon.damage > 12,
                        "Big Bullet produces larger, stronger projectiles");
                Enemy edge = new Enemy(cannon.x + 20, cannon.y - 10, 0, 100, 0);
                ship.enemies.add(edge); ship.update(.016, 0, 0);
                check(edge.hp == 80, "Big Bullet's larger visual size also increases hit area");
            }
            ship = offering(Upgrade.DAMAGE, type);
            double baseDamage = ship.damage;
            select(ship, Upgrade.DAMAGE);
            check(ship.damage == baseDamage + 5, "Level damage stacks on ship's starting weapon");
            ship = offering(Upgrade.MULTISHOT, type);
            int baseProjectiles = ship.projectiles;
            select(ship, Upgrade.MULTISHOT);
            check(ship.projectiles == baseProjectiles + 1, "Multishot stacks on ship's starting barrels");
            ship = offering(Upgrade.FIRE_RATE, type);
            double baseInterval = ship.fireInterval;
            select(ship, Upgrade.FIRE_RATE);
            check(ship.fireInterval < baseInterval, "Fire upgrades stack on ship's starting rate");
        }
        SurvivalGame game = game();
        game.update(.016, 0, 0);
        check(!game.shots.isEmpty() && game.shots.get(0).vy < 0, "Fires upward without shoot input");
        for (int i = 0; i < 15; i++) game.update(.016, 0, 0);
        check(game.shots.size() >= 2, "Rapid automatic fire repeats");
        game = game();
        for (int i = 0; i < 12; i++) game.update(.05, 0, 0);
        check(game.enemies.size() == 1 && game.enemies.get(0).y < 0, "Enemies enter only from above");
        Enemy descending = game.enemies.get(0);
        double beforeY = descending.y;
        game.update(.05, 0, 0);
        check(descending.y > beforeY, "Enemies move downward");
        game = game();
        game.enemies.add(new Enemy(40, 492, 0, 20, 30));
        game.update(.016, 0, 0);
        check(game.hp == 88 && game.kills == 0 && game.xp == 0, "Escapes cost HP without kill rewards");
        game.shots.add(new Shot(game.x, game.y, 0, 0, 10, true));
        game.update(.016, 0, 0);
        check(game.hp == 78, "Enemy projectiles damage HP");
        game.enemies.add(new Enemy(game.x, game.y, 0, 100, 0));
        game.update(.016, 0, 0);
        check(game.hp == 60, "Enemy collision damages HP");
        game = game();
        game.enemies.add(new Enemy(60, 200, 0, 5, 0));
        game.shots.add(new Shot(60, 200, 0, 0, 12, false));
        game.update(.016, 0, 0);
        check(game.kills == 1 && game.xp == 6 && game.enemies.isEmpty(), "Kills award XP once");
        game.addExperience(game.xpRequired() - 6);
        check(game.level == 2 && game.xp == 0 && game.choices().size() == 3,
                "XP threshold opens three upgrade choices");
        check(new HashSet<>(game.choices()).size() == 3, "Upgrade offers have no duplicates");
        double clock = game.elapsed, x = game.x;
        game.update(.05, 1, 1);
        check(game.elapsed == clock && game.x == x, "Upgrade selection freezes combat and timers");
        game.choose(-1);
        check(game.choices().size() == 3, "Invalid selection cannot consume a level");
        game.choose(0);
        check(game.choices().isEmpty(), "Choice resumes combat");
        game = game(); game.addExperience(SurvivalGame.XP_BASE + (SurvivalGame.XP_BASE + SurvivalGame.XP_PER_LEVEL)
                + (SurvivalGame.XP_BASE + 2 * SurvivalGame.XP_PER_LEVEL) + 4);
        game.choose(0); game.choose(0);
        check(game.level == 4 && game.xp == 4 && game.choices().size() == 3,
                "Overflow XP preserves every pending level choice");
        game = offering(Upgrade.LIFESTEAL); select(game, Upgrade.LIFESTEAL);
        game.hp = 50;
        game.enemies.add(new Enemy(60, 200, 0, 3, 0));
        game.shots.add(new Shot(60, 200, 0, 0, 100, false));
        game.update(.016, 0, 0);
        check(Math.abs(game.hp - 50.12) < .0001, "Lifesteal uses actual damage, excluding overkill");
        game = offering(Upgrade.REGEN); select(game, Upgrade.REGEN); game.hp = 50;
        game.update(.05, 0, 0);
        check(game.hp > 50 && game.hp < 51, "Repair drones restore HP over active time");
        game = offering(Upgrade.MAX_HP); game.hp = 60; select(game, Upgrade.MAX_HP);
        check(game.maxHp == 125 && game.hp == 85, "Hull upgrade increases HP capacity and heals");
        game = offering(Upgrade.HEAL); game.hp = 90; select(game, Upgrade.HEAL);
        check(game.hp == 100, "Healing is capped at maximum HP");
        game = new SurvivalGame(448, 520, new Random(1) {
            @Override public double nextDouble() { return 0; }
        });
        game.hp = 50;
        game.enemies.add(new Enemy(game.x, game.y - 45, 0, 1, 0));
        game.shots.add(new Shot(game.x, game.y - 45, 0, 0, 12, false));
        game.update(.016, 0, 0);
        check(game.repairs.size() == 1, "A successful repair drop appears on a kill");
        game.magnet = 8;
        for (int i = 0; i < 20; i++) game.update(.016, 0, 0);
        check(game.hp == 65 && game.repairs.isEmpty(), "With a magnet, nearby repair pickups are pulled in and restore 15 HP");
        game = offering(Upgrade.MULTISHOT); select(game, Upgrade.MULTISHOT);
        game.update(.016, 0, 0);
        check(game.projectiles == 2 && game.shots.size() == 2, "Multishot fires additional projectiles");
        game = offering(Upgrade.FIRE_RATE); select(game, Upgrade.FIRE_RATE);
        check(game.fireInterval < .18, "Fire rate upgrade speeds up firing");
        game.projectiles = 5; game.fireInterval = .055; game.lifesteal = .24; game.moveSpeed = 380;
        for (int i = 0; i < 30; i++) {
            game.addExperience(game.xpRequired());
            check(!game.choices().contains(Upgrade.MULTISHOT)
                    && !game.choices().contains(Upgrade.FIRE_RATE)
                    && !game.choices().contains(Upgrade.LIFESTEAL)
                    && !game.choices().contains(Upgrade.SPEED), "Capped upgrades are excluded");
            game.choose(0);
        }
        game = offering(Upgrade.DAMAGE); select(game, Upgrade.DAMAGE);
        check(game.damage == 17, "Damage upgrade increases damage");
        game = offering(Upgrade.SPEED); select(game, Upgrade.SPEED);
        check(game.moveSpeed > 220, "Thrusters increase movement speed");
        game = game(); double startX = game.x, startY = game.y;
        game.update(.05, 1, -1);
        check(Math.abs(Math.hypot(game.x - startX, game.y - startY) - 11) < .001,
                "Diagonal movement is normalized");
        for (int i = 0; i < 100; i++) game.update(.05, -1, -1);
        check(game.x >= 16 && game.y >= 95, "Movement stays inside playable bounds");
        game = game(); game.hp = 1;
        game.shots.add(new Shot(game.x, game.y, 0, 0, 10, true));
        game.update(.016, 0, 0);
        check(game.hp == 0 && game.isOver(), "Zero HP ends the run");
        clock = game.elapsed; game.update(.05, 1, 0); game.addExperience(100);
        check(game.elapsed == clock && game.level == 1, "Game over freezes combat and progression");
        // Enemy tiers and kinds.
        game = game(); game.elapsed = 2 * SurvivalGame.TIER_SECONDS + 5;
        check(game.tier() == 2, "Difficulty tier rises every 45 seconds");
        check(SurvivalGame.xpFor(SurvivalGame.GRUNT) == 6 && SurvivalGame.xpFor(SurvivalGame.ARMORED) == 10
                && SurvivalGame.xpFor(SurvivalGame.ELITE) == 60, "Tougher enemies are worth more XP");
        game = game(); game.hp = game.maxHp = 100000;
        boolean[] early = new boolean[8];
        for (int i = 0; i < 500; i++) {
            game.update(.05, 0, 0);
            for (Enemy e : game.enemies) early[e.kind] = true;
        }
        check(game.elapsed < 30 && early[0] && early[1] && !early[2] && !early[3] && !early[4] && !early[5],
                "Only basic enemies appear during the first 30 seconds");
        game = game(); game.hp = game.maxHp = 100000; game.elapsed = 130;
        boolean[] late = new boolean[8];
        for (int i = 0; i < 1000; i++) {
            game.update(.05, 0, 0);
            for (Enemy e : game.enemies) late[e.kind] = true;
        }
        check(late[SurvivalGame.RUSHER] && late[SurvivalGame.SPLITTER] && late[SurvivalGame.SHOOTER],
                "Rushers and splitters join the run after 120 seconds");
        game = game(); game.hp = game.maxHp = 100000; game.elapsed = SurvivalGame.ELITE_SECONDS - .03;
        game.update(.05, 0, 0);
        boolean elite = false;
        for (Enemy e : game.enemies) elite |= e.kind == SurvivalGame.ELITE;
        check(elite, "An elite arrives at 75 seconds");
        game = game();
        Enemy rusher = new Enemy(game.x - 100, 150, SurvivalGame.RUSHER, 1000, 0);
        game.enemies.add(rusher);
        game.update(.05, 0, 0);
        check(rusher.x > game.x - 100, "Rushers drift toward the player's column");
        game = game();
        game.enemies.add(new Enemy(60, 200, SurvivalGame.SPLITTER, 5, 0));
        game.shots.add(new Shot(60, 200, 0, 0, 12, false));
        game.update(.016, 0, 0);
        int minis = 0;
        for (Enemy e : game.enemies) if (e.kind == SurvivalGame.MINI) minis++;
        check(game.kills == 1 && minis == 2 && game.xp == 12, "Splitters break into two minis when destroyed");
        game = new SurvivalGame(448, 520, new Random(3) {
            @Override public double nextDouble() { return .99; }
        });
        game.enemies.add(new Enemy(60, 200, SurvivalGame.ELITE, 5, 0));
        game.shots.add(new Shot(60, 200, 0, 0, 12, false));
        game.update(.016, 0, 0);
        check(game.repairs.size() == 1 && game.xp == 60 && game.level == 1,
                "Elites always drop a repair kit and grant 60 XP");
        game = game(); game.hp = game.maxHp = 100000;
        game.enemies.add(new Enemy(224, 150, SurvivalGame.ELITE, 100000, 0));
        int volley = 0;
        for (int i = 0; i < 40 && volley == 0; i++) {
            game.update(.05, 0, 0);
            for (Shot shot : game.shots) if (shot.hostile) volley++;
        }
        check(volley == 3, "Elites fire a three-way volley");
        // Special pickups.
        game = game();
        game.pickups.add(new SurvivalGame.Pickup(game.x, game.y, SurvivalGame.ItemType.SHIELD));
        game.update(.016, 0, 0);
        check(game.pickups.isEmpty() && game.shield > 4.9, "Collecting a shield pickup starts the shield");
        game.shots.add(new Shot(game.x, game.y, 0, 0, 10, true));
        game.update(.016, 0, 0);
        check(game.hp == 100, "Shield blocks enemy projectiles");
        for (int i = 0; i < 120; i++) game.update(.05, 0, 0);
        check(game.shield == 0, "Shield runs out after its duration");
        game = game();
        game.pickups.add(new SurvivalGame.Pickup(game.x, game.y - 80, SurvivalGame.ItemType.RAPID_FIRE));
        game.magnet = 8;
        for (int i = 0; i < 40; i++) game.update(.05, 0, 0);
        check(game.pickups.isEmpty() && game.rapid > 0, "Magnet pulls nearby pickups in");
        game = game();
        SurvivalGame.Pickup idle = new SurvivalGame.Pickup(game.x + 60, game.y - 60, SurvivalGame.ItemType.SHIELD);
        game.pickups.add(idle);
        game.update(.05, 0, 0);
        check(idle.x == game.x + 60, "Pickups are not pulled in without a magnet");
        game = game();
        SurvivalGame.Pickup far = new SurvivalGame.Pickup(game.x + 150, game.y - 150, SurvivalGame.ItemType.SHIELD);
        game.pickups.add(far); game.magnet = 8;
        game.update(.05, 0, 0);
        check(far.x == game.x + 150, "Magnet only reaches pickups within its radius");
        game = game();
        game.enemies.add(new Enemy(60, 200, 0, 1000, 0));
        game.enemies.add(new Enemy(120, 200, 1, 1000, 0));
        Enemy bossLike = new Enemy(200, 200, SurvivalGame.ELITE, 1000, 0);
        game.enemies.add(bossLike);
        game.pickups.add(new SurvivalGame.Pickup(game.x, game.y, SurvivalGame.ItemType.BOMB));
        game.update(.016, 0, 0);
        game.update(.016, 0, 0);
        check(game.kills == 2 && game.enemies.size() == 1 && bossLike.hp == 900,
                "Bomb destroys normal enemies and only damages elites");
        game = game();
        game.pickups.add(new SurvivalGame.Pickup(game.x, game.y, SurvivalGame.ItemType.DOUBLE_XP));
        game.update(.016, 0, 0);
        game.enemies.add(new Enemy(60, 200, 0, 5, 0));
        game.shots.add(new Shot(60, 200, 0, 0, 12, false));
        game.update(.016, 0, 0);
        check(game.xp == 12, "Double XP doubles kill rewards");
        SurvivalGame normal = game(), fast = game();
        fast.pickups.add(new SurvivalGame.Pickup(fast.x, fast.y, SurvivalGame.ItemType.RAPID_FIRE));
        for (int i = 0; i < 17; i++) { normal.update(.05, 0, 0); fast.update(.05, 0, 0); }
        check(fast.shots.size() > normal.shots.size(), "Rapid fire pickup raises the fire rate");
        game = new SurvivalGame(448, 520, new Random(5) {
            @Override public double nextDouble() { return 0; }
        });
        game.enemies.add(new Enemy(60, 200, 0, 5, 0));
        game.shots.add(new Shot(60, 200, 0, 0, 12, false));
        game.update(.016, 0, 0);
        check(game.pickups.size() == 1, "Kills can drop a special pickup");
        game = new SurvivalGame(448, 520, new Random(5) {
            @Override public double nextDouble() { return .99; }
        });
        game.enemies.add(new Enemy(60, 200, SurvivalGame.ELITE, 5, 0));
        game.shots.add(new Shot(60, 200, 0, 0, 12, false));
        game.update(.016, 0, 0);
        check(game.pickups.size() == 1, "Elites always drop a special pickup");
        game = new SurvivalGame(448, 520, new Random(7));
        check(game.xpRequired() == 120, "First level needs 120 XP");
        game.addExperience(120);
        check(game.xpRequired() == 165 && game.level == 2, "Each level needs 45 more XP");
        // Score: kill points plus 5 per full second survived.
        game = game();
        check(game.score() == 0, "A new run starts with no score");
        game.enemies.add(new Enemy(60, 200, SurvivalGame.GRUNT, 5, 0));
        game.enemies.add(new Enemy(160, 200, SurvivalGame.ARMORED, 5, 0));
        game.shots.add(new Shot(60, 200, 0, 0, 12, false));
        game.shots.add(new Shot(160, 200, 0, 0, 12, false));
        game.update(.016, 0, 0);
        check(game.kills == 2 && game.killScore == 35, "Grunts score 10 and armored enemies 25");
        game.elapsed = 61.9;
        check(game.timeScore() == 305 && game.score() == 340, "Each full second survived scores 5");
        check(SurvivalGame.pointsFor(SurvivalGame.ELITE) == 100 && SurvivalGame.pointsFor(SurvivalGame.MINI) == 5
                && SurvivalGame.pointsFor(SurvivalGame.SHOOTER) == 15 && SurvivalGame.pointsFor(SurvivalGame.RUSHER) == 15
                && SurvivalGame.pointsFor(SurvivalGame.SPLITTER) == 20, "Every enemy kind has a score value");
        game = game();
        game.enemies.add(new Enemy(60, game.height - 28, SurvivalGame.GRUNT, 1000, 0));
        game.update(.016, 0, 0);
        check(game.enemies.isEmpty() && game.killScore == 0, "Escaping enemies score nothing");
        game = game(); game.killScore = 50; game.elapsed = 10; game.hp = 0;
        int finalScore = game.score();
        game.update(.05, 0, 0);
        check(game.score() == finalScore, "The score is frozen once the run is over");
        // Difficulty scaling over time.
        check(SurvivalGame.spawnInterval(0) == .95, "Spawning starts at one enemy every 0.95 s");
        check(SurvivalGame.spawnInterval(120) < SurvivalGame.spawnInterval(60)
                && SurvivalGame.spawnInterval(60) < SurvivalGame.spawnInterval(0), "Enemies spawn faster as time passes");
        check(SurvivalGame.spawnInterval(10000) == SurvivalGame.MIN_SPAWN_INTERVAL, "Spawn interval has a floor");
        game = game();
        check(game.strength() == 1, "Enemy strength starts at 1");
        game.elapsed = 2 * SurvivalGame.STRENGTH_SECONDS;
        check(game.strength() == 3, "Enemy strength grows by 1 every 420 seconds");
        game = game(); game.hp = game.maxHp = 100000; game.elapsed = 2 * SurvivalGame.STRENGTH_SECONDS;
        double maxPower = 0;
        for (int i = 0; i < 100; i++) {
            game.update(.05, 0, 0);
            for (Enemy e : game.enemies) maxPower = Math.max(maxPower, e.power);
        }
        check(maxPower > 3, "Enemies spawned later carry a higher power");
        game = game();
        Enemy escape = new Enemy(60, game.height - 20, 0, 5, 0); escape.power = 2;
        game.enemies.add(escape);
        game.update(.016, 0, 0);
        check(game.hp == 76, "Escaping enemies hurt in proportion to their power");
        game = game();
        Enemy rammer = new Enemy(game.x, game.y, 0, 5, 0); rammer.power = 2;
        game.enemies.add(rammer);
        game.update(.016, 0, 0);
        check(game.hp == 64, "Contact damage scales with enemy power");
        game = game();
        Enemy gunner = new Enemy(60, 200, SurvivalGame.SHOOTER, 100000, 0); gunner.power = 2;
        game.enemies.add(gunner);
        double hostile = 0;
        for (int i = 0; i < 60 && hostile == 0; i++) {
            game.update(.05, 0, 0);
            for (Shot shot : game.shots) if (shot.hostile) hostile = shot.damage;
        }
        check(hostile == 20, "Enemy projectile damage scales with enemy power");
        game = game();
        Enemy strong = new Enemy(60, 200, 0, 5, 0); strong.power = 2;
        game.enemies.add(strong);
        game.shots.add(new Shot(60, 200, 0, 0, 12, false));
        game.update(.016, 0, 0);
        check(game.xp == 12, "Stronger enemies grant proportionally more XP");

        // More upgrade options.
        check(Upgrade.values().length == 12, "The upgrade pool has 12 options");
        game = offering(Upgrade.CRITICAL); select(game, Upgrade.CRITICAL);
        check(Math.abs(game.critChance - .1) < 1e-9, "Critical hits add 10% chance");
        game = offering(Upgrade.ARMOR); select(game, Upgrade.ARMOR);
        check(Math.abs(game.armor - .1) < 1e-9, "Plating adds 10% damage reduction");
        game = offering(Upgrade.SCAVENGER); select(game, Upgrade.SCAVENGER);
        check(Math.abs(game.xpBonus - .2) < 1e-9, "Scavenger adds 20% XP");
        game = offering(Upgrade.SALVAGE); select(game, Upgrade.SALVAGE);
        check(Math.abs(game.dropBonus - .03) < 1e-9, "Salvage rights add 3% drop chance");
        game = new SurvivalGame(448, 520, new Random(3) {
            @Override public double nextDouble() { return 0; }
        });
        Enemy target = new Enemy(60, 200, 0, 100, 0);
        game.enemies.add(target);
        game.shots.add(new Shot(60, 200, 0, 0, 12, false));
        game.update(.016, 0, 0);
        check(target.hp == 88, "Without critical hits a shot deals normal damage");
        game = new SurvivalGame(448, 520, new Random(3) {
            @Override public double nextDouble() { return 0; }
        });
        game.critChance = .1;
        target = new Enemy(60, 200, 0, 100, 0);
        game.enemies.add(target);
        game.shots.add(new Shot(60, 200, 0, 0, 12, false));
        game.update(.016, 0, 0);
        check(target.hp == 76, "A critical hit deals double damage");
        game = game(); game.armor = .5;
        Enemy bottom = new Enemy(60, game.height - 20, 0, 5, 0);
        game.enemies.add(bottom);
        game.update(.016, 0, 0);
        check(game.hp == 94, "Plating reduces damage taken");
        game = game(); game.xpBonus = .5;
        game.enemies.add(new Enemy(60, 200, 0, 5, 0));
        game.shots.add(new Shot(60, 200, 0, 0, 12, false));
        game.update(.016, 0, 0);
        check(game.xp == 9, "Scavenger raises XP from kills");
        for (int round = 0; round < 2; round++) {
            game = new SurvivalGame(448, 520, new Random(3) {
                @Override public double nextDouble() { return .06; }
            });
            game.dropBonus = round == 0 ? 0 : .03;
            game.enemies.add(new Enemy(60, 200, 0, 5, 0));
            game.shots.add(new Shot(60, 200, 0, 0, 12, false));
            game.update(.016, 0, 0);
            check(game.pickups.size() == round, round == 0 ? "No pickup at 6% roll without salvage"
                    : "Salvage rights turn a 6% roll into a pickup");
        }
        for (int seed = 0; seed < 100; seed++) {
            game = new SurvivalGame(448, 520, new Random(seed));
            game.critChance = .5; game.armor = .5; game.xpBonus = 1; game.dropBonus = .2;
            game.addExperience(game.xpRequired());
            check(!game.choices().contains(Upgrade.CRITICAL) && !game.choices().contains(Upgrade.ARMOR)
                    && !game.choices().contains(Upgrade.SCAVENGER) && !game.choices().contains(Upgrade.SALVAGE),
                    "Maxed upgrades are no longer offered");
        }

        // Exercise sustained play, changing upgrades, spawning, and list removal.
        game = game(); game.hp = game.maxHp = 100000;
        for (int i = 0; i < 12000; i++) {
            if (!game.choices().isEmpty()) game.choose(0);
            game.update(.016, Math.sin(i * .01), 0);
            check(game.shots.size() < 1000 && game.enemies.size() < 1000, "Entities stay bounded");
        }
        check(game.elapsed > 180 && game.kills > 0 && game.level > 1, "Sustained run progresses");

        // Boss enemies.
        game = game(); game.hp = game.maxHp = 100000;
        game.elapsed = SurvivalGame.BOSS_FIRST_SECONDS - .06;
        game.update(.05, 0, 0);
        check(!game.bossAlive() && game.bossesSpawned == 0, "No boss before the first boss time");
        game.update(.05, 0, 0);
        check(game.bossAlive() && game.bossesSpawned == 1 && game.bossWarning > 0, "A boss arrives at 2:00 with a warning");
        Enemy boss = null;
        for (Enemy e : game.enemies) if (e.kind == SurvivalGame.BOSS) boss = e;
        double firstHp = boss.maxHp;
        check(firstHp >= 600, "The boss is far tougher than an elite");
        for (int i = 0; i < 400; i++) game.update(.05, 0, 0);
        check(game.enemies.contains(boss) && boss.y == SurvivalGame.BOSS_HOLD_Y, "The boss stops at its hold line and never escapes");
        boolean bossShot = false;
        game.shots.clear();
        for (int i = 0; i < 60 && !bossShot; i++) {
            game.update(.05, 0, 0);
            int spread = 0;
            for (Shot shot : game.shots) if (shot.hostile && shot.y < 300) spread++;
            bossShot = spread >= 5;
        }
        check(bossShot, "The boss fires a five-way spread");
        double sweep = boss.x;
        for (int i = 0; i < 40; i++) game.update(.05, 0, 0);
        check(boss.x != sweep, "The boss sweeps side to side");
        game.hp = 100000; game.y = boss.y = SurvivalGame.BOSS_HOLD_Y;
        game.x = game.width / 2.0 + (game.width / 2.0 - 60) * Math.sin((game.elapsed + .016) * .5);
        game.update(.016, 0, 0);
        check(game.enemies.contains(boss) && game.hp < 100000, "Touching the boss hurts but does not remove it");
        game = game(); game.elapsed = SurvivalGame.BOSS_FIRST_SECONDS + SurvivalGame.BOSS_SECONDS - .01;
        game.hp = game.maxHp = 100000; game.update(.05, 0, 0); game.update(.05, 0, 0);
        check(game.bossesSpawned == 2, "Another boss arrives every two minutes");
        double secondHp = 0;
        for (Enemy e : game.enemies) if (e.kind == SurvivalGame.BOSS) secondHp = Math.max(secondHp, e.maxHp);
        check(secondHp > firstHp, "Later bosses have more health");
        game = game(); game.hp = game.maxHp = 100000;
        double bossX = game.width / 2.0 + (game.width / 2.0 - 60) * Math.sin(.016 * .5);
        Enemy bossTarget = new Enemy(bossX, 200, SurvivalGame.BOSS, 100, 0);
        game.enemies.add(bossTarget);
        game.pickups.clear();
        game.shots.add(new Shot(bossX, 200, 0, 0, 1000, false));
        int scoreBefore = game.killScore;
        game.update(.016, 0, 0);
        check(game.bossKills == 1 && game.kills == 1 && game.killScore - scoreBefore == 500,
                "Killing a boss counts it and is worth 500 points");
        check(game.pickups.size() == 2 && game.repairs.size() == 1, "A boss drops two pickups and a repair kit");
        check(game.xp + (game.level - 1) * SurvivalGame.XP_BASE >= 200 || game.level > 1, "A boss grants a large amount of XP");
        game = game(); game.hp = game.maxHp = 100000;
        Enemy tough = new Enemy(60, 200, SurvivalGame.BOSS, 500, 0);
        game.enemies.add(tough);
        game.pickups.add(new SurvivalGame.Pickup(game.x, game.y, SurvivalGame.ItemType.BOMB));
        game.update(.016, 0, 0);
        check(tough.hp == 500 - SurvivalGame.BOMB_ELITE_DAMAGE, "A bomb hurts a boss instead of destroying it");

        // Easier pacing for longer runs.
        check(SurvivalGame.spawnInterval(120) > .7, "Spawning stays relaxed through the first two minutes");
        game = game(); game.elapsed = 240;
        check(game.strength() < 1.7, "Enemies are still under 1.7x power at four minutes");
        check(SurvivalGame.XP_BASE == 120 && SurvivalGame.XP_PER_LEVEL == 45, "Level-ups need more XP so upgrades come at a steady pace");

        System.out.println("SurvivalTest: " + checks + " checks passed");
    }
}
