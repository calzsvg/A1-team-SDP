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
            game.addExperience(20);
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
        game.addExperience(14);
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
        game = game(); game.addExperience(100);
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
        for (int i = 0; i < 20; i++) game.update(.016, 0, 0);
        check(game.hp == 65 && game.repairs.isEmpty(), "Nearby repair pickups attract and restore 15 HP");
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
        // Exercise sustained play, changing upgrades, spawning, and list removal.
        game = game(); game.hp = game.maxHp = 100000;
        for (int i = 0; i < 12000; i++) {
            if (!game.choices().isEmpty()) game.choose(0);
            game.update(.016, Math.sin(i * .01), 0);
            check(game.shots.size() < 1000 && game.enemies.size() < 1000, "Entities stay bounded");
        }
        check(game.elapsed > 180 && game.kills > 0 && game.level > 1, "Sustained run progresses");
        System.out.println("SurvivalTest: " + checks + " checks passed");
    }
}
