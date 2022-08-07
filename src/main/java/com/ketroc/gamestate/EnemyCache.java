package com.ketroc.gamestate;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.bots.Bot;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.Chat;
import com.ketroc.utils.PosConstants;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class EnemyCache {
    public static Set<Enemy> enemyList = new HashSet<>();
    public static Set<Upgrades> enemyUpgrades = new HashSet<>();

    public static void onStepStart() {
        //TODO: remember creep tumor positions
        enemyList.removeIf(enemy ->
                enemy == null ||
                !enemy.isAlive() ||
                (Bot.OBS.getVisibility(enemy.getPosition()) == Visibility.VISIBLE &&
                        enemy.getLastSeenFrame() != Time.nowFrames() &&
                        UnitUtils.canNeverMove(enemy.getType())) ||
                enemy.isExpired()
        );
        updateEnemyUpgrades();
    }

    private static void updateEnemyUpgrades() {
        switch (PosConstants.opponentRace) {
            case ZERG:
                if (!enemyUpgrades.contains(Upgrades.BURROW) &&
                        enemyList.stream().anyMatch(enemy -> UnitUtils.isBurrowed(enemy.getUip().unit()) &&
                                enemy.getType() != Units.ZERG_CREEP_TUMOR_BURROWED)) {
                    enemyUpgrades.add(Upgrades.BURROW);
                    Chat.chat("Burrow Upgrade Detected");
                }
                if (!enemyUpgrades.contains(Upgrades.CENTRIFICAL_HOOKS) &&
                        enemyList.stream().anyMatch(enemy -> enemy.getType().toString().contains("BANELING") &&
                                enemy.getUip().unit().getHealth().orElse(0f) > 30)) {
                    enemyUpgrades.add(Upgrades.CENTRIFICAL_HOOKS);
                    Chat.chat("Baneling Speed Upgrade Detected");
                }
                if (!enemyUpgrades.contains(Upgrades.NEURAL_PARASITE) &&
                        !Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getBuffs().contains(Buffs.NEURAL_PARASITE)).isEmpty()) {
                    enemyUpgrades.add(Upgrades.NEURAL_PARASITE);
                }
            case PROTOSS:
                if (!enemyUpgrades.contains(Upgrades.PSI_STORM_TECH) &&
                        Bot.OBS.getEffects().stream().anyMatch(effect -> effect.getEffect() == Effects.PSI_STORM_PERSISTENT)) {
                    enemyUpgrades.add(Upgrades.PSI_STORM_TECH);
                    Chat.chat("Psi Storm Upgrade Detected");
                }
                if (!enemyUpgrades.contains(Upgrades.BLINK_TECH) &&
                        EnemyCache.enemyList.stream().anyMatch(enemy -> enemy.getType() == Units.PROTOSS_STALKER &&
                                enemy.getDistanceFromPrevStep() > 3 && enemy.getDistanceFromPrevStep() <= 8.1f)) {
                    enemyUpgrades.add(Upgrades.BLINK_TECH);
                    Chat.chat("Blink Upgrade Detected");
                    Strategy.DO_USE_CYCLONES = false;
                }

            case TERRAN:

        }
    }

    public static void onStep() {

    }

    public static void onStepEnd() {
        //save this step's unit object for next game loop
        enemyList.forEach(enemy -> {
            enemy.setPrevStepUnit(enemy.getUip().unit());
            enemy.setPrevUnitFrame(enemy.getUip().getLastSeenGameLoop());
        });
    }

    public static void onUnitEnteredVision(UnitInPool uip) {
        if (uip.unit().getAlliance() == Alliance.ENEMY &&
                uip.unit().getDisplayType() != DisplayType.SNAPSHOT &&
                !isIgnored((Units)uip.unit().getType())) {
            add(uip);
        }
    }

    public static boolean contains(Tag unitTag) {
        return enemyList.stream().anyMatch(enemy -> enemy.is(unitTag));
    }

    public static void add(UnitInPool uip) {
        if (!contains(uip.getTag())) {
            enemyList.add(new Enemy(uip));
        }
    }

    public static void print() {
        System.out.println("\nENEMY UNIT CACHE");
        System.out.println("================");
        enemyList.stream()
                .collect(Collectors.groupingBy(enemy -> enemy.getUip().unit().getType()))
                .forEach((unitType, enemies) -> System.out.println(enemies.size() + ": " + unitType));
        System.out.println("\nENEMY UPGRADES");
        System.out.println("==============");
        enemyUpgrades.forEach(upgrade -> System.out.println(upgrade));
    }


    //egg/baneling_cocoon ignored since they are destroyed on morph completion
    public static boolean isIgnored(Units unitType) {
        switch (unitType) {
            case ZERG_EGG: case ZERG_LARVA: case ZERG_BANELING_COCOON:
            case PROTOSS_INTERCEPTOR:
            case TERRAN_NUKE:
                return true;
        }
        return false;
    }
}
