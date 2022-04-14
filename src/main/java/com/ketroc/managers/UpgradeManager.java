package com.ketroc.managers;

import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrade;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.purchases.Purchase;
import com.ketroc.purchases.PurchaseStructure;
import com.ketroc.purchases.PurchaseUpgrade;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.ActionIssued;
import com.ketroc.utils.UnitUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UpgradeManager {

    public static final List<Upgrades> airUpgrades = new ArrayList<>(List.of(
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL1,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL2,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3));

    public static final List<Upgrades> structureUpgrades = new ArrayList<>(List.of(
            Upgrades.TERRAN_BUILDING_ARMOR,
            Upgrades.HISEC_AUTO_TRACKING));

    public static final List<Upgrades> bioUpgrades = new ArrayList<>(List.of(
            Upgrades.TERRAN_INFANTRY_WEAPONS_LEVEL1,
            Upgrades.TERRAN_INFANTRY_ARMORS_LEVEL1,
            Upgrades.TERRAN_INFANTRY_WEAPONS_LEVEL2,
            Upgrades.TERRAN_INFANTRY_ARMORS_LEVEL2,
            Upgrades.TERRAN_INFANTRY_WEAPONS_LEVEL3,
            Upgrades.TERRAN_INFANTRY_ARMORS_LEVEL3));

    public static final List<Upgrades> bioAttackThenArmorUpgrades = new ArrayList<>(List.of(
            Upgrades.TERRAN_INFANTRY_WEAPONS_LEVEL1,
            Upgrades.TERRAN_INFANTRY_WEAPONS_LEVEL2,
            Upgrades.TERRAN_INFANTRY_WEAPONS_LEVEL3,
            Upgrades.TERRAN_INFANTRY_ARMORS_LEVEL1,
            Upgrades.TERRAN_INFANTRY_ARMORS_LEVEL2,
            Upgrades.TERRAN_INFANTRY_ARMORS_LEVEL3));

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

    public static List<Upgrades> armoryUpgradeList = new ArrayList<>(airUpgrades);
    public static List<Upgrades> engBayUpgradeList = new ArrayList<>(structureUpgrades);

    public static void onStep() {
        if (Strategy.techBuilt) {
            checkEngBays();
            checkArmories();
        }
        checkStarportTechLabs();
        checkFactoryTechLabs();
    }

    public static void updateUpgradeList(Upgrade upgrade) {
        engBayUpgradeList.remove(upgrade);
        armoryUpgradeList.remove(upgrade);
        starportUpgradeList.remove(upgrade);
    }

    private static void checkArmories() {
        if (armoryUpgradeList.isEmpty()) {
            return;
        }
        List<Unit> armories = UnitUtils.myUnitsOfType(Units.TERRAN_ARMORY);
        Unit idleArmory = armories.stream().filter(unit -> ActionIssued.getCurOrder(unit).isEmpty()).findFirst().orElse(null);

        //if an armory is idle and not already in purchase queue for a new upgrade
        if (idleArmory != null && !Purchase.isUpgradeQueued(idleArmory.getTag())) {
            Upgrades nextUpgrade = getNextArmoryUpgrade(armories);
            if (nextUpgrade != null) {
                KetrocBot.purchaseQueue.add(new PurchaseUpgrade(nextUpgrade, Bot.OBS.getUnit(idleArmory.getTag())));
            }
        }
    }

    private static void checkEngBays() {
        if (engBayUpgradeList.isEmpty()) {
            return;
        }
        List<Unit> engBays = UnitUtils.myUnitsOfType(Units.TERRAN_ENGINEERING_BAY);

        //check if armory is needed for +2/+2
        if (bioUpgradesNeedArmory(engBays)) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY));
        }


        //if an eng bay is idle and not already in purchase queue for a new upgrade
        Unit idleEngBay = engBays.stream().filter(unit -> ActionIssued.getCurOrder(unit).isEmpty()).findFirst().orElse(null);
        if (idleEngBay != null && !Purchase.isUpgradeQueued(idleEngBay.getTag())) {
            Upgrades nextUpgrade = getNextEngBayUpgrade(engBays);
            if (nextUpgrade != null) {
                KetrocBot.purchaseQueue.add(new PurchaseUpgrade(nextUpgrade, Bot.OBS.getUnit(idleEngBay.getTag())));
            }
        }
    }

    private static boolean bioUpgradesNeedArmory(List<Unit> engBays) {
        return UnitUtils.numMyUnits(Units.TERRAN_ARMORY, true) == 0 &&
                (engBayUpgradeList.contains(Upgrades.TERRAN_INFANTRY_ARMORS_LEVEL2) ||
                        engBayUpgradeList.contains(Upgrades.TERRAN_INFANTRY_WEAPONS_LEVEL2)) &&
                engBays.stream()
                        .flatMap(u -> u.getOrders().stream())
                        .anyMatch(order ->
                                ((order.getAbility() == Abilities.RESEARCH_TERRAN_INFANTRY_WEAPONS &&
                                        !Bot.OBS.getUpgrades().contains(Upgrades.TERRAN_INFANTRY_WEAPONS_LEVEL1)) ||
                                (order.getAbility() == Abilities.RESEARCH_TERRAN_INFANTRY_ARMOR &&
                                        !Bot.OBS.getUpgrades().contains(Upgrades.TERRAN_INFANTRY_ARMORS_LEVEL1))) &&
                                order.getProgress().orElse(0f) > 0.5f);
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

    private static Upgrades getNextEngBayUpgrade(List<Unit> engBays) {
        for (Upgrades nextUpgrade : engBayUpgradeList) {
            String abilityStr = Bot.OBS.getUpgradeData(false).get(nextUpgrade).getAbility().toString();
            if (engBays.stream()
                    .flatMap(armory -> ActionIssued.getCurOrder(armory).stream())
                    .noneMatch(actionIssued -> abilityStr.contains(actionIssued.ability.toString()))) { // eg "RESEARCH_TERRAN_INFANTRY_WEAPONS_LEVEL1" contains "RESEARCH_TERRAN_INFANTRY_WEAPONS"
                return nextUpgrade;
            }
        }
        return null;
    }

    private static void checkFactoryTechLabs() {
        //don't start blue flame until 4 total hellions exist
        if (!Strategy.DO_USE_HELLIONS &&
                Strategy.gamePlan != GamePlan.GHOST_HELLBAT &&
                UnitUtils.numMyUnits(UnitUtils.HELLION_TYPE, true) > 0) {
            Strategy.DO_USE_HELLIONS = true;
            PurchaseUpgrade.add(Upgrades.INFERNAL_PRE_IGNITERS);

            //get +1, +2, and +3 mech attack upgrade
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
        if (UnitUtils.numMyUnits(Units.TERRAN_BANSHEE, true) == 0) {
            return;
        }

        //if all upgrades done
        if (starportUpgradeList.isEmpty()) {
            return;
        }
        List<Upgrades> starportUpgradesInProgress = UnitUtils.myUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream()
                .filter(unit -> ActionIssued.getCurOrder(unit).isPresent())
                .map(unit -> Bot.abilityToUpgrade.get(ActionIssued.getCurOrder(unit).get().ability))
                .collect(Collectors.toList());

        starportUpgradeList.stream()
                .filter(upgrade -> !starportUpgradesInProgress.contains(upgrade))
                .filter(upgrade -> upgrade != Upgrades.BANSHEE_SPEED || ArmyManager.doOffense) //don't get banshee speed until on the offense
                .findFirst()
                .ifPresent(upgrade -> {
                    UnitUtils.myUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream()
                            .filter(unit -> ActionIssued.getCurOrder(unit).isEmpty())
                            .findFirst()
                            .ifPresent(techLab -> {
                                if (!Purchase.isUpgradeQueued(upgrade)) {
                                    KetrocBot.purchaseQueue.addFirst(new PurchaseUpgrade(upgrade, Bot.OBS.getUnit(techLab.getTag())));
                                }
                            });
                });
    }

    private static void getStarportUpgrades() { //TODO: don't start if making vikings
        if (!starportUpgradeList.isEmpty() && !Purchase.containsUpgrade()) {
            UnitUtils.myUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream()
                    .filter(techLab -> ActionIssued.getCurOrder(techLab).isEmpty())
                    .findFirst()
                    .map(techLab -> Bot.OBS.getUnit(techLab.getTag()))
                    .ifPresent(techLab -> KetrocBot.purchaseQueue.add(new PurchaseUpgrade(starportUpgradeList.remove(0), techLab)));
        }
    }


}
