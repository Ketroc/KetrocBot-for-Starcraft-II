package com.ketroc.bots;

import com.github.ocraft.s2client.bot.ClientError;
import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.*;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.game.PlayerInfo;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.launchers.Launcher;
import com.ketroc.managers.ActionErrorManager;
import com.ketroc.utils.DebugHelper;
import com.ketroc.utils.Error;
import com.ketroc.utils.Print;
import com.ketroc.utils.Time;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bot extends S2Agent {
    public static ActionInterface ACTION;
    public static ObservationInterface OBS;
    public static QueryInterface QUERY;
    public static DebugInterface DEBUG;
    public static ControlInterface CONTROL;
    public static String opponentId;
    public static Map<Abilities, Units> abilityToUnitType = new HashMap<>();
    public static Map<Abilities, Upgrades> abilityToUpgrade = new HashMap<>();
    public static long[] prevGameFrames = new long[]{1, 1};
    public static int myId;
    public static int enemyId;
    public static long startTime;

    protected boolean doSkipFrame;

    public Bot(String opponentId) {
        this.opponentId = (!opponentId.equals("")) ? opponentId : "BlizzAI";
    }

    @Override
    public void onGameStart() {
        startTime = System.currentTimeMillis();
        updateIds(); //fix ids for 5.0.6 maps / AIE maps

        OBS = observation();
        ACTION = actions();
        QUERY = query();
        DEBUG = debug();
        CONTROL = control();

        myId = OBS.getPlayerId();
        enemyId = OBS.getGameInfo().getPlayersInfo().stream()
                .map(PlayerInfo::getPlayerId)
                .filter(id -> id != myId)
                .findFirst()
                .get();

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
        DebugHelper.onGameStart();
    }

    @Override
    public void onStep() {
        //repeating same frame
        if (OBS.getGameLoop() == prevGameFrames[0]) {
            Print.print("gameFrame repeated = " + OBS.getGameLoop());
            doSkipFrame = true;
            return;
        }

        OBS.getActionErrors().forEach(actionError -> ActionErrorManager.actionErrorList.add(actionError));

        //mandatory frame delay for real-time
        if (Launcher.isRealTime && prevGameFrames[0] != 1 && Time.before(prevGameFrames[0] + Launcher.STEP_SIZE)) {
            doSkipFrame = true;
            return;
        }
        //save prev 2 frames
        prevGameFrames[1] = prevGameFrames[0];
        prevGameFrames[0] = OBS.getGameLoop();
    }

    public void updateIds() {
        int baseBuild = control().proto().getBaseBuild();
        System.out.println("baseBuild = " + baseBuild);
        String mapName = observation().getGameInfo().getMapName();
        System.out.println("mapName = " + mapName);
        if (baseBuild >= 81009 ||
                (!mapName.endsWith("AIE") && !mapName.matches("^.*\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}$"))) {
            return;
        }

        try {
            //make actions accessible
            Class.forName("com.github.ocraft.s2client.bot.gateway.impl.ActionInterfaceImpl")
                    .getDeclaredField("actions")
                    .setAccessible(true);

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
        catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | IllegalAccessException | InvocationTargetException e) {
            Error.onException(e);
        }
    }

    @Override
    public void onError(List<ClientError> clientErrors, List<String> protocolErrors) {
        super.onError(clientErrors, protocolErrors);
        System.out.println("onError Override:");
        clientErrors.forEach(System.out::println);
        protocolErrors.forEach(System.out::println);
    }
}
