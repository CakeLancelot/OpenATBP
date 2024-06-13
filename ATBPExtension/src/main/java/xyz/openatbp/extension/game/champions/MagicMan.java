package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.pathfinding.MovementManager;

public class MagicMan extends UserActor {
    private static final int PASSIVE_DURATION = 3000;
    private static final int PASSIVE_SPEED_DURATION = 3000;
    private static final double PASSIVE_SPEED_VALUE = 0.2d;
    private static final int Q_CAST_DELAY = 500;
    private static final int Q_SILENCE_DURATION = 2000;
    private static final int W_STEALTH_DURATION = 3000;
    private static final double E_DASH_SPEED = 10d;
    private static final double E_ARMOR_VALUE = 0.3d;
    private static final int E_ARMOR_DURATION = 3000;
    private static final double E_SLOW_VALUE = 0.3d;
    private static final int E_SLOW_DURATION = 3000;
    private double estimatedQDuration = 0;
    private long qStartTime = 0;
    private long passiveIconStarted = 0;
    private boolean passiveActivated = false;
    private Point2D wLocation = null;
    private Point2D wDest = null;
    private boolean ultStarted;
    private boolean interruptE = false;
    private ArrayList<Actor> playersHitBySnake = new ArrayList<>(3);
    private int eDashTime;
    private int wUses = 0;
    private MagicManClone magicManClone;

    public MagicMan(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (System.currentTimeMillis() - passiveIconStarted >= PASSIVE_DURATION
                && passiveActivated) {
            this.passiveActivated = false;
            ExtensionCommands.removeStatusIcon(this.parentExt, this.player, "passive");
        }
        if (this.magicManClone != null) this.magicManClone.update(msRan);
        if (!this.getState(ActorState.INVISIBLE) && wLocation != null) {
            if (this.magicManClone != null) this.magicManClone.die(this);
            this.wLocation = null;
            this.wDest = null;
            this.canCast[1] = true;
            this.setState(ActorState.REVEALED, true);
            int wCooldown = ChampionData.getBaseAbilityCooldown(this, 2);
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt, this.player, "w", true, getReducedCooldown(wCooldown), 250);
            Runnable resetWUses = () -> this.wUses = 0;
            scheduleTask(resetWUses, getReducedCooldown(wCooldown));
        }
        if (this.ultStarted && this.cancelDashEndAttack()) {
            this.interruptE = true;
            this.ultStarted = false;
            ExtensionCommands.actorAnimate(parentExt, room, id, "idle", 100, false);
        }
        if (System.currentTimeMillis() - this.qStartTime > this.estimatedQDuration)
            this.playersHitBySnake.clear();
    }

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            this.applyStopMovingDuringAttack();
            if (this.getState(ActorState.INVISIBLE)) {
                this.setState(ActorState.INVISIBLE, false);
            }
            String projectile = "magicman_projectile";
            MagicManPassive passiveAttack = new MagicManPassive(a, handleAttack(a));
            RangedAttack rangedAttack = new RangedAttack(a, passiveAttack, projectile);
            scheduleTask(rangedAttack, BASIC_ATTACK_DELAY);
        }
    }

    @Override
    public void setState(ActorState state, boolean enabled) {
        if (state == ActorState.REVEALED && enabled) {
            if (this.wDest == null) super.setState(state, true);
        } else super.setState(state, enabled);
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (this.magicManClone != null) this.magicManClone.die(this);
    }

    @Override
    public void useAbility(
            int ability,
            JsonNode spellData,
            int cooldown,
            int gCooldown,
            int castDelay,
            Point2D dest) {
        switch (ability) {
            case 1:
                this.canCast[0] = false;
                if (this.getState(ActorState.INVISIBLE)) {
                    this.setState(ActorState.INVISIBLE, false);
                }
                this.stopMoving(castDelay);
                this.qStartTime = System.currentTimeMillis();
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "sfx_magicman_snakes", this.location);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "vo/vo_magicman_snakes", this.location);
                Point2D endPoint = Champion.getAbilityLine(this.location, dest, 9.5f).getP2();
                this.estimatedQDuration = ((this.location.distance(endPoint) / 7f)) * 1000f;
                double dx = endPoint.getX() - this.location.getX();
                double dy = endPoint.getY() - this.location.getY();
                double theta = Math.atan2(dy, dx);
                double dist = this.location.distance(endPoint);
                double theta2 = theta - (Math.PI / 8);
                double theta3 = theta + (Math.PI / 8);
                double x = this.location.getX();
                double y = this.location.getY();
                Point2D endPoint2 =
                        new Point2D.Double(
                                x + (dist * Math.cos(theta2)), y + (dist * Math.sin(theta2)));
                Point2D endPoint3 =
                        new Point2D.Double(
                                x + (dist * Math.cos(theta3)), y + (dist * Math.sin(theta3)));
                this.fireMMProjectile(
                        new SnakeProjectile(
                                this.parentExt,
                                this,
                                new Line2D.Float(this.location, endPoint),
                                7f,
                                0.25f,
                                "projectile_magicman_snake"),
                        this.location,
                        endPoint,
                        9.5f);
                this.fireMMProjectile(
                        new SnakeProjectile(
                                this.parentExt,
                                this,
                                new Line2D.Float(this.location, endPoint2),
                                7f,
                                0.25f,
                                "projectile_magicman_snake"),
                        this.location,
                        endPoint2,
                        9.5f);
                this.fireMMProjectile(
                        new SnakeProjectile(
                                this.parentExt,
                                this,
                                new Line2D.Float(this.location, endPoint3),
                                7f,
                                0.25f,
                                "projectile_magicman_snake"),
                        this.location,
                        endPoint3,
                        9.5f);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "q",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                int globalCD = gCooldown + 1000;
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, globalCD, dest), gCooldown);
                break;
            case 2:
                this.canCast[1] = false;
                this.wUses++;
                if (this.getState(ActorState.INVISIBLE)) {
                    this.setState(ActorState.INVISIBLE, false);
                }
                if (this.wUses == 1) {
                    Point2D dashPoint =
                            MovementManager.getDashPoint(
                                    this, new Line2D.Float(this.location, dest));
                    if (Double.isNaN(dashPoint.getY())) dashPoint = this.location;
                    this.addState(ActorState.INVISIBLE, 0d, W_STEALTH_DURATION);
                    this.wLocation = new Point2D.Double(this.location.getX(), this.location.getY());
                    Point2D endLocation =
                            Champion.getAbilityLine(this.wLocation, dest, 100f).getP2();
                    if (this.location.distance(endLocation) <= 1d) endLocation = this.location;
                    this.wDest = endLocation;
                    ExtensionCommands.snapActor(
                            this.parentExt, this.room, this.id, this.location, dashPoint, true);
                    this.setLocation(dashPoint);
                    this.magicManClone = new MagicManClone(this.wLocation);
                    RoomHandler handler = parentExt.getRoomHandler(room.getName());
                    handler.addCompanion(this.magicManClone);
                    ExtensionCommands.actorAbilityResponse(
                            this.parentExt, this.player, "w", true, 1000, 0);
                } else {
                    this.setState(ActorState.INVISIBLE, false);
                    ExtensionCommands.actorAbilityResponse(
                            this.parentExt,
                            this.player,
                            "w",
                            true,
                            getReducedCooldown(cooldown),
                            gCooldown);
                }
                int abilityDelay = this.wUses == 1 ? 1000 : getReducedCooldown(cooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest),
                        abilityDelay);
                break;
            case 3:
                this.canCast[2] = false;
                this.canMove = false;
                this.ultStarted = true;
                Point2D firstLocation =
                        new Point2D.Double(this.location.getX(), this.location.getY());
                Point2D dashPoint = this.dash(dest, true, E_DASH_SPEED);
                double dashTime = dashPoint.distance(firstLocation) / E_DASH_SPEED;
                this.eDashTime = (int) (dashTime * 1000d);
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "sfx_magicman_explode_roll",
                        this.location);
                ExtensionCommands.actorAnimate(
                        this.parentExt, this.room, this.id, "spell3", eDashTime, true);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "e",
                        true,
                        getReducedCooldown(cooldown),
                        (int) (eDashTime * 0.8) + gCooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dashPoint),
                        eDashTime);
                break;
        }
    }

    private void handleCloneDeath() {
        this.parentExt.getRoomHandler(this.room.getName()).removeCompanion(this.magicManClone);
        this.magicManClone = null;
    }

    private MagicManAbilityRunnable abilityRunnable(
            int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
        return new MagicManAbilityRunnable(ability, spellData, cooldown, gCooldown, dest);
    }

    private class MagicManAbilityRunnable extends AbilityRunnable {

        public MagicManAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            Runnable enableQCasting = () -> canCast[0] = true;
            int delay = getReducedCooldown(cooldown) - Q_CAST_DELAY;
            scheduleTask(enableQCasting, delay);
            attackCooldown = 0;
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
            if (wUses == 2) wUses = 0;
        }

        @Override
        protected void spellE() {
            Runnable enableECasting = () -> canCast[2] = true;
            int delay = getReducedCooldown(cooldown) - eDashTime;
            scheduleTask(enableECasting, delay);
            canMove = true;
            ultStarted = false;
            if (!interruptE && getHealth() > 0) {
                ExtensionCommands.actorAnimate(parentExt, room, id, "spell3b", 500, false);
                ExtensionCommands.playSound(parentExt, room, id, "sfx_magicman_explode", location);
                ExtensionCommands.playSound(
                        parentExt, room, id, "vo/vo_magicman_explosion", location);
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "magicman_explosion",
                        id + "_ultExplosion",
                        1500,
                        (float) location.getX(),
                        (float) location.getY(),
                        false,
                        team,
                        0f);
                double damageModifier = getPlayerStat("spellDamage") * 0.001d;
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                for (Actor a : Champion.getActorsInRadius(handler, location, 4f)) {
                    if (isNonStructure(a)) {
                        double damage = (double) (a.getHealth()) * (0.35d + damageModifier);
                        a.addToDamageQueue(MagicMan.this, damage, spellData, false);
                        a.addEffect("armor", a.getStat("armor") * -E_ARMOR_VALUE, E_ARMOR_DURATION);
                        a.addState(ActorState.SLOWED, E_SLOW_VALUE, E_SLOW_DURATION);
                    }
                }
            } else if (interruptE) {
                ExtensionCommands.playSound(parentExt, room, id, "sfx_skill_interrupted", location);
            }
            interruptE = false;
            ultStarted = false;
        }

        @Override
        protected void spellPassive() {}
    }

    private class SnakeProjectile extends Projectile {

        public SnakeProjectile(
                ATBPExtension parentExt,
                UserActor owner,
                Line2D path,
                float speed,
                float hitboxRadius,
                String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
        }

        @Override
        protected void hit(Actor victim) {
            victim.addState(ActorState.SILENCED, 0d, Q_SILENCE_DURATION);
            ExtensionCommands.createWorldFX(
                    this.parentExt,
                    this.owner.getRoom(),
                    this.id,
                    "magicman_snake_explosion",
                    this.id + "_explosion",
                    750,
                    (float) this.location.getX(),
                    (float) this.location.getY(),
                    false,
                    owner.getTeam(),
                    0f);
            if (!playersHitBySnake.contains(victim)) {
                playersHitBySnake.add(victim);
                JsonNode spellData = parentExt.getAttackData(MagicMan.this.avatar, "spell1");
                victim.addToDamageQueue(MagicMan.this, getSpellDamage(spellData), spellData, false);
            }
            this.destroy();
        }

        @Override
        public void destroy() {
            super.destroy();
        }
    }

    private class MagicManClone extends Actor {

        private boolean dead;
        private double timeTraveled = 0;

        MagicManClone(Point2D location) {
            this.room = MagicMan.this.room;
            this.parentExt = MagicMan.this.parentExt;
            this.currentHealth = 99999;
            this.maxHealth = 99999;
            this.location = location;
            this.avatar = MagicMan.this.avatar + "_decoy";
            this.id = MagicMan.this + "_decoy";
            this.team = MagicMan.this.team;
            this.actorType = ActorType.COMPANION;
            this.stats = initializeStats();
            this.movementLine = new Line2D.Float(this.location, MagicMan.this.wDest);
            ExtensionCommands.createActor(
                    this.parentExt, this.room, this.id, this.avatar, this.location, 0f, team);
            ExtensionCommands.moveActor(
                    this.parentExt,
                    this.room,
                    this.id,
                    this.movementLine.getP1(),
                    this.movementLine.getP2(),
                    (float) MagicMan.this.getPlayerStat("speed"),
                    true);
        }

        @Override
        public void handleKill(Actor a, JsonNode attackData) {}

        @Override
        public boolean damaged(Actor a, int damage, JsonNode attackData) {
            return false;
        }

        @Override
        public void attack(Actor a) {}

        @Override
        public void die(Actor a) {
            this.stopMoving();
            ExtensionCommands.knockOutActor(this.parentExt, this.room, this.id, a.getId(), 10000);
            this.dead = true;
            ExtensionCommands.createWorldFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "magicman_eat_it",
                    this.id + "_eatIt",
                    2000,
                    (float) this.location.getX(),
                    (float) this.location.getY(),
                    false,
                    this.team,
                    0f);
            ExtensionCommands.playSound(
                    this.parentExt, this.room, "", "sfx_magicman_decoy", this.location);
            ExtensionCommands.playSound(
                    this.parentExt, this.room, this.id, "vo/vo_magicman_decoy2", this.location);
            JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell2");
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            for (Actor actor : Champion.getActorsInRadius(handler, this.location, 2.5f)) {
                if (isNonStructure(actor)) {
                    actor.addToDamageQueue(
                            MagicMan.this, getSpellDamage(spellData), spellData, false);
                }
            }
            this.timeTraveled = 0;
            ExtensionCommands.destroyActor(this.parentExt, this.room, this.id);
            MagicMan.this.handleCloneDeath();
        }

        @Override
        public void update(int msRan) {
            this.handleDamageQueue();
            if (this.dead) return;
            this.timeTraveled += 0.1d;
            this.location =
                    MovementManager.getRelativePoint(
                            this.movementLine,
                            (float) MagicMan.this.getPlayerStat("speed"),
                            this.timeTraveled);
            this.handlePathing();
        }

        @Override
        public void setTarget(Actor a) {}
    }

    private class MagicManPassive implements Runnable {

        Actor target;
        boolean crit;

        MagicManPassive(Actor target, boolean crit) {
            this.target = target;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if (crit) damage *= 2;
            new Champion.DelayedAttack(
                            parentExt, MagicMan.this, target, (int) damage, "basicAttack")
                    .run();
            if (this.target.getActorType() == ActorType.PLAYER) {
                addEffect("speed", getStat("speed") * PASSIVE_SPEED_VALUE, PASSIVE_SPEED_DURATION);
                if (!passiveActivated)
                    ExtensionCommands.addStatusIcon(
                            parentExt,
                            player,
                            "passive",
                            "magicman_spell_4_short_description",
                            "icon_magicman_passive",
                            0f);
                passiveActivated = true;
                passiveIconStarted = System.currentTimeMillis();
            }
        }
    }
}
