package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.champions.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

//TODO: Add more accurate pathing by creating more points alongside the main ones already defined. Also add collision detection.
public class Minion extends Actor{
    enum AggroState{MOVING, PLAYER, TOWER, MINION, BASE} //State in which the minion is targeting
    enum MinionType{RANGED, MELEE, SUPER} //Type of minion
    private AggroState state = AggroState.MOVING;
    private MinionType type;
    private String target;
    private final double[] blueBotX = {36.90,26.00,21.69,16.70,3.44,-9.56,-21.20,-28.02,-33.11,-36.85}; //Path points from blue base to purple base
    private final double[] blueBotY = {2.31,8.64,12.24,17.25,17.81,18.76,14.78,7.19,5.46,2.33};
    private final double[] blueTopX = {36.68, 30.10, 21.46, 18.20, -5.26, -12.05, -24.69, -28.99, -35.67};
    private final double[] blueTopY = {-2.56, -7.81, -12.09, -16.31, -17.11, -17.96, -13.19, -7.50, -2.70};
    private float travelTime; //How long the minion has been traveling
    private int pathIndex = 0; //What stage in their pathing to the other side are they in
    private boolean attacking = false;
    private int lane;
    private boolean dead = false;
    private Map<String, Integer> aggressors;
    private Line2D movementLine;

    public Minion(ATBPExtension parentExt, Room room, int team, int minionNum, int wave, int lane){
        this.attackCooldown = 300;
        this.avatar = "creep"+team+"_";
        String typeString = "super";
        if(minionNum <= 2){
            typeString = "melee"+minionNum;
            this.type = MinionType.MELEE;
            this.maxHealth = 450;
        }else if(minionNum <= 4){
            typeString = "ranged"+minionNum;
            this.avatar+="ranged";
            this.type = MinionType.RANGED;
            this.maxHealth = 350;
        }
        else{
            this.type = MinionType.SUPER;
            this.avatar+="super";
            this.maxHealth = 500;
        }
        this.currentHealth = this.maxHealth;
        float x = (float) blueBotX[0]; //Bot Lane
        float y = (float) blueBotY[0];
        if(team == 0) x = (float) blueBotX[blueBotX.length-1];
        if(lane == 0){ //Top Lane
            x = (float) blueTopX[0];
            y = (float) blueTopY[0];
            if(team == 0) x = (float) blueTopX[blueTopX.length-1];
        }
        this.speed = 1.75f;
        this.location = new Point2D.Float(x,y);
        this.id = team+"creep_"+lane+typeString+wave;
        this.room = room;
        this.team = team;
        this.parentExt = parentExt;
        this.lane = lane;
        this.actorType = ActorType.MINION;
        if(team == 0){
            if(lane == 0) pathIndex = blueTopX.length-1;
            else pathIndex = blueBotX.length-1;
        }
        this.movementLine = new Line2D.Float(this.location,this.location);
        aggressors = new HashMap<>(3);
    }

    public void move(ATBPExtension parentExt){ // Moves the minion along the defined path
        Actor newTarget = this.getNewTarget();
        if(newTarget != null && this.movementLine != null){
            this.setTarget(this.parentExt, newTarget.getId());
            return;
        }
        double[] pathX;
        double[] pathY;
        if(this.lane != 0){
            pathX = blueBotX;
            pathY = blueBotY;
        }else{
            pathX = blueTopX;
            pathY=blueTopY;
        }
        if(this.pathIndex < pathX.length && this.pathIndex > -1){
            Point2D destination = new Point2D.Float((float) pathX[this.pathIndex], (float) pathY[this.pathIndex]);
            for(User u : room.getUserList()){
                ExtensionCommands.moveActor(parentExt,u,this.id,this.location,destination, (float) this.speed,true);
            }
            this.movementLine = new Line2D.Float(this.location,destination);
            this.travelTime = 0.1f;
        }
    }

    public ISFSObject creationObject(){ //Object fed to the extension command to spawn the minion
        ISFSObject minion = new SFSObject();
        String actorName = "creep";
        if(this.team == 1) actorName+="1";
        if(this.type != MinionType.MELEE) actorName+="_";
        minion.putUtfString("id",this.id);
        minion.putUtfString("actor",actorName + getType());
        ISFSObject spawnPoint = new SFSObject();
        spawnPoint.putFloat("x", (float) location.getX());
        spawnPoint.putFloat("y",0f);
        spawnPoint.putFloat("z", (float) location.getY());
        minion.putSFSObject("spawn_point",spawnPoint);
        minion.putFloat("rotation",0f);
        minion.putInt("team",this.team);
        return minion;
    }

    //Checks if point is within minion's aggro range
    public boolean nearEntity(Point2D p){
        return this.location.distance(p)<=5;
    }
    public boolean nearEntity(Point2D p, float radius){ return this.location.distance(p)<=5+radius; }
    public boolean facingEntity(Point2D p){ // Returns true if the point is in the same direction as the minion is heading
        //TODO: Some minions don't attack others attacking the base when they spawn
        double deltaX = movementLine.getX2()-location.getX();
        //Negative = left Positive = right
        if(Double.isNaN(deltaX)) return false;
        if(deltaX>0 && p.getX()>this.location.getX()) return true;
        else if(deltaX<0 && p.getX()<this.location.getX()) return true;
        else return false;
    }

    public boolean withinAttackRange(Point2D p){ // Checks if point is within minion's attack range
        float range = 1.25f;
        if(this.type == MinionType.RANGED) range = 4f;
        else if(this.type == MinionType.SUPER) range = 1.5f;
        return this.location.distance(p)<=range;
    }

    public boolean withinAttackRange(Point2D p, float radius){
        float range = 1f;
        if(this.type == MinionType.RANGED) range = 4f;
        else if(this.type == MinionType.SUPER) range = 1.5f;
        range+=radius;
        return this.location.distance(p)<=range;
    }
    private int findPathIndex(){ //Finds the nearest point along the defined path for the minion to travel to
        double[] pathX;
        double[] pathY;
        if(this.lane != 0){
            pathX = blueBotX;
            pathY = blueBotY;
        }else{
            pathX = blueTopX;
            pathY = blueTopY;
        }
        double shortestDistance = 100;
        int index = -1;
        if(movementLine == null) index = 1;
        else{
            for(int i = 0; i < pathX.length; i++){
                Point2D pathPoint = new Point2D.Double(pathX[i],pathY[i]);
                if(Math.abs(pathPoint.distance(this.location)) < shortestDistance){
                    shortestDistance = pathPoint.distance(this.location);
                    index = i;
                }
            }
            if(Math.abs(shortestDistance) < 0.01 && ((this.team == 1 && index+1 != pathX.length) || (this.team == 0 && index-1 != 0))){
                if(this.team == 1) index++;
                else index--;
            }
        }
        return index;
    }

    public void addTravelTime(float time){
        this.travelTime+=time;
    }
    
    public Point2D getLocation(){
        return this.location;
    }

    public Point2D getRelativePoint(){ //Gets player's current location based on time
        Point2D rPoint = new Point2D.Float();
        if(this.movementLine == null) return this.location;
        float x2 = (float) this.movementLine.getX2();
        float y2 = (float) this.movementLine.getY2();
        float x1 = (float) movementLine.getX1();
        float y1 = (float) movementLine.getY1();
        double dist = movementLine.getP1().distance(movementLine.getP2());
        double time = dist/(float)this.speed;
        double currentTime = this.travelTime;
        if(currentTime>time) currentTime=time;
        double currentDist = (float)this.speed*currentTime;
        float x = (float)(x1+(currentDist/dist)*(x2-x1));
        float y = (float)(y1+(currentDist/dist)*(y2-y1));
        rPoint.setLocation(x,y);
        if(dist != 0) return rPoint;
        else return location;
    }

    public void arrived(){ //Ran when the minion arrives at a point on the path
        this.travelTime = 0;
        this.location = this.movementLine.getP2();
        if(this.team == 1) this.pathIndex++;
        else this.pathIndex--;
    }

    public int getPathIndex(){
        return this.pathIndex;
    }

    public String getTarget(){
        return this.target;
    }
    public void setTarget(ATBPExtension parentExt, String id){ //Sets what the minion is targeting. Also changes state
        this.target = id;
        if(id.contains("tower")) this.state = AggroState.TOWER;
        else if(id.contains("creep")) this.state = AggroState.MINION;
        else if(id.contains("base")) this.state = AggroState.BASE;
        else this.state = AggroState.PLAYER;
        //this.stopMoving(parentExt);
    }

    public int getState(){
        switch(this.state){
            case PLAYER:
                return 1;
            case MINION:
                return 2;
            case TOWER:
                return 3;
            case BASE:
                return 4;
            default:
                return 0;
        }
    }

    public void moveTowardsActor(ATBPExtension parentExt, Point2D dest){ //Moves towards a specific point. TODO: Have the path stop based on collision radius
        this.movementLine = new Line2D.Float(this.location,dest);
        this.travelTime = 0.1f;
        for(User u : room.getUserList()){
            ExtensionCommands.moveActor(parentExt,u,this.id,this.location,dest,1.75f, true);
        }
    }

    public void setState(int state){ //Really only useful for setting to the movement state.
        double[] pathX;
        double[] pathY;
        if(this.lane != 0){
            pathX = blueBotX;
            pathY = blueBotY;
        }else{
            pathX = blueTopX;
            pathY = blueTopY;
        }
        switch(state){
            case 0:
                int index = findPathIndex();
                if(this.state != AggroState.PLAYER){
                    if(this.team == 0) index--;
                    else index++;
                    if(index < 0) index = 0;
                    if(index >= pathX.length) index = pathX.length-1;
                }
                this.state = AggroState.MOVING;
                Point2D newDest = new Point2D.Float((float) pathX[index], (float) pathY[index]);
                this.movementLine = new Line2D.Float(this.location,newDest);
                this.travelTime = 0;
                this.target = null;
                this.pathIndex = index;
                this.attacking = false;
                break;
            case 1:
                this.state = AggroState.PLAYER;
                break;
            case 2:
                this.state = AggroState.TOWER;
                break;
        }
    }

    public boolean canAttack(){
        return attackCooldown==0;
    }

    @Override
    public void attack(Actor a){
        if(attackCooldown == 0 && this.state != AggroState.MOVING){
            this.stopMoving(parentExt);
            int newCooldown = 1500;
            if(this.type == MinionType.RANGED) newCooldown = 2000;
            else if(this.type == MinionType.SUPER) newCooldown = 1000;
            this.attackCooldown = newCooldown;
            for(User u : room.getUserList()){
                ExtensionCommands.attackActor(parentExt,u,this.id,a.getId(),(float) a.getLocation().getX(), (float) a.getLocation().getY(), false, true);
            }
            if(this.type == MinionType.RANGED){
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedRangedAttack(this,a),300, TimeUnit.MILLISECONDS);
            }else{
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(this,a,20),300,TimeUnit.MILLISECONDS);
            }
        }
    }
    @Override
    public void rangedAttack(Actor a){
        String fxId = "minion_projectile_";
        if(this.team == 1) fxId+="blue";
        else fxId+="purple";
        for(User u : room.getUserList()){
            ExtensionCommands.createProjectileFX(parentExt,u,fxId,this.getId(),a.getId(),"Bip001","Bip001",(float)0.5);
        }
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(this,a,30),500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void die(Actor a) { //TODO: Actor voice line plays after each kill (last hit or not)
        if(this.dead) return;
        this.dead = true;
        this.parentExt.getRoomHandler(this.room.getId()).handleAssistXP(a,aggressors.keySet(), 10);
        for(User u : this.getRoomUsers()){
            ExtensionCommands.knockOutActor(parentExt,u,this.id,a.getId(),30);
            ExtensionCommands.destroyActor(parentExt,u,this.id);
        }
        if(a.getActorType() == ActorType.PLAYER) this.parentExt.getRoomHandler(this.room.getId()).addScore(a.getTeam(),1);
    }

    @Override
    public void update(int msRan) {
        this.location = getRelativePoint();
        if(this.attackCooldown > 0) this.reduceAttackCooldown();
        RoomHandler roomHandler = parentExt.getRoomHandler(this.room.getId());
        if(msRan % 1000 == 0){
            for(String k : aggressors.keySet()){
                if(aggressors.get(k) == 10) aggressors.remove(k);
                else aggressors.put(k,aggressors.get(k)+1);
            }
        }
        switch(this.getState()){
            case 0: // MOVING
                if(this.movementLine == null){
                    this.move(this.parentExt);
                    break;
                }
                if(this.getState() == 0 && (this.getPathIndex() < 10 & this.getPathIndex()>= 0) && this.movementLine.getP2() != null && ((Math.abs(this.movementLine.getP2().distance(this.location)) < 0.2) || Double.isNaN(this.movementLine.getP2().getX()))){
                    this.arrived();
                    this.move(parentExt);
                }else{
                    this.addTravelTime(0.1f);
                }
                Actor newTarget = this.getNewTarget();
                if(newTarget != null) this.setTarget(this.parentExt, newTarget.getId());
                break;
            case 1: //PLAYER TARGET
                UserActor target = roomHandler.getPlayer(this.getTarget());
                Point2D currentPoint = target.getLocation();
                int health = target.getHealth();
                if(!this.nearEntity(currentPoint) || health <= 0){ //Resets the minion's movement if it loses the target
                    this.setState(0);
                    this.move(parentExt);
                }else{
                    if (this.withinAttackRange(currentPoint) && this.canAttack()) this.attack(target);
                    else this.moveTowardsActor(parentExt,currentPoint);
                }
                break;
            case 2: // MINION TARGET
                Minion targetMinion = roomHandler.findMinion(this.getTarget());
                if(targetMinion != null && this.withinAttackRange(targetMinion.getLocation())){
                    if(this.canAttack()){
                        if(!this.isAttacking()){
                            this.setAttacking(true);
                        }
                        if(targetMinion.getHealth() > 0){
                            this.attack(targetMinion);
                        }else{ //Handles tower death and resets minion on tower kill
                            this.setState(0);
                            this.move(parentExt);
                            this.travelTime+=0.1f;
                        }
                    }
                }else if(targetMinion != null){
                    this.moveTowardsActor(parentExt,targetMinion.getLocation()); //TODO: Optimize so it's not sending a lot of commands
                }else{
                    this.setState(0);
                    this.move(parentExt);
                    this.travelTime+=0.1f;
                }
                break;
            case 3: // TOWER TARGET
                Tower targetTower = roomHandler.findTower(this.getTarget());
                if(targetTower != null && this.withinAttackRange(targetTower.getLocation())){
                    if(this.canAttack()){
                        if(!this.isAttacking()){
                            this.setAttacking(true);
                        }
                        if(targetTower.getHealth() > 0){
                            this.attack(targetTower);
                        }else{ //Handles tower death and resets minion on tower kill
                            System.out.println("Tower dead!");
                            if(targetTower.getTowerNum() == 0 || targetTower.getTowerNum() == 3) roomHandler.getOpposingTeamBase(this.team).unlock();
                            this.setState(0);
                            this.move(parentExt);
                            this.travelTime+=0.1f;
                            break;
                        }
                    }
                }else if(targetTower != null){
                    this.addTravelTime(0.1f);
                    //m.moveTowardsActor(parentExt,targetTower.getLocation()); //TODO: Optimize so it's not sending a lot of commands
                }else{
                    this.setState(0);
                    this.move(parentExt);
                    break;
                }
                break;
            case 4: // BASE TARGET
                Base targetBase = roomHandler.getOpposingTeamBase(this.getTeam());
                if(targetBase != null && this.withinAttackRange(targetBase.getLocation(),1.8f)){
                    if(this.canAttack()){
                        if(!this.isAttacking()){
                            this.setAttacking(true);
                        }
                        if(targetBase.getHealth() > 0){
                            this.attack(targetBase);
                        }else{ //Handles base death and ends game
                            parentExt.stopScript(room.getId());
                        }
                    }
                }else if(targetBase != null){
                    this.addTravelTime(0.1f);
                }
                break;
        }
    }

    public void stopMoving(ATBPExtension parentExt){ //Stops moving
        this.travelTime = 0f;
        this.movementLine = new Line2D.Float(this.location,this.location);
        for(User u : room.getUserList()){
            ExtensionCommands.moveActor(parentExt,u,this.id,this.location,this.location,1.75f,false);
        }
    }

    public String getType(){
        if(this.type == MinionType.MELEE) return "";
        else if(this.type == MinionType.RANGED) return "ranged";
        else return "super";
    }

    @Override
    public boolean damaged(Actor a, int damage) {
        if(this.id.equalsIgnoreCase("0creep_0melee01")) return false;
        if(this.dead) return true;
        if(a.getActorType() == ActorType.PLAYER){
            aggressors.put(a.getId(),0);
        }
        this.currentHealth-=damage;
        if(currentHealth <= 0){ //Minion dies
            this.die(a);
            return true;
        }else{
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id",this.id);
            updateData.putInt("currentHealth",(int) currentHealth);
            updateData.putDouble("pHealth",pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            for(User u : this.getRoomUsers()){
                ExtensionCommands.updateActorData(parentExt,u,updateData);
            }
            return false;
        }
    }

    public boolean isAttacking(){
        return this.attacking;
    }

    public void setAttacking(boolean attacking){
        this.attacking = attacking;
    }

    public int getLane(){
        return this.lane;
    }

    public List<User> getRoomUsers(){
        return this.room.getUserList();
    }

    public Actor getNewTarget(){
        RoomHandler roomHandler = parentExt.getRoomHandler(this.room.getId());
        for(Minion minion : roomHandler.getMinions()){ // Check other minions
            if(this.getTeam() != minion.getTeam() && this.getLane() == minion.getLane()){
                if(this.nearEntity(minion.getLocation()) && this.facingEntity(minion.getLocation())){
                    if(this.getTarget() == null){
                        return minion;
                    }
                }
            }
        }
        Base opposingBase = roomHandler.getOpposingTeamBase(this.team);
        if(opposingBase.isUnlocked() && this.nearEntity(opposingBase.getLocation(),1.8f)){
            if(this.getTarget() == null){
                this.moveTowardsActor(parentExt,opposingBase.getLocation());
                return opposingBase;
            }
        }
        for(Tower t: roomHandler.getTowers()){
            if(t.getTeam() != this.getTeam() && this.nearEntity(t.getLocation())){ //Minion prioritizes towers over players
                this.moveTowardsActor(parentExt,t.getLocation());
                return t;
            }
        }
        for(UserActor u : roomHandler.getPlayers()){
            int health = u.getHealth();
            int userTeam = u.getTeam();
            Point2D currentPoint = u.getCurrentLocation();
            if(this.getTeam() != userTeam && health > 0 && this.nearEntity(currentPoint) && this.facingEntity(currentPoint)){
                if(this.getTarget() == null){ //This will probably always be true.
                    ExtensionCommands.setTarget(parentExt,u.getUser(),this.getId(), u.getId());
                    return u;
                }
            }
        }
        return null;
    }
}
