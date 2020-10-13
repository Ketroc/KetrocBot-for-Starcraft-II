package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseUpgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UpgradeManager {
    public static final List<Upgrades> shipAttack = new ArrayList<>(List.of(
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL1,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL2,
            Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3));

    public static final List<Upgrades> shipArmor = new ArrayList<>(List.of(
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2,
            Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3));

    public static void onStep() {
        //updateUpgradeList();
        checkArmories();
        checkEngBay();
        checkStarportTechLabs();
    }

    public static void updateUpgradeList() {
        Bot.OBS.getUpgrades().stream()
                .forEach(upgrade -> {
                    shipAttack.remove(upgrade);
                    shipArmor.remove(upgrade);
                });
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
                BansheeBot.purchaseQueue.add(new PurchaseUpgrade(nextUpgrade, Bot.OBS.getUnit(idleArmory.getTag())));
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
        else if (shipArmor.size() <= shipAttack.size() && !shipArmor.isEmpty()) {
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

    }


}
