package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.models.Base;
import com.ketroc.purchases.Purchase;
import com.ketroc.purchases.PurchaseStructureMorph;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class StructureFloaterExpansionCC extends StructureFloater {

    public Point2d basePos;
    public Point2d safeLandingPos;
    public long createdFrame;
    public Units upgradeType = Units.TERRAN_PLANETARY_FORTRESS;
    public Abilities upgradeAbility = Abilities.MORPH_PLANETARY_FORTRESS;

    public StructureFloaterExpansionCC(UnitInPool structure, Point2d targetPos) {
        super(structure, targetPos, true);
        basePos = targetPos;
        PlacementMap.makeAvailable(structure.unit());
        PlacementMap.makeUnavailable((Units)structure.unit().getType(), basePos);
        doDetourAroundEnemy = true;
        createdFrame = Time.nowFrames();
        List<Base> ocBases = GameCache.baseList.subList(0, Strategy.NUM_BASES_TO_OC);
        if (ocBases.stream().anyMatch(base -> base.getCcPos().distance(targetPos) < 1)) {
            upgradeType = Units.TERRAN_ORBITAL_COMMAND;
            upgradeAbility = Abilities.MORPH_ORBITAL_COMMAND;
        }
    }

    public StructureFloaterExpansionCC(Unit structure, Point2d targetPos) {
        this(Bot.OBS.getUnit(structure.getTag()), targetPos);
    }

    @Override
    public void onStep() {
        //remove if dead
        if (!isAlive()) {
            onDeath();
            return;
        }

        //no new micro commands if landing
        if (UnitUtils.getOrder(unit.unit()) == Abilities.LAND) {
            return;
        }

        //Post-Landed code
        if (!unit.unit().getFlying().orElse(true) &&
                !unit.unit().getActive().get() &&
                Time.nowFrames() > createdFrame + 192) { //don't PF before floating
            //if landed not on position
            if (safeLandingPos != null && basePos.distance(safeLandingPos) > 1) {
                //remove this object when PF starts morphing FIXME: sometimes PF finishes without this if being true
                if (ActionIssued.getCurOrder(unit).stream().anyMatch(order -> order.ability == upgradeAbility) ||
                        unit.unit().getType() == upgradeType) {
                    removeMe = true; //TODO: cancel PF upgrade and move over if enemy command structure dies
                }
                //morph to PF
                else if (!Purchase.isMorphQueued(unit.getTag(), upgradeAbility)) {
                    //Chat.chat("Polanetary forotdds");
                    Chat.tag("OFFENSIVE_PF");
                    KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(upgradeAbility, unit));
                }
            }
            else {
                //TODO: watch while upgrading PF
                // -return to flying with new target (basePos) if enemy base dies
                removeMe = true;
            }
            return;
        }

        //set to new base if this expansion position is now mine
        if (isBaseBlockedByMyCommandStructure()) {
            assignToAnotherBase();
            return;
        }

        //2.5min without landing (land and PF now, or assign to another enemy base)
        if (Time.toSeconds(Time.nowFrames() - createdFrame) >= 150) {
            if (UnitUtils.getDistance(unit.unit(), basePos) < 20) {
                //Chat.chatWithoutSpam("Creating Offensive PF here because 2.5min of travel has elapsed", 120);
                removeCCFromBaseList();
                safeLandingPos = calcSafeLandingPos();
                if (safeLandingPos != null) {
                    ActionHelper.unitCommand(unit.unit(), Abilities.LAND, safeLandingPos, false);
                }
                else {
                    assignToAnotherBase();
                }
            }
            else {
                assignToAnotherBase();
            }
        }

        //approaching landing zone
        if (unit.unit().getFlying().orElse(true) && UnitUtils.getDistance(unit.unit(), basePos) < 13.5) {
            if (isBaseBlockedByEnemyCommandStructure()) {
                //Chat.chatWithoutSpam("Recalculating landing pos for offensive PF", 120);
                removeCCFromBaseList();
                safeLandingPos = calcSafeLandingPos();
                if (safeLandingPos != null) {
                    ActionHelper.unitCommand(unit.unit(), Abilities.LAND, safeLandingPos, false);
                }
                else {
                    super.onStep();
                }
            }
            else {
                ActionHelper.unitCommand(unit.unit(), Abilities.LAND, basePos, false);
                addCCToBaseList();
            }
            return;
        }

        //travel micro
        super.onStep();
    }

    @Override
    public void onArrival() {
        return;
    }

    @Override
    protected Point2d findDetourPos() {
        return findDetourPos2(4f);
    }


    @Override
    //ignores a marine when far away... ignores 3 marines or a missile turret when close
    protected boolean isSafe() {
        boolean isCloseToBase = UnitUtils.getDistance(unit.unit(), basePos) < 20;
        return InfluenceMaps.getAirThreatToStructure(unit.unit()) <= (isCloseToBase ? 7 : 3);
    }

    private Point2d calcSafeLandingPos() {
        Point2d inFrontOfExpansionPos = Position.toHalfPoint(Position.towards(basePos, unit.unit().getPosition().toPoint2d(), 11));
        return calcSafeLandingPos(inFrontOfExpansionPos);
    }
    private Point2d calcSafeLandingPos(Point2d searchOriginPos) {
        //get position in front of enemy command structure
        Base base = GameCache.baseList.stream()
                .filter(b -> b.getCcPos().distance(basePos) < 1)
                .findFirst().get();

        //get a list of cc positions sorted by nearest to flying cc
        List<Point2d> landingPosList = Position.getSpiralList(searchOriginPos, 8);
        landingPosList = landingPosList.stream()
                //.filter(landingPos -> landingPos.distance(basePos) < 12) //in range for PF to kill enemy command structure
                .filter(landingPos -> UnitUtils.isPlaceable(Units.TERRAN_COMMAND_CENTER, landingPos))
                .filter(landingPos -> InfluenceMaps.getGroundThreatToStructure(Units.TERRAN_BARRACKS, landingPos) < 4) //barracks instead of command center: is a hack to help ignore a portion of the kiting buffer of enemy units
                //.filter(landingPos -> !InfluenceMaps.getValue(InfluenceMaps.pointThreatToAir, landingPos))
                .sorted(Comparator.comparing(p -> p.distance(basePos)))
                .collect(Collectors.toList());

        //go to another base if no viable landing positions
        if (landingPosList.isEmpty()) {
            assignToAnotherBase();
        }
        //query closest valid landing position
        else {
            return Position.getFirstPosFromQuery(landingPosList, Abilities.LAND_COMMAND_CENTER);
        }
        return null;
    }

    private void assignToAnotherBase() {
        Point2d randomUnownedBasePos = UnitUtils.getRandomUnownedBasePos();
        if (randomUnownedBasePos != null) {
            basePos = targetPos = randomUnownedBasePos;
            safeLandingPos = null;
        }
        else {
            super.onStep();
        }
    }

    public void removeCCFromBaseList() {
        GameCache.baseList.stream()
                .filter(base -> base.getCc() != null &&
                        base.getCc().getTag().equals(unit.getTag()))
                .findFirst()
                .ifPresent(base -> base.setCc(null));
    }

    private void addCCToBaseList() {
        GameCache.baseList.stream()
                .filter(base -> targetPos.distance(base.getCcPos()) < 1)
                .findFirst()
                .ifPresent(base -> base.setCc(unit));
    }

    private boolean isBaseBlockedByMyCommandStructure() {
        return !Bot.OBS.getUnits(Alliance.SELF, u ->
                UnitUtils.COMMAND_STRUCTURE_TYPE_TERRAN.contains(u.unit().getType()) &&
                !u.unit().getFlying().orElse(true) &&
                UnitUtils.getDistance(u.unit(), basePos) < 1 &&
                !u.getTag().equals(unit.getTag())).isEmpty();
    }

    private boolean isBaseBlockedByEnemyCommandStructure() {
        return !Bot.OBS.getUnits(Alliance.ENEMY, u ->
                UnitUtils.COMMAND_STRUCTURE_TYPE.contains(u.unit().getType()) &&
                !u.unit().getFlying().orElse(true) &&
                UnitUtils.getDistance(u.unit(), basePos) < 2).isEmpty();
    }

    @Override
    public void onDeath() {
        removeMe = true;
    }
}
