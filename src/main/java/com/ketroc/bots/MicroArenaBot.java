package com.ketroc.bots;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.UnitType;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrade;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.observation.Alert;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.geometry.Position;
import com.ketroc.micro.Marauder;
import com.ketroc.micro.MarauderMicroArena;
import com.ketroc.micro.MarineMicroArena;
import com.ketroc.micro.MarineOffense;
import com.ketroc.utils.*;
import com.ketroc.utils.Error;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MicroArenaBot extends Bot {
    List<MarineMicroArena> marines = new ArrayList<>();
    List<MarauderMicroArena> marauders = new ArrayList<>();
    UnitInPool myPylonUip;
    UnitInPool enemyPylonUip;
    static boolean isLeftSpawn;
    static Point2d myPylonPos;
    static Point2d enemyPylonPos;
    static Point2d midPos;
//    static Point2d myCornerPos;
//    static Point2d enemyCornerPos;
//    static Point2d sidePos;
    boolean isArmySpawned;
    boolean isPlateauMicroMap;

    public MicroArenaBot(String opponentId) {
        super(opponentId);
    }

    @Override
    public void onGameStart() {
        super.onGameStart();
    }

    @Override
    public void onStep() {
        super.onStep();
        if (!isArmySpawned) {
            // ==== SETUP ====
            initMap();
            initArmy();
            ACTION.sendActions();
            return;
        }

        // ==== EVERY OTHER ATTACK FRAME ====
        //marine micro
        marines.forEach(MarineMicroArena::onStep);

        //marauder micro
        marauders.forEach(MarauderMicroArena::onStep);

        ACTION.sendActions();
    }

    private void initArmy() {
        marines = Bot.OBS.getUnits(u -> u.unit().getType() == Units.TERRAN_MARINE).stream()
                .map(marineUip -> new MarineMicroArena(marineUip, getPath()))
                .toList();
        marauders = Bot.OBS.getUnits(u -> u.unit().getType() == Units.TERRAN_MARAUDER).stream()
                .map(marauderUip -> new MarauderMicroArena(marauderUip, getPath()))
                .toList();
        marines.forEach(MarineMicroArena::onStep);
        marauders.forEach(MarauderMicroArena::onStep);
        isArmySpawned = !marines.isEmpty();
    }

    private void initMap() {
        myPylonUip = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.PROTOSS_PYLON).stream().findFirst().orElse(null);
        enemyPylonUip = Bot.OBS.getUnits(Alliance.ENEMY, u -> u.unit().getType() == Units.PROTOSS_PYLON).stream().findFirst().orElse(null);
        if (myPylonUip != null) {
            myPylonPos = myPylonUip.unit().getPosition().toPoint2d();
            enemyPylonPos = enemyPylonUip.unit().getPosition().toPoint2d();
            isPlateauMicroMap = Bot.OBS.terrainHeight(Position.midPoint(myPylonPos, enemyPylonPos)) > 10;
            isLeftSpawn = myPylonPos.getX() < 30f;
            initializePathingPosLists();
        }
    }

    private void initializePathingPosLists() {
        int xDir = isLeftSpawn ? 1 : -1;
        int yDir = new Random().nextInt(0,2) * 2 - 1; //randomly 1 or -1
        midPos = Position.midPoint(myPylonPos, enemyPylonPos).add(0,6f*yDir);
//        myCornerPos = myPylonPos.add(xDir*5, 17*yDir);
//        enemyCornerPos = enemyPylonPos.add(xDir*-5, 17*yDir);
//        sidePos = Position.midPoint(myCornerPos, enemyCornerPos).add(xDir*-3, 0);
    }

    public void visualizePositions() {
        DebugHelper.draw3dBox(myPylonPos, Color.GREEN, 1);
        DebugHelper.draw3dBox(enemyPylonPos, Color.GREEN, 1);
        DebugHelper.draw3dBox(midPos, Color.GREEN, 1);
//        DebugHelper.draw3dBox(sidePos, Color.RED, 1);
//        DebugHelper.draw3dBox(myCornerPos, Color.RED, 1);
//        DebugHelper.draw3dBox(enemyCornerPos, Color.RED, 1);
        DEBUG.sendDebug();
    }

    private List<Point2d> getPath() {
        return isPlateauMicroMap
                ? List.of(enemyPylonPos)
                : List.of(midPos, enemyPylonPos);
    }

//    public static List<Point2d> getLowHpUnitPath(UnitInPool uip) {
//        int xDir = isLeftSpawn ? 1 : -1;
//        float x = uip.unit().getPosition().getX();
//        return x*xDir < (sidePos.getX()+2)*xDir
//                ? List.of(myPylonPos, myCornerPos, enemyCornerPos, enemyPylonPos)
//                : List.of(sidePos, enemyCornerPos, enemyPylonPos);
//    }

    @Override
    public void onUnitEnterVision(UnitInPool unitInPool) {

    }

    @Override
    public void onGameFullStart() {
        System.out.println("reached onGameFullStart");
    }

    @Override
    public void onGameEnd() {

    }

    @Override
    public void onUnitIdle(UnitInPool unitInPool) {

    }

    @Override
    public void onUnitDestroyed(UnitInPool unitInPool) {

    }

    @Override
    public void onAlert(Alert alert) {
        try {

        }
        catch (Throwable e) {
            Error.onException(e);
        }
    }

    @Override
    public void onUnitCreated(UnitInPool unitInPool) {

    }

    @Override
    public void onBuildingConstructionComplete(UnitInPool unitInPool) {

    }

    @Override
    public void onUpgradeCompleted(Upgrade upgrade) {

    }

    @Override
    public void onNydusDetected() {

    }

    @Override
    public void onNuclearLaunchDetected() {

    }
}