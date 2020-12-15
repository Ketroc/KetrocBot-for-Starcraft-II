package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrade;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.utils.UnitUtils;
import com.ketroc.terranbot.bots.KetrocBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseUpgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class UpgradeManager {
    public static boolean doStarportUpgrades;

    public static final List<Upgrades> shipAttack = new ArrayList<>(List.of(
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL1,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL2,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3));

    public static final List<Upgrades> shipArmor = new ArrayList<>(List.of(
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3));

    public static List<Upgrades> starportUpgradeList = new ArrayList<>(List.of(
            Upgrades.BANSHEE_CLOAK,
            Upgrades.BANSHEE_SPEED));

    public static void onStep() {
        //updateUpgradeList();
        checkArmories();
        checkEngBay();
        checkStarportTechLabs();
    }

    public static void updateUpgradeList(Upgrade upgrade) {
        shipAttack.remove(upgrade);
        shipArmor.remove(upgrade);
        starportUpgradeList.remove(upgrade);
    }

    private static void checkArmories() {
        //done, if already 3-3
        if (shipArmor.isEmpty() && shipAttack.isEmpty()) {
            return;
        }
        List<Unit> armories = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_ARMORY);
        Unit idleArmory = armories.stream().filter(unit -> unit.getOrders().isEmpty()).findFirst().orElse(null);

        //if an armory is idle and not already in purchase queue for a new upgrade
        if (idleArmory != null && !Purchase.isUpgradeQueued(idleArmory.getTag())) {
            Upgrades nextUpgrade = getNextArmoryUpgrade(armories);
            if (nextUpgrade != null) {
                KetrocBot.purchaseQueue.add(new PurchaseUpgrade(nextUpgrade, Bot.OBS.getUnit(idleArmory.getTag())));
            }
        }
    }

    private static Upgrades getNextArmoryUpgrade(List<Unit> armories) {
        Optional<Unit> activeArmory = armories.stream()
                .filter(unit -> !unit.getOrders().isEmpty())
                .findFirst();
        Abilities activeAbility = (activeArmory.isPresent()) ? (Abilities)activeArmory.get().getOrders().get(0).getAbility() : null;
        if (activeAbility == Abilities.RESEARCH_TERRAN_SHIP_WEAPONS) {
            if (!shipArmor.isEmpty()) return shipArmor.get(0);
        }
        else if (activeAbility == Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING) {
            if (!shipAttack.isEmpty()) return shipAttack.get(0);
        }
        else if (shipArmor.size() >= shipAttack.size() && !shipArmor.isEmpty()) {
            return shipArmor.get(0);
        }
        else if (!shipAttack.isEmpty()) {
            return shipAttack.get(0);
        }
        return null;
    }

    private static void checkEngBay() {

    }

    private static void checkStarportTechLabs() {
        //don't start cloak/speed until 1 banshee in production
        if (!doStarportUpgrades && UnitUtils.getNumFriendlyUnits(Units.TERRAN_BANSHEE, true) > 0) {
            doStarportUpgrades = true;
        }

        //if all upgrades done
        if (starportUpgradeList.isEmpty()) {
            return;
        }
        List<Upgrades> starportUpgradesInProgress = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream()
                .filter(unit -> UnitUtils.getOrder(unit) != null)
                .map(unit -> Bot.abilityToUpgrade.get(unit.getOrders().get(0).getAbility()))
                .collect(Collectors.toList());

        if (doStarportUpgrades) { //if at least 1 banshee
            starportUpgradeList.stream()
                    .filter(upgrade -> !starportUpgradesInProgress.contains(upgrade))
                    .findFirst()
                    .ifPresent(upgrade -> {
                        UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream()
                                .filter(unit -> unit.getOrders().isEmpty())
                                .findFirst()
                                .ifPresent(techLab -> {
                                    if (!Purchase.isUpgradeQueued(upgrade)) {
                                        KetrocBot.purchaseQueue.add(new PurchaseUpgrade(upgrade, Bot.OBS.getUnit(techLab.getTag())));
                                    }
                                });
                    });
        }
    }

    private static void getStarportUpgrades() { //TODO: don't start if making vikings
        if (!starportUpgradeList.isEmpty() && !Purchase.containsUpgrade()) {
            UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream()
                    .filter(techLab -> techLab.getOrders().isEmpty())
                    .findFirst()
                    .map(techLab -> Bot.OBS.getUnit(techLab.getTag()))
                    .ifPresent(techLab -> KetrocBot.purchaseQueue.add(new PurchaseUpgrade(starportUpgradeList.remove(0), techLab)));
        }
    }


}
