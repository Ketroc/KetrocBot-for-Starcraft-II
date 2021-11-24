package com.ketroc.managers;

import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrade;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.purchases.Purchase;
import com.ketroc.purchases.PurchaseUpgrade;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.ActionIssued;
import com.ketroc.utils.UnitUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UpgradeManager {
    public static boolean doStarportTechlabUpgrades;

    public static final List<Upgrades> airUpgrades = new ArrayList<>(List.of(
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL1,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL2,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3));

    public static List<Upgrades> armoryUpgradeList = new ArrayList<>(airUpgrades);

    public static final List<Upgrades> airThenMechUpgrades = new ArrayList<>(List.of(
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL1,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL2,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3,
            Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL1,
            Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL2,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3,
            Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL3));

    public static final List<Upgrades> mechThenAirUpgrades = new ArrayList<>(List.of(
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1,
            Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL1,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2,
            Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL2,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL1,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL2,
            Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL3,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3));

    public static final List<Upgrades> mechAttackUpgrades = new ArrayList<>(List.of(
            Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL1,
            Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL2,
            Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL3));

    public static final List<Upgrades> airAttackUpgrades = new ArrayList<>(List.of(
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL1,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL2,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3));

    public static final List<Upgrades> mechArmorUpgrades = new ArrayList<>(List.of(
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3));

    public static List<Upgrades> starportUpgradeList = new ArrayList<>(List.of(
            Upgrades.BANSHEE_CLOAK,
            Upgrades.BANSHEE_SPEED));

    public static List<Upgrades> bansheeUpgradeList = new ArrayList<>(List.of(
            Upgrades.BANSHEE_CLOAK,
            Upgrades.BANSHEE_SPEED));

    public static List<Upgrades> ravenUpgradeList = new ArrayList<>(List.of(
            Upgrades.RAVEN_CORVID_REACTOR));

    public static void onStep() {
        //updateUpgradeList();
        checkArmories();
        checkStarportTechLabs();
        checkFactoryTechLabs();
    }

    public static void updateUpgradeList(Upgrade upgrade) {
        armoryUpgradeList.remove(upgrade);
        starportUpgradeList.remove(upgrade);
    }

    private static void checkArmories() {
        //done, if already 3-3
        if (armoryUpgradeList.isEmpty()) {
            return;
        }
        List<Unit> armories = UnitUtils.getMyUnitsOfType(Units.TERRAN_ARMORY);
        Unit idleArmory = armories.stream().filter(unit -> ActionIssued.getCurOrder(unit).isEmpty()).findFirst().orElse(null);

        //if an armory is idle and not already in purchase queue for a new upgrade
        if (idleArmory != null && !Purchase.isUpgradeQueued(idleArmory.getTag())) {
            Upgrades nextUpgrade = getNextArmoryUpgrade(armories);
            if (nextUpgrade != null) {
                KetrocBot.purchaseQueue.add(new PurchaseUpgrade(nextUpgrade, Bot.OBS.getUnit(idleArmory.getTag())));
            }
        }
    }

    private static Upgrades getNextArmoryUpgrade(List<Unit> armories) {
        for (Upgrades nextUpgrade : armoryUpgradeList) {
            String abilityStr = Bot.OBS.getUpgradeData(false).get(nextUpgrade).getAbility().toString();
            if (armories.stream()
                    .flatMap(armory -> ActionIssued.getCurOrder(armory).stream())
                    .noneMatch(actionIssued -> abilityStr.contains(actionIssued.ability.toString()))) { // eg "RESEARCH_TERRAN_SHIP_WEAPONS_LEVEL1" contains "RESEARCH_TERRAN_SHIP_WEAPONS"
                return nextUpgrade;
            }
        }
        return null;
    }

    private static void checkFactoryTechLabs() {
        //don't start blue flame until 4 total hellions exist
        if (!Strategy.DO_USE_HELLIONS && UnitUtils.numMyUnits(UnitUtils.HELLION_TYPE, true) > 0) {
            Strategy.DO_USE_HELLIONS = true;
            PurchaseUpgrade.add(Upgrades.INFERNAL_PRE_IGNITERS);

            //get +3attack
            if (!UpgradeManager.armoryUpgradeList.contains(Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL1)) {
                UpgradeManager.armoryUpgradeList.add(Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL1);
            }
            if (!UpgradeManager.armoryUpgradeList.contains(Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL2)) {
                UpgradeManager.armoryUpgradeList.add(Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL2);
            }
            if (!UpgradeManager.armoryUpgradeList.contains(Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL3)) {
                UpgradeManager.armoryUpgradeList.add(Upgrades.TERRAN_VEHICLE_WEAPONS_LEVEL3);
            }
        }
    }

    private static void checkStarportTechLabs() {
        //don't start cloak/speed until 1 banshee in production
        if (!doStarportTechlabUpgrades && UnitUtils.numMyUnits(Units.TERRAN_BANSHEE, true) > 0) {
            doStarportTechlabUpgrades = true;
        }

        //if all upgrades done
        if (starportUpgradeList.isEmpty()) {
            return;
        }
        List<Upgrades> starportUpgradesInProgress = UnitUtils.getMyUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream()
                .filter(unit -> ActionIssued.getCurOrder(unit).isPresent())
                .map(unit -> Bot.abilityToUpgrade.get(ActionIssued.getCurOrder(unit).get().ability))
                .collect(Collectors.toList());

        if (doStarportTechlabUpgrades) { //if at least 1 banshee
            starportUpgradeList.stream()
                    .filter(upgrade -> !starportUpgradesInProgress.contains(upgrade))
                    .filter(upgrade -> upgrade != Upgrades.BANSHEE_SPEED || ArmyManager.doOffense) //don't get banshee speed until on the offense
                    .findFirst()
                    .ifPresent(upgrade -> {
                        UnitUtils.getMyUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream()
                                .filter(unit -> ActionIssued.getCurOrder(unit).isEmpty())
                                .findFirst()
                                .ifPresent(techLab -> {
                                    if (!Purchase.isUpgradeQueued(upgrade)) {
                                        KetrocBot.purchaseQueue.addFirst(new PurchaseUpgrade(upgrade, Bot.OBS.getUnit(techLab.getTag())));
                                    }
                                });
                    });
        }
    }

    private static void getStarportUpgrades() { //TODO: don't start if making vikings
        if (!starportUpgradeList.isEmpty() && !Purchase.containsUpgrade()) {
            UnitUtils.getMyUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream()
                    .filter(techLab -> ActionIssued.getCurOrder(techLab).isEmpty())
                    .findFirst()
                    .map(techLab -> Bot.OBS.getUnit(techLab.getTag()))
                    .ifPresent(techLab -> KetrocBot.purchaseQueue.add(new PurchaseUpgrade(starportUpgradeList.remove(0), techLab)));
        }
    }


}
