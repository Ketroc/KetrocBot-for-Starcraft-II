package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.*;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.ketroc.terranbot.managers.ActionErrorManager;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.Time;

import java.util.HashMap;
import java.util.Map;

public class Bot extends S2Agent {
    public static ActionInterface ACTION;
    public static ObservationInterface OBS;
    public static QueryInterface QUERY;
    public static DebugInterface DEBUG;
    public static ControlInterface CONTROL;
    public static boolean isDebugOn;
    public static boolean isRealTime;
    public static String opponentId;
    public static Map<Abilities, Units> abilityToUnitType = new HashMap<>(); //TODO: move
    public static Map<Abilities, Upgrades> abilityToUpgrade = new HashMap<>(); //TODO: move
    public static long gameFrame = -1;
    public static long stepStartTime;

    public Bot(boolean isDebugOn, String opponentId, boolean isRealTime) {
        this.isDebugOn = isDebugOn;
        this.opponentId = opponentId;
        this.isRealTime = isRealTime;
    }

    @Override
    public void onGameStart() {
        OBS = observation();
        ACTION = actions();
        QUERY = query();
        DEBUG = debug();
        CONTROL = control();

        System.out.println("Units.TERRAN_REFINERY_RICH (before) = " + Units.TERRAN_REFINERY_RICH.getUnitTypeId());
        if (Bot.OBS.getGameInfo().getMapName().contains("5.0.")) {
            Units.remapForBuild(81009);
            System.out.println("Units.TERRAN_REFINERY_RICH (after) = " + Units.TERRAN_REFINERY_RICH.getUnitTypeId());
        }

        //load abilityToUnitType map
        OBS.getUnitTypeData(false).forEach((unitType, unitTypeData) -> {
            unitTypeData.getAbility().ifPresent(ability -> {
                if (ability instanceof Abilities && unitType instanceof Units) {
                    abilityToUnitType.put((Abilities) ability, (Units) unitType);
                }
            });
        });

        //load abilityToUpgrade map
        OBS.getUpgradeData(false).forEach((upgrade, upgradeData) -> {
            upgradeData.getAbility().ifPresent(ability -> {
                if (ability instanceof Abilities && upgrade instanceof Upgrades) {
                    switch ((Abilities) ability) { //fix for api bug
                        case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL1_V2:
                            ability = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL1;
                            break;
                        case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL2_V2:
                            ability = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL2;
                            break;
                        case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL3_V2:
                            ability = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL3;
                            break;
                    }
                    abilityToUpgrade.put((Abilities) ability, (Upgrades) upgrade);
                }
            });
        });
    }

    @Override
    public void onStep() {
//        if (Time.nowFrames() % Strategy.SKIP_FRAMES == 0) {
//            stepStartTime = System.currentTimeMillis();
//        }
        Bot.OBS.getActionErrors().forEach(actionError -> ActionErrorManager.actionErrorList.add(actionError));
    }
}
