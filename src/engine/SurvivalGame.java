package engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import entity.Ship.ShipType;

/** Run-local Survival simulation; clocks advance only during active combat. */
public final class SurvivalGame {
    public enum Upgrade {
        DAMAGE("Overcharge", "+5 bullet damage"),
        FIRE_RATE("Rapid fire", "18% faster auto fire"),
        MULTISHOT("Split cannon", "+1 projectile (max 5)"),
        MAX_HP("Reinforced hull", "+25 max HP, heal 25"),
        LIFESTEAL("Vampire rounds", "+4% damage converted to HP"),
        REGEN("Repair drones", "+0.8 HP per second"),
        SPEED("Thrusters", "+12% movement speed"),
        HEAL("Emergency repair", "Restore 40% max HP"),
        CRITICAL("Critical hits", "+10% chance to deal double damage"),
        ARMOR("Plating", "Take 10% less damage"),
        SCAVENGER("Scavenger", "+20% XP from kills"),
        SALVAGE("Salvage rights", "+3% special pickup drop chance");
        public final String title, description;
        Upgrade(String title, String description) {
            this.title = title; this.description = description;
        }
    }
    /** Enemy kinds. 0-2 are the original types; the rest unlock as the run goes on. */
    public static final int GRUNT = 0, ARMORED = 1, SHOOTER = 2, RUSHER = 3,
            SPLITTER = 4, ELITE = 5, MINI = 6;
    /** Seconds between difficulty tiers, and between elite arrivals. */
    public static final double TIER_SECONDS = 30, ELITE_SECONDS = 60;
    /** Enemy power is 1 at the start and grows by 1 every STRENGTH_SECONDS. */
    public static final double STRENGTH_SECONDS = 240;
    /** Spawn interval starts at 0.85 s and shrinks by 0.5% per second, never below MIN_SPAWN_INTERVAL. */
    public static final double START_SPAWN_INTERVAL = .85, MIN_SPAWN_INTERVAL = .12;
    public static double spawnInterval(double elapsed) {
        return Math.max(MIN_SPAWN_INTERVAL, START_SPAWN_INTERVAL * Math.pow(.995, elapsed));
    }
    /** Enemy strength multiplier at the current time. */
    public double strength() { return 1 + elapsed / STRENGTH_SECONDS; }
    public static final class Enemy {
        public double x, y, hp;
        public final double maxHp, speed;
        public final int kind;
        double fireClock = 1.5;
        /** Strength multiplier fixed when the enemy spawns; scales its damage and the XP it grants. */
        public double power = 1;
        public Enemy(double x, double y, int kind, double hp, double speed) {
            this.x = x; this.y = y; this.kind = kind;
            this.hp = hp; this.maxHp = hp; this.speed = speed;
        }
    }
    public static final class Shot {
        public double x, y;
        public final double vx, vy, damage;
        public final boolean hostile;
        public final int width, height;
        public Shot(double x, double y, double vx, double vy, double damage, boolean hostile) {
            this(x, y, vx, vy, damage, hostile, 6, 10);
        }
        public Shot(double x, double y, double vx, double vy, double damage,
                boolean hostile, int width, int height) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.damage = damage; this.hostile = hostile;
            this.width = width; this.height = height;
        }
    }
    /** Special pickups dropped by enemies; each gives a short-lived effect (bomb is instant). */
    public enum ItemType {
        SHIELD("Shield", "5 s invulnerability", 5),
        MAGNET("Magnet", "8 s pull nearby pickups", 8),
        BOMB("Bomb", "Destroy normal enemies, hurt elites", 0),
        DOUBLE_XP("Double XP", "15 s double XP", 15),
        RAPID_FIRE("Rapid fire", "8 s faster auto fire", 8);
        public final String title, description;
        public final double seconds;
        ItemType(String title, String description, double seconds) {
            this.title = title; this.description = description; this.seconds = seconds;
        }
    }
    public static final class Pickup {
        public double x, y;
        public final ItemType type;
        public Pickup(double x, double y, ItemType type) { this.x = x; this.y = y; this.type = type; }
    }
    /** Chance that a normal kill also drops a special pickup. */
    /** Pickups are pulled toward the ship only while a Magnet is active, within this radius and speed. */
    public static final double MAGNET_RADIUS = 90, MAGNET_SPEED = 190;
    public static final double ITEM_DROP_CHANCE = .05;
    public static final double BOMB_ELITE_DAMAGE = 100;
    public static final class Repair {
        public double x, y;
        Repair(double x, double y) { this.x = x; this.y = y; }
    }
    public final int width, height;
    public final ShipType shipType;
    public final List<Enemy> enemies = new ArrayList<>();
    public final List<Shot> shots = new ArrayList<>();
    public final List<Repair> repairs = new ArrayList<>();
    public final List<Pickup> pickups = new ArrayList<>();
    private final List<Upgrade> choices = new ArrayList<>();
    private final Random random;
    public double x, y, hp = 100, maxHp = 100, elapsed;
    public double damage = 12, fireInterval = .18, moveSpeed = 220;
    public double lifesteal, regeneration, hurtFlash;
    /** Upgrade-driven bonuses: critical hit chance, damage reduction, extra XP and extra pickup drop chance. */
    public double critChance, armor, xpBonus, dropBonus;
    /** Seconds left on each timed pickup effect. */
    public double shield, magnet, doubleXp, rapid;
    public int level = 1, xp, kills, projectiles = 1;
    /** Points earned from kills; see {@link #score()} for the run total. */
    public int killScore;
    private double spawnClock = .5, shotClock, contactGrace, nextElite = ELITE_SECONDS;
    public SurvivalGame(int width, int height, Random random) {
        this(width, height, random, ShipType.STANDARD);
    }
    /** Snapshot the equipped ship; Survival uses its own faster combat tuning. */
    public SurvivalGame(int width, int height, Random random, ShipType shipType) {
        this.width = width; this.height = height; this.random = random;
        this.shipType = shipType == null ? ShipType.STANDARD : shipType;
        switch (this.shipType) {
        case FAST_MOVE: moveSpeed = 310; break;
        case FAST_ATTACK: fireInterval = .12; break;
        case TWO_WAY: projectiles = 2; fireInterval = .24; moveSpeed = 190; break;
        case BIG_BULLET: damage = 20; fireInterval = .24; break;
        default: break;
        }
        x = width / 2.0; y = height - 75;
    }
    /** XP needed for the next level: the first level costs XP_BASE, each later one XP_PER_LEVEL more. */
    public static final int XP_BASE = 100, XP_PER_LEVEL = 35;
    public int xpRequired() { return XP_BASE + (level - 1) * XP_PER_LEVEL; }
    public boolean isOver() { return hp <= 0; }
    /** Difficulty tier of the run: rises by one every {@link #TIER_SECONDS}. */
    public int tier() { return (int) (elapsed / TIER_SECONDS); }
    /** XP awarded for destroying the given enemy kind. */
    public static int xpFor(int kind) {
        switch (kind) {
        case ARMORED: return 10;
        case RUSHER: return 8;
        case SPLITTER: return 12;
        case ELITE: return 60;
        case MINI: return 2;
        default: return 6;
        }
    }
    /** Score awarded for every full second survived. */
    public static final int POINTS_PER_SECOND = 5;
    /** Score awarded for destroying the given enemy kind. Escaping enemies score nothing. */
    public static int pointsFor(int kind) {
        switch (kind) {
        case ARMORED: return 25;
        case SHOOTER: return 15;
        case RUSHER: return 15;
        case SPLITTER: return 20;
        case ELITE: return 100;
        case MINI: return 5;
        default: return 10;
        }
    }
    /** Points earned from survival time: {@link #POINTS_PER_SECOND} per full second. */
    public int timeScore() { return (int) elapsed * POINTS_PER_SECOND; }
    /** Run score: kill points plus survival time points. */
    public int score() { return killScore + timeScore(); }
    /** Which kinds can appear: the pool widens at 20 s, 45 s and 90 s. */
    private int pickKind() {
        if (elapsed < 20) return random.nextInt(2);
        if (elapsed < 45) return random.nextInt(3);
        if (elapsed < 90) return random.nextInt(4);
        return random.nextInt(5);
    }
    private Enemy newEnemy(int kind, double x, double y) {
        int tier = tier();
        double hp, speed;
        switch (kind) {
        case ARMORED: hp = 30 + tier * 8; speed = 32 + Math.min(65, elapsed * .22); break;
        case RUSHER: hp = 12 + tier * 5; speed = 95 + Math.min(60, elapsed * .2); break;
        case SPLITTER: hp = 36 + tier * 8; speed = 40 + Math.min(40, elapsed * .15); break;
        case ELITE: hp = 220 + tier * 60; speed = 26; break;
        case MINI: hp = 8 + tier * 2; speed = 80; break;
        default: hp = 18 + tier * 8; speed = 48 + Math.min(65, elapsed * .22); break;
        }
        Enemy enemy = new Enemy(x, y, kind, hp, speed);
        enemy.power = strength();
        return enemy;
    }
    public List<Upgrade> choices() { return Collections.unmodifiableList(choices); }
    public void update(double dt, double dx, double dy) {
        if (isOver() || !choices.isEmpty() || dt <= 0) return;
        dt = Math.min(.05, dt);
        elapsed += dt;
        hurtFlash = Math.max(0, hurtFlash - dt);
        shield = Math.max(0, shield - dt);
        magnet = Math.max(0, magnet - dt);
        doubleXp = Math.max(0, doubleXp - dt);
        rapid = Math.max(0, rapid - dt);
        contactGrace = Math.max(0, contactGrace - dt);
        double length = Math.max(1, Math.hypot(dx, dy));
        x = Math.max(16, Math.min(width - 16, x + dx / length * moveSpeed * dt));
        y = Math.max(95, Math.min(height - 45, y + dy / length * moveSpeed * dt));
        heal(regeneration * dt);
        shotClock -= dt;
        if (shotClock <= 0) {
            shotClock += rapid > 0 ? fireInterval * .6 : fireInterval;
            for (int i = 0; i < projectiles; i++) {
                double offset = i - (projectiles - 1) / 2.0;
                boolean big = shipType == ShipType.BIG_BULLET;
                // Two-way cannons retain their separated, parallel barrels.
                double spacing = shipType == ShipType.TWO_WAY || big ? 16 : 9;
                double drift = shipType == ShipType.TWO_WAY ? 0 : offset * 24;
                shots.add(new Shot(x + offset * spacing, y - (big ? 25 : 17),
                        drift, -520, damage, false, big ? 18 : 6, big ? 30 : 10));
            }
        }
        spawnClock -= dt;
        if (spawnClock <= 0) {
            spawnClock += spawnInterval(elapsed);
            int kind = pickKind();
            enemies.add(newEnemy(kind, 20 + random.nextDouble() * (width - 40), -20));
        }
        if (elapsed >= nextElite) {
            nextElite += ELITE_SECONDS;
            enemies.add(newEnemy(ELITE, 40 + random.nextDouble() * (width - 80), -30));
        }
        for (Enemy enemy : enemies) {
            enemy.y += enemy.speed * dt;
            if (enemy.kind == RUSHER) {
                // Rushers slide toward the player's column while they fall.
                double gap = x - enemy.x;
                enemy.x += Math.max(-1, Math.min(1, gap / 20)) * 70 * dt;
            }
            boolean shoots = enemy.kind == SHOOTER || enemy.kind == ELITE;
            if (shoots && enemy.y > 80 && enemy.y < height - 100) {
                enemy.fireClock -= dt;
                if (enemy.fireClock <= 0) {
                    boolean elite = enemy.kind == ELITE;
                    enemy.fireClock = elite ? 1.6 : 2.4;
                    shots.add(new Shot(enemy.x, enemy.y + 12, 0, 150, 10 * enemy.power, true));
                    if (elite) {
                        shots.add(new Shot(enemy.x, enemy.y + 12, -60, 150, 10 * enemy.power, true));
                        shots.add(new Shot(enemy.x, enemy.y + 12, 60, 150, 10 * enemy.power, true));
                    }
                }
            }
        }
        for (Iterator<Shot> it = shots.iterator(); it.hasNext();) {
            Shot shot = it.next();
            shot.x += shot.vx * dt; shot.y += shot.vy * dt;
            boolean removed = shot.y < -30 || shot.y > height + 20 || shot.x < 0 || shot.x > width;
            if (!removed && shot.hostile && Math.abs(shot.x - x) < 14 && Math.abs(shot.y - y) < 16) {
                hurt(shot.damage); removed = true;
            } else if (!removed && !shot.hostile) {
                for (Enemy enemy : enemies) {
                    if (enemy.hp > 0 && Math.abs(shot.x - enemy.x) < 14 + shot.width / 2.0
                            && Math.abs(shot.y - enemy.y) < 12 + shot.height / 2.0) {
                        double hit = shot.damage * (critChance > 0 && random.nextDouble() < critChance ? 2 : 1);
                        double dealt = Math.min(enemy.hp, hit);
                        enemy.hp -= hit; heal(dealt * lifesteal); removed = true;
                        break;
                    }
                }
            }
            if (removed) it.remove();
        }
        int earned = 0;
        List<Enemy> spawned = new ArrayList<>();
        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext();) {
            Enemy enemy = it.next();
            if (enemy.hp <= 0) {
                kills++; earned += (int) Math.max(1, Math.round(xpFor(enemy.kind) * enemy.power)); killScore += pointsFor(enemy.kind);
                // Elites always drop a repair kit; everything else has a 12% chance.
                if (enemy.kind == ELITE || random.nextDouble() < .12)
                    repairs.add(new Repair(enemy.x, enemy.y));
                // Elites always drop a special pickup too; others rarely.
                if (enemy.kind == ELITE || random.nextDouble() < ITEM_DROP_CHANCE + dropBonus) {
                    ItemType[] types = ItemType.values();
                    pickups.add(new Pickup(enemy.x, enemy.y, types[random.nextInt(types.length)]));
                }
                if (enemy.kind == SPLITTER) {
                    spawned.add(newEnemy(MINI, enemy.x - 12, enemy.y));
                    spawned.add(newEnemy(MINI, enemy.x + 12, enemy.y));
                }
                it.remove();
            } else if (enemy.y >= height - 28) {
                hurt(12 * enemy.power); it.remove();
            } else if (Math.abs(enemy.x - x) < 25 && Math.abs(enemy.y - y) < 26) {
                if (contactGrace <= 0) { hurt(18 * enemy.power); contactGrace = .6; }
                it.remove();
            }
        }
        enemies.addAll(spawned);
        for (Iterator<Repair> it = repairs.iterator(); it.hasNext();) {
            Repair repair = it.next(); repair.y += 45 * dt;
            double distance = Math.hypot(x - repair.x, y - repair.y);
            if (magnet > 0 && distance < MAGNET_RADIUS && distance > 1) {
                repair.x += (x - repair.x) / distance * MAGNET_SPEED * dt;
                repair.y += (y - repair.y) / distance * MAGNET_SPEED * dt;
            }
            if (distance < 20) { heal(15); it.remove(); }
            else if (repair.y > height) it.remove();
        }
        for (Iterator<Pickup> it = pickups.iterator(); it.hasNext();) {
            Pickup pickup = it.next(); pickup.y += 45 * dt;
            double distance = Math.hypot(x - pickup.x, y - pickup.y);
            if (magnet > 0 && distance < MAGNET_RADIUS && distance > 1) {
                pickup.x += (x - pickup.x) / distance * MAGNET_SPEED * dt;
                pickup.y += (y - pickup.y) / distance * MAGNET_SPEED * dt;
            }
            if (distance < 20) { apply(pickup.type); it.remove(); }
            else if (pickup.y > height) it.remove();
        }
        if (!isOver()) addExperience((int) Math.round(earned * (doubleXp > 0 ? 2 : 1) * (1 + xpBonus)));
    }
    /** Activates a pickup's effect. Timed effects refresh to their full duration. */
    private void apply(ItemType type) {
        switch (type) {
        case SHIELD: shield = type.seconds; break;
        case MAGNET: magnet = type.seconds; break;
        case DOUBLE_XP: doubleXp = type.seconds; break;
        case RAPID_FIRE: rapid = type.seconds; break;
        case BOMB:
            for (Enemy enemy : enemies) {
                if (enemy.kind == ELITE) enemy.hp -= BOMB_ELITE_DAMAGE;
                else enemy.hp = 0;
            }
            break;
        default: break;
        }
    }
    private void hurt(double amount) {
        if (shield > 0) return;
        hp = Math.max(0, hp - amount * (1 - armor)); hurtFlash = .18;
    }
    private void heal(double amount) { if (!isOver()) hp = Math.min(maxHp, hp + amount); }
    public void addExperience(int amount) {
        if (amount < 0 || isOver()) return;
        xp += amount; offerLevel();
    }
    private void offerLevel() {
        if (!choices.isEmpty() || xp < xpRequired()) return;
        xp -= xpRequired(); level++;
        List<Upgrade> pool = new ArrayList<>();
        for (Upgrade upgrade : Upgrade.values()) {
            if (upgrade == Upgrade.MULTISHOT && projectiles >= 5) continue;
            if (upgrade == Upgrade.FIRE_RATE && fireInterval <= .055) continue;
            if (upgrade == Upgrade.LIFESTEAL && lifesteal >= .24) continue;
            if (upgrade == Upgrade.SPEED && moveSpeed >= 380) continue;
            if (upgrade == Upgrade.CRITICAL && critChance >= .5) continue;
            if (upgrade == Upgrade.ARMOR && armor >= .5) continue;
            if (upgrade == Upgrade.SCAVENGER && xpBonus >= 1) continue;
            if (upgrade == Upgrade.SALVAGE && dropBonus >= .2) continue;
            pool.add(upgrade);
        }
        Collections.shuffle(pool, random); choices.addAll(pool.subList(0, 3));
    }
    public void choose(int index) {
        if (index < 0 || index >= choices.size() || isOver()) return;
        switch (choices.get(index)) {
        case DAMAGE: damage += 5; break;
        case FIRE_RATE: fireInterval = Math.max(.055, fireInterval * .82); break;
        case MULTISHOT: projectiles = Math.min(5, projectiles + 1); break;
        case MAX_HP: maxHp += 25; heal(25); break;
        case LIFESTEAL: lifesteal = Math.min(.24, lifesteal + .04); break;
        case REGEN: regeneration += .8; break;
        case SPEED: moveSpeed = Math.min(380, moveSpeed * 1.12); break;
        case HEAL: heal(maxHp * .4); break;
        case CRITICAL: critChance = Math.min(.5, critChance + .1); break;
        case ARMOR: armor = Math.min(.5, armor + .1); break;
        case SCAVENGER: xpBonus = Math.min(1, xpBonus + .2); break;
        case SALVAGE: dropBonus = Math.min(.2, dropBonus + .03); break;
        default: break;
        }
        choices.clear(); offerLevel();
    }
}
