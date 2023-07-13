package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Gunter extends UserActor{

    private boolean ultActivated = false;
    private Point2D ultPoint;
    public Gunter(User u, ATBPExtension parentExt){
        super(u,parentExt);
    }

    @Override
    public void update(int msRan){ //Add something so armor/mr isn't weird
        super.update(msRan);
        if(ultActivated && ultPoint != null){
            JsonNode spellData = parentExt.getAttackData(getAvatar(),"spell3");
            Line2D ogLine = new Line2D.Float(this.getRelativePoint(false),this.ultPoint);
            List<Actor> affectedActors = Champion.getActorsAlongLine(parentExt.getRoomHandler(room.getId()),Champion.getDistanceLine(ogLine,7f),4f);
            for(Actor a : affectedActors){
                if(a.getTeam() != this.team && a.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE){
                    double damage = (double)getSpellDamage(spellData)/10;
                    handleSpellVamp(damage);
                    a.addToDamageQueue(this,Math.round(damage),spellData);
                }
            }
        }
    }
    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest){
        super.useAbility(ability,spellData,cooldown,gCooldown,castDelay,dest);

        switch(ability){
            case 1:
                Point2D dashLocation = Champion.getDashPoint(parentExt,this,dest);
                ExtensionCommands.moveActor(parentExt,room,id,getRelativePoint(false),dashLocation,20f,true);
                double time = dest.distance(getRelativePoint(false))/20f;
                int runtime = (int)Math.floor(time*1000);
                this.setCanMove(false);
                ExtensionCommands.playSound(parentExt,this.room,this.id,"sfx_gunter_slide",this.location);
                ExtensionCommands.createActorFX(parentExt,room,this.id,"gunter_slide_trail",runtime,this.id+"_gunterTrail",true,"Bip01",true,false,team);
                ExtensionCommands.createActorFX(parentExt,room,this.id,"gunter_slide_snow",runtime,this.id+"_gunterTrail",true,"Bip01",true,false,team);
                this.setLocation(dashLocation);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new GunterAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),runtime,TimeUnit.MILLISECONDS);
                ExtensionCommands.actorAbilityResponse(this.parentExt,player,"q",true,getReducedCooldown(cooldown),gCooldown);
                break;
            case 2:
                Line2D maxRangeLine = Champion.getMaxRangeLine(new Line2D.Float(this.location,dest),7f);
                this.fireProjectile(new BottleProjectile(this.parentExt,this,maxRangeLine,11f,0.5f,this.id+"projectile_gunter_bottle"),"projectile_gunter_bottle",dest,8f);
                ExtensionCommands.actorAbilityResponse(this.parentExt,player,"w",true,getReducedCooldown(cooldown),gCooldown);
                break;
            case 3: //TODO: Last left off - actually make this do damage
                this.ultActivated = true;
                this.ultPoint = dest;
                ExtensionCommands.createActorFX(parentExt,room,this.id,"gunter_powered_up",2500,this.id+"_gunterPower",true,"Bip01",true,false,team);
                ExtensionCommands.createActorFX(parentExt,room,this.id,"gunter_bottle_cone",2500,this.id+"gunterUlt",true,"Bip01",true,false,team);
                this.setCanMove(false);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new GunterAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),2500,TimeUnit.MILLISECONDS);
                ExtensionCommands.actorAnimate(parentExt,room,this.id,"spell3b",2500,true);
                ExtensionCommands.actorAbilityResponse(this.parentExt,player,"e",true,getReducedCooldown(cooldown),gCooldown);
                ExtensionCommands.playSound(parentExt,room,this.id,"sfx_gunter_bottles_ultimate",this.location);
                break;
        }
    }
    @Override
    public void attack(Actor a){
        this.handleAttack(a);
        currentAutoAttack = SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(a, new PassiveAttack(a),"gunter_bottle_projectile"),500, TimeUnit.MILLISECONDS);
    }

    public void shatter(Actor a){
        ExtensionCommands.playSound(parentExt,room,"","sfx_gunter_slide_shatter",a.getLocation());
        ExtensionCommands.createWorldFX(parentExt,room,id,"gunter_belly_slide_bottles",a.getId()+"_shattered",500,(float)a.getLocation().getX(),(float)a.getLocation().getY(),false,team,0f);
        for(Actor actor : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),a.getLocation(), 2f)){
            if(actor.getTeam() != this.team && !a.getId().equalsIgnoreCase(actor.getId())){
                JsonNode spellData = this.parentExt.getAttackData(this.getAvatar(),"spell4");
                handleSpellVamp(getSpellDamage(spellData));
                actor.addToDamageQueue(this,getSpellDamage(spellData),spellData);
            }
        }
    }

    @Override
    public boolean canUseAbility(){
        if(ultActivated) return false;
        else return super.canUseAbility();
    }

    @Override
    public boolean canAttack(){
        if(this.ultActivated) return false;
        else return super.canAttack();
    }

    @Override
    public boolean canMove(){
        if(this.ultActivated) return false;
        else return super.canMove;
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData){
        if(attackData.has("spellName") && attackData.get("spellName").asText().contains("spell_1")) this.shatter(a);
        else if(attackData.has("attackName") && attackData.get("attackName").asText().contains("Basic")) this.shatter(a);
    }

    private class GunterAbilityRunnable extends AbilityRunnable {

        public GunterAbilityRunnable(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            setCanMove(true);
            Point2D loc = getRelativePoint(false);
            ExtensionCommands.createWorldFX(parentExt,room,id+"_slide","gunter_belly_slide_bottles",id+"_slideBottles",500,(float)loc.getX(),(float)loc.getY(),false,team,0f);
            ExtensionCommands.playSound(parentExt,room,id,"sfx_gunter_slide_shatter",loc);
            List<Actor> affectedActors = Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),loc,4f);
            for(Actor a : affectedActors){
                if(a.getTeam() != team){
                    handleSpellVamp(getSpellDamage(spellData));
                    a.addToDamageQueue(Gunter.this,getSpellDamage(spellData),spellData);
                }
            }
        }

        @Override
        protected void spellW() {

        }

        @Override
        protected void spellE() {
            setCanMove(true);
            ultActivated = false;
            ultPoint = null;
        }
        @Override
        protected void spellPassive() {

        }
    }

    private class BottleProjectile extends Projectile {

        public BottleProjectile(ATBPExtension parentExt, UserActor owner, Line2D path, float speed, float hitboxRadius, String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
        }

        @Override
        protected void hit(Actor victim) {
            JsonNode spellData = parentExt.getAttackData(getAvatar(),"spell2");
            handleSpellVamp(getSpellDamage(spellData));
            victim.addToDamageQueue(Gunter.this,getSpellDamage(spellData),spellData);
            destroy();
        }
        @Override
        public void destroy(){
            super.destroy();
            ExtensionCommands.playSound(parentExt,room,"","sfx_gunter_bottle_shatter",this.location);
            ExtensionCommands.createWorldFX(parentExt,room,this.id,"gunter_bottle_shatter",this.id+"_bottleShatter",500,(float)this.location.getX(),(float)this.location.getY(),false,team,0f);
        }
    }

    private class PassiveAttack implements Runnable {

        Actor target;

        PassiveAttack(Actor target){ this.target = target;}

        @Override
        public void run() {
            JsonNode attackData = parentExt.getAttackData(getAvatar(),"basicAttack");
            Gunter.this.handleLifeSteal();
            this.target.addToDamageQueue(Gunter.this,(int)getPlayerStat("attackDamage"),attackData);
        }
    }
}
