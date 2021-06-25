package com.ketroc.strategies.defenses;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.managers.WorkerManager;
import com.ketroc.models.Base;
import com.ketroc.models.StructureScv;
import com.ketroc.purchases.PurchaseStructure;
import com.ketroc.strategies.ScvTarget;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.*;

import java.util.List;

public class CannonRushDefense {
    public static int cannonRushStep;
    public static boolean isSafe = true; //if no probe or cannon left (only pylons remain)

    public static void onStep() {
        switch (cannonRushStep) {
            case 0: //was a pylon built in my vision in the first 2min of the game
                if (Time.nowFrames() < Time.toFrames("1:50") &&
                        !UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, Units.PROTOSS_PYLON,
                                LocationConstants.myMineralPos, 40).isEmpty() &&
                        !isAnyCannonComplete()) {
                    cancelCCFirst();
                    Chat.chat("Cannon Rush Detected");
                    Chat.tag("VS_CANNON_RUSH");
                    Chat.tag("VS_CHEESE");
                    cannonRushStep++;
                }
                break;

            case 1: //1 scv per probe, calc #scvs per cannon TODO: go to case 2 if a cannon completes (use ScvTarget.giveUp)

                //next stage if cannon completed
                if (isAnyCannonComplete()) {
                    cancelScvDefense();
                    cannonRushStep++; //TODO: add case 2 for next steps instead of just giving up here.
                    Bot.ACTION.sendChat("Cannon Rush Defense failed and abandoned.  Hope it doesn't look too bad.", ActionChat.Channel.BROADCAST);
                }

                //remove dead targets (and probes that left)
                ScvTarget.removeDeadTargets();

                //add new probes, cannons, and pylons as targets
                addNewTarget(Units.PROTOSS_PHOTON_CANNON);
                addNewTarget(Units.PROTOSS_PROBE);
                addNewTarget(Units.PROTOSS_PYLON);

                //fill targets list with required scvs, send those scvs to attack their target, send marines
//                List<Unit> marines = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE);
                List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(LocationConstants.baseLocations.get(0), 50);

                for (ScvTarget scvTarget : ScvTarget.targets) {
                    //attack with scvs
                    int numScvsToSend = Math.min(scvTarget.numScvs - scvTarget.getScvList().size(), availableScvs.size());
                    for (int i = 0; i < numScvsToSend; i++) {
                        UnitInPool newScv = availableScvs.remove(0);
                        Base.releaseScv(newScv.unit());
                        //if sending 4+ scvs put some behind the cannon before attacking to prevent scvs blocking each other
                        if (numScvsToSend >= 4 && i < numScvsToSend/2) {
                            Point2d behindCannon = Position.towards(
                                    scvTarget.targetUnit.unit().getPosition().toPoint2d(),
                                    newScv.unit().getPosition().toPoint2d(), 3);
                            ActionHelper.unitCommand(newScv.unit(), Abilities.MOVE, behindCannon, false);
                            ActionHelper.unitCommand(newScv.unit(), Abilities.ATTACK, scvTarget.targetUnit.unit(), true);
                        }
                        else {
                            ActionHelper.unitCommand(newScv.unit(), Abilities.ATTACK, scvTarget.targetUnit.unit(), false);
                        }
                        scvTarget.addScv(newScv);
                    }
//                    //attack with marines if cannon
//                    if (!marines.isEmpty() && scvTarget.targetUnit.unit().getType() == Units.PROTOSS_PHOTON_CANNON) {
//                        ActionHelper.unitCommand(marines, Abilities.ATTACK, scvTarget.targetUnit.unit(), false);
//                    }
                }
//                //if marines don't have a target, attack starting with closest to natural base
//                if (!marines.isEmpty() && !ScvTarget.targets.isEmpty()) {
//
//                    Unit cleanUp = ScvTarget.targets.stream()
//                            .map(scvTarget -> scvTarget.targetUnit.unit())
//                            .sorted(Comparator.comparing(targetUnit -> UnitUtils.getDistance(targetUnit, GameCache.baseList.get(1).getCcPos())))
//                            .findFirst()
//                            .get();
//                    ActionHelper.unitCommand(marines, Abilities.ATTACK, cleanUp, false);
//                }

                DebugHelper.addInfoLine("targets list size: " + ScvTarget.targets.size());
                for (ScvTarget target : ScvTarget.targets) {
                    DebugHelper.addInfoLine("scvs: " + target.getScvList().size() + " on: " + target.targetUnit.unit().getType());
                }

                //check if safe to build/expand (only pylons remaining)
                isSafe = !ScvTarget.targets.stream().anyMatch(t -> t.targetUnit.unit().getType() == Units.PROTOSS_PROBE || t.targetUnit.unit().getType() == Units.PROTOSS_PHOTON_CANNON);

                //check if cannon rush is done
                if (ScvTarget.targets.isEmpty()) {
                    cannonRushStep = 0;
                    Chat.chat("Cannon Rush Defense completed");

                }
                break;

            case 2: //TODO: create a stage2 for vs completed cannon (cyclone etc)
                //go back a step when all completed cannons are dead
                if (Bot.OBS.getUnits(Alliance.ENEMY, u ->
                        u.unit().getType() == Units.PROTOSS_PHOTON_CANNON &&
                        u.unit().getBuildProgress() == 1 &&
                        UnitUtils.getDistance(u.unit(), GameCache.baseList.get(0).getCcPos()) < 60).isEmpty()) {
                    cannonRushStep--;
                    Chat.chat("No complete cannons remain.");
                }
                break;

        }
    }

    private static void cancelCCFirst() {
        Chat.chat("cancelling CC First");
        Strategy.BUILD_EXPANDS_IN_MAIN = true;
        StructureScv.cancelProduction(Units.TERRAN_COMMAND_CENTER, GameCache.baseList.get(1).getCcPos());
        KetrocBot.purchaseQueue.removeIf(purchase -> purchase instanceof PurchaseStructure &&
            ((PurchaseStructure) purchase).getStructureType() == Units.TERRAN_COMMAND_CENTER);
    }

    private static boolean isAnyCannonComplete() {
        return UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_PHOTON_CANNON).stream()
                            .anyMatch(cannon -> cannon.unit().getBuildProgress() == 1 &&
                                    (cannon.unit().getShield().orElse(0f) > 1 ||
                                            cannon.unit().getHealth().orElse(0f) > 20));
    }

    public static void cancelScvDefense() {
        ScvTarget.targets.forEach(scvTarget -> scvTarget.cancelTarget());
        ScvTarget.targets.clear();
    }

    private static void addNewTarget(Units unitType) {
        for (UnitInPool newTarget : UnitUtils.getEnemyUnitsOfType(unitType)) {
            if (newTarget.unit().getDisplayType() != DisplayType.SNAPSHOT && !UnitUtils.isInFogOfWar(newTarget) &&  //is visible
                    UnitUtils.getDistance(newTarget.unit(), LocationConstants.baseLocations.get(0)) < 50 && //within 50 distance
                    ScvTarget.targets.stream().noneMatch(t -> t.targetUnit.getTag().equals(newTarget.getTag()))) { //not already in the targets list
                ScvTarget.targets.add(new ScvTarget(newTarget));
            }
        }
    }

    private static void add(UnitInPool targetUnit) {
        ScvTarget.targets.add(new ScvTarget(targetUnit));
    }

}
