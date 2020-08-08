package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.WorkerManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CannonRushDefense {
    public static int cannonRushStep;
    public static boolean isSafe = true; //if no probe or cannon left (only pylons remain)
    public static List<ScvTarget> targets = new ArrayList<>();

    public static void onStep() {
        switch (cannonRushStep) {
            case 0: //was a pylon built in my vision in the first 2min of the game
                if (Bot.OBS.getGameLoop() < 2500 && !GameCache.allEnemiesMap.getOrDefault(Units.PROTOSS_PYLON, Collections.emptyList()).isEmpty()) {
                    cannonRushStep++;
                }
                break;

            case 1: //1 scv per probe, calc #scvs per cannon TODO: go to case 2 if a cannon completes (use ScvTarget.giveUp)

                //remove dead targets (and probes that left)
                for (int i=0; i<targets.size(); i++) {
                    if (!targets.get(i).targetUnit.isAlive() || targets.get(i).targetUnit.getLastSeenGameLoop() != Bot.OBS.getGameLoop() ||
                            targets.get(i).targetUnit.unit().getPosition().toPoint2d().distance(LocationConstants.baseLocations.get(0)) > 50) {
                        if (!targets.get(i).scvs.isEmpty()) {
                            Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(targets.get(i).scvs), Abilities.HARVEST_GATHER, GameCache.defaultRallyNode, false);
                        }
                        targets.remove(i--);
                    }
                }

                //remove scvs that have died
                targets.stream().forEach(scvTarget -> scvTarget.scvs.removeIf(u -> !u.isAlive()));

                //add new probes, cannons, and pylons as targets
                addNewTarget(Units.PROTOSS_PHOTON_CANNON);
                addNewTarget(Units.PROTOSS_PROBE);
                addNewTarget(Units.PROTOSS_PYLON);

                //fill targets list with required scvs, send those scvs to attack their target, send marines
                List<Unit> marines = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE);
                List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(LocationConstants.baseLocations.get(0), 50);
                //if (Bot.isDebugOn) Bot.DEBUG.debugTextOut("availableScvs: " + CannonRushDefense.isSafe, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * 5) / 1080.0)), Color.WHITE, 12);
                for (ScvTarget target : targets) {
                    //attack with scvs
                    for (int i = 0; i < target.numScvs - target.scvs.size() && !availableScvs.isEmpty(); i++) {
                        UnitInPool newScv = availableScvs.remove(0);
                        Bot.ACTION.unitCommand(newScv.unit(), Abilities.ATTACK, target.targetUnit.unit(), false);
                        target.scvs.add(newScv);
                    }
                    //attack with marines if cannon
                    if (!marines.isEmpty() && target.targetUnit.unit().getType() == Units.PROTOSS_PHOTON_CANNON) {
                        Bot.ACTION.unitCommand(marines, Abilities.ATTACK, target.targetUnit.unit(), false);
                    }
                }
                //if marines don't have a target, attack any target
                if (!marines.isEmpty() && !targets.isEmpty()) {
                    Bot.ACTION.unitCommand(marines, Abilities.ATTACK, targets.get(0).targetUnit.unit(), false);
                }

//                if (Bot.isDebugOn) Bot.DEBUG.debugTextOut("targets list size: " + targets.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * (6)) / 1080.0)), Color.WHITE, 12);
//                int i = 1;
//                for (ScvTarget target : targets) {
//                    if (Bot.isDebugOn) Bot.DEBUG.debugTextOut("scvs: " + target.scvs.size() + " on: " + (Units)target.targetUnit.unit().getType(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * (6 + i++)) / 1080.0)), Color.WHITE, 12);
//                }

                //check if safe to build/expand (only pylons remaining)
                isSafe = !targets.stream().anyMatch(t -> t.targetUnit.unit().getType() == Units.PROTOSS_PROBE || t.targetUnit.unit().getType() == Units.PROTOSS_PHOTON_CANNON);

                //check if cannon rush is done
                if (targets.isEmpty()) {
                    cannonRushStep = 0;
                }
                break;

            case 2: //TODO: create a stage2 for vs completed cannon (cyclone etc)
                break;

        }
    }

    private static void addNewTarget(Units unitType) {
        for (UnitInPool newTarget : UnitUtils.getEnemyUnitsOfType(unitType)) {
            if (newTarget.getLastSeenGameLoop() == Bot.OBS.getGameLoop() &&  //is visible
                    UnitUtils.getDistance(newTarget.unit(), LocationConstants.baseLocations.get(0)) < 50 && //within 50 distance
                    !targets.stream().anyMatch(t -> t.targetUnit.getTag().equals(newTarget.getTag()))) { //not already in the targets list
                targets.add(new ScvTarget(newTarget));
            }
        }
    }

}
