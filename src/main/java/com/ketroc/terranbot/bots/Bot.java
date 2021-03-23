package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.*;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.ketroc.terranbot.managers.ActionErrorManager;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.Time;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    public Bot(boolean isDebugOn, String opponentId, boolean isRealTime) {
        this.isDebugOn = isDebugOn;
        this.opponentId = opponentId;
        this.isRealTime = true; //isRealTime; TODO: for testing realtime changes
    }

    @Override
    public void onGameStart() {
        updateIds(); //fix ids for 5.0.6 maps

        OBS = observation();
        ACTION = actions();
        QUERY = query();
        DEBUG = debug();
        CONTROL = control();

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
        Bot.OBS.getActionErrors().forEach(actionError -> ActionErrorManager.actionErrorList.add(actionError));
    }

    public void updateIds() {
        int baseBuild = control().proto().getBaseBuild();
        String mapName = observation().getGameInfo().getMapName();
        if (baseBuild >= 81009 || !mapName.matches("^.*\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}$")) {
            return;
        }

        try {
            //undo ocraft's id update to 4.10
            if (baseBuild >= 75689) {
                Class<?> units = Class.forName("com.github.ocraft.s2client.protocol.data.Units");
                Class[] args = new Class[2];
                args[0] = int.class;
                args[1] = int.class;
                Method method = units.getDeclaredMethod("updateId", args);
                method.setAccessible(true);

                method.invoke(units, 1960, 1943);
                method.invoke(units, 1956, 1981);
                method.invoke(units, 1955, 1980);
                method.invoke(units, 1961, 1982);
                method.invoke(units, 1962, 1983);
                method.invoke(units, 1963, 1984);
            }

            //update ids to latest patch
            Units.remapForBuild(Integer.MAX_VALUE);
            Abilities.remapForBuild(Integer.MAX_VALUE);
        }
        catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
