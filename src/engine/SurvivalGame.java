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
        HEAL("Emergency repair", "Restore 40% max HP");
        public final String title, description;
        Upgrade(String title, String description) {
            this.title = title; this.description = description;
        }
    }
    public static final class Enemy {
        public double x, y, hp;
        public final double maxHp, speed;
        public final int kind;
        double fireClock = 1.5;
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
    public static final class Repair {
        public double x, y;
        Repair(double x, double y) { this.x = x; this.y = y; }
    }
    public final int width, height;
    public final ShipType shipType;
    public final List<Enemy> enemies = new ArrayList<>();
    public final List<Shot> shots = new ArrayList<>();
    public final List<Repair> repairs = new ArrayList<>();
    private final List<Upgrade> choices = new ArrayList<>();
    private final Random random;
    public double x, y, hp = 100, maxHp = 100, elapsed;
    public double damage = 12, fireInterval = .18, moveSpeed = 220;
    public double lifesteal, regeneration, hurtFlash;
    public int level = 1, xp, kills, projectiles = 1;
    private double spawnClock = .5, shotClock, contactGrace;
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
    public int xpRequired() { return 20 + (level - 1) * 12; }
    public boolean isOver() { return hp <= 0; }
    public List<Upgrade> choices() { return Collections.unmodifiableList(choices); }
    public void update(double dt, double dx, double dy) {
        if (isOver() || !choices.isEmpty() || dt <= 0) return;
        dt = Math.min(.05, dt);
        elapsed += dt;
        hurtFlash = Math.max(0, hurtFlash - dt);
        contactGrace = Math.max(0, contactGrace - dt);
        double length = Math.max(1, Math.hypot(dx, dy));
        x = Math.max(16, Math.min(width - 16, x + dx / length * moveSpeed * dt));
        y = Math.max(95, Math.min(height - 45, y + dy / length * moveSpeed * dt));
        heal(regeneration * dt);
        shotClock -= dt;
        if (shotClock <= 0) {
            shotClock += fireInterval;
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
            spawnClock += Math.max(.18, .85 - elapsed * .003);
            int kind = random.nextInt(elapsed < 20 ? 2 : 3);
            double health = (kind == 1 ? 30 : 18) + Math.floor(elapsed / 30) * 8;
            enemies.add(new Enemy(20 + random.nextDouble() * (width - 40), -20,
                    kind, health, (kind == 1 ? 32 : 48) + Math.min(65, elapsed * .22)));
        }
        for (Enemy enemy : enemies) {
            enemy.y += enemy.speed * dt;
            if (enemy.kind == 2 && enemy.y > 80 && enemy.y < height - 100) {
                enemy.fireClock -= dt;
                if (enemy.fireClock <= 0) {
                    enemy.fireClock = 2.4;
                    shots.add(new Shot(enemy.x, enemy.y + 12, 0, 150, 10, true));
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
                        double dealt = Math.min(enemy.hp, shot.damage);
                        enemy.hp -= shot.damage; heal(dealt * lifesteal); removed = true;
                        break;
                    }
                }
            }
            if (removed) it.remove();
        }
        int earned = 0;
        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext();) {
            Enemy enemy = it.next();
            if (enemy.hp <= 0) {
                kills++; earned += enemy.kind == 1 ? 10 : 6;
                if (random.nextDouble() < .12) repairs.add(new Repair(enemy.x, enemy.y));
                it.remove();
            } else if (enemy.y >= height - 28) {
                hurt(12); it.remove();
            } else if (Math.abs(enemy.x - x) < 25 && Math.abs(enemy.y - y) < 26) {
                if (contactGrace <= 0) { hurt(18); contactGrace = .6; }
                it.remove();
            }
        }
        for (Iterator<Repair> it = repairs.iterator(); it.hasNext();) {
            Repair repair = it.next(); repair.y += 45 * dt;
            double distance = Math.hypot(x - repair.x, y - repair.y);
            if (distance < 90 && distance > 1) {
                repair.x += (x - repair.x) / distance * 190 * dt;
                repair.y += (y - repair.y) / distance * 190 * dt;
            }
            if (distance < 20) { heal(15); it.remove(); }
            else if (repair.y > height) it.remove();
        }
        if (!isOver()) addExperience(earned);
    }
    private void hurt(double amount) { hp = Math.max(0, hp - amount); hurtFlash = .18; }
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
        default: break;
        }
        choices.clear(); offerLevel();
    }
}
