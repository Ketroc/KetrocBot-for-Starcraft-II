package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;
import com.ketroc.terranbot.bots.Ketroc;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.WorkerManager;

import java.util.Comparator;
import java.util.List;

public class CannonRushDefense {
    public static int cannonRushStep;
    public static boolean isSafe = true; //if no probe or cannon left (only pylons remain)

    public static void onStep() {
        switch (cannonRushStep) {
            case 0: //was a pylon built in my vision in the first 2min of the game
                if (Bot.OBS.getGameLoop() < Time.toFrames("1:50") &&
                        !UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_PYLON).stream()
                                .anyMatch(pylon -> UnitUtils.getDistance(pylon.unit(), LocationConstants.baseLocations.get(0)) < 40)) {
                    cannonRushStep++;
                }
                break;

            case 1: //1 scv per probe, calc #scvs per cannon TODO: go to case 2 if a cannon completes (use ScvTarget.giveUp)

                //remove dead targets (and probes that left)
                ScvTarget.removeDeadTargets();

                //remove scvs that have died
                ScvTarget.targets.stream().forEach(scvTarget -> scvTarget.scvs.removeIf(u -> !u.isAlive()));

                //add new probes, cannons, and pylons as targets
                addNewTarget(Units.PROTOSS_PHOTON_CANNON);
                addNewTarget(Units.PROTOSS_PROBE);
                addNewTarget(Units.PROTOSS_PYLON);

                //fill targets list with required scvs, send those scvs to attack their target, send marines
                List<Unit> marines = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE);
                List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(LocationConstants.baseLocations.get(0), 50);

                for (ScvTarget scvTarget : ScvTarget.targets) {
                    //attack with scvs
                    if (scvTarget.targetUnit.unit().getType() == Units.PROTOSS_PROBE) {
                        System.out.println("scvTarget.numScvs = " + scvTarget.numScvs);
                        System.out.println("scvTarget.scvs.size() = " + scvTarget.scvs.size());
                    }
                    for (int i = 0; i < scvTarget.numScvs - scvTarget.scvs.size() && !availableScvs.isEmpty(); i++) {
                        UnitInPool newScv = availableScvs.remove(0);
                        Bot.ACTION.unitCommand(newScv.unit(), Abilities.ATTACK, scvTarget.targetUnit.unit(), false);
                        scvTarget.addScv(newScv);
                    }
                    //attack with marines if cannon
                    if (!marines.isEmpty() && scvTarget.targetUnit.unit().getType() == Units.PROTOSS_PHOTON_CANNON) {
                        Bot.ACTION.unitCommand(marines, Abilities.ATTACK, scvTarget.targetUnit.unit(), false);
                    }
                }
                //if marines don't have a target, attack starting with closest to natural base
                if (!marines.isEmpty() && !ScvTarget.targets.isEmpty()) {

                    Unit cleanUp = ScvTarget.targets.stream()
                            .map(scvTarget -> scvTarget.targetUnit.unit())
                            .sorted(Comparator.comparing(targetUnit -> UnitUtils.getDistance(targetUnit, GameCache.baseList.get(1).getCcPos())))
                            .findFirst()
                            .get();
                    Bot.ACTION.unitCommand(marines, Abilities.ATTACK, cleanUp, false);
                }

                if (Ketroc.isDebugOn) Bot.DEBUG.debugTextOut("targets list size: " + ScvTarget.targets.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * (6)) / 1080.0)), Color.WHITE.WHITE, 12);
                int i = 1;
                for (ScvTarget target : ScvTarget.targets) {
                    if (Ketroc.isDebugOn) Bot.DEBUG.debugTextOut("scvs: " + target.scvs.size() + " on: " + (Units)target.targetUnit.unit().getType(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * (6 + i++)) / 1080.0)), Color.WHITE, 12);
                }

                //check if safe to build/expand (only pylons remaining)
                isSafe = !ScvTarget.targets.stream().anyMatch(t -> t.targetUnit.unit().getType() == Units.PROTOSS_PROBE || t.targetUnit.unit().getType() == Units.PROTOSS_PHOTON_CANNON);

                //check if cannon rush is done
                if (ScvTarget.targets.isEmpty()) {
                    cannonRushStep = 0;
                }
                break;

            case 2: //TODO: create a stage2 for vs completed cannon (cyclone etc)
                break;

        }
    }

    private static void addNewTarget(Units unitType) {
        for (UnitInPool newTarget : UnitUtils.getEnemyUnitsOfType(unitType)) {
            if (UnitUtils.isVisible(newTarget) &&  //is visible
                    UnitUtils.getDistance(newTarget.unit(), LocationConstants.baseLocations.get(0)) < 50 && //within 50 distance
                    !ScvTarget.targets.stream().anyMatch(t -> t.targetUnit.getTag().equals(newTarget.getTag()))) { //not already in the targets list
                ScvTarget.targets.add(new ScvTarget(newTarget));
            }
        }
    }

}
