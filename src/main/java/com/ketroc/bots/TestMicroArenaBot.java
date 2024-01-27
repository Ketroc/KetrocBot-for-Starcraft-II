package com.ketroc.bots;

import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.geometry.Position;
import com.ketroc.micro.Marauder;
import com.ketroc.micro.MarineOffense;
import com.ketroc.utils.DebugHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TestMicroArenaBot extends S2Agent {
    UnitInPool myPylonUip;
    UnitInPool enemyPylonUip;
    boolean isLeftSpawn;
    Point2d myPylonPos;
    Point2d enemyPylonPos;
    Point2d midPos;
    Point2d myCornerPos;
    Point2d enemyCornerPos;
    Point2d sidePos;
    boolean isWallDown;

    @Override
    public void onGameStart() {
        super.onGameStart();
    }

    @Override
    public void onStep() {
        super.onStep();
        if (!isWallDown) {
            // ==== SETUP ====
            initMap();
            isWallDown = observation().getUnits(Alliance.NEUTRAL, uip -> uip.unit().getType().getUnitTypeId() == 390).size() == 4;

            if (isWallDown) {
                // ==== FIRST ATTACK FRAME ====
                observation().getUnits(Alliance.SELF).forEach(uip -> {
                    actions().unitCommand(uip.unit(), Abilities.ATTACK, midPos, false);
                    actions().unitCommand(uip.unit(), Abilities.ATTACK, enemyPylonPos, true);
                });
                actions().sendActions();
            }
            return;
        }

        actions().sendActions();
        debug().sendDebug();
    }

    private void initMap() {
        myPylonUip = observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.PROTOSS_PYLON).stream().findFirst().orElse(null);
        enemyPylonUip = observation().getUnits(Alliance.ENEMY, u -> u.unit().getType() == Units.PROTOSS_PYLON).stream().findFirst().orElse(null);
        if (myPylonUip != null) {
            myPylonPos = myPylonUip.unit().getPosition().toPoint2d();
            enemyPylonPos = enemyPylonUip.unit().getPosition().toPoint2d();
            isLeftSpawn = myPylonPos.getX() < 30f;
            initializePathingPosLists();
            visualizePositions(); //TODO: delete (for testing)
            int q=123; //TODO: delete (for testing)
        }
    }

    private void initializePathingPosLists() {
        int xDir = isLeftSpawn ? 1 : -1;
        int yDir = 1; //new Random().nextInt(0,2) * 2 - 1; //randomly 1 or -1
        midPos = Position.midPoint(myPylonPos, enemyPylonPos).add(0,6f*yDir);
        myCornerPos = myPylonPos.add(xDir*5, 17*yDir);
        enemyCornerPos = enemyPylonPos.add(xDir*-5, 17*yDir);
        sidePos = Position.midPoint(myCornerPos, enemyCornerPos).add(xDir*-3, 0);
    }

    public void visualizePositions() {
        DebugHelper.draw3dBox(myPylonPos, Color.GREEN, 1);
        DebugHelper.draw3dBox(enemyPylonPos, Color.GREEN, 1);
        DebugHelper.draw3dBox(midPos, Color.GREEN, 1);
        DebugHelper.draw3dBox(sidePos, Color.RED, 1);
        DebugHelper.draw3dBox(myCornerPos, Color.RED, 1);
        DebugHelper.draw3dBox(enemyCornerPos, Color.RED, 1);
        debug().sendDebug();
    }

    private List<Point2d> getPath() {
        return List.of(midPos, enemyPylonPos);
    }

    private List<Point2d> getLowHpUnitPath(UnitInPool unit) {
        int xDir = isLeftSpawn ? 1 : -1;
        float x = unit.unit().getPosition().getX();
        List<Point2d> path = new ArrayList<>();
        return x*xDir < (sidePos.getX()+2)*xDir
                ? List.of(myPylonPos, myCornerPos, enemyCornerPos, enemyPylonPos)
                : List.of(sidePos, enemyCornerPos, enemyPylonPos);
    }

    @Override
    public void onUnitCreated(UnitInPool unitInPool) {

    }

    @Override
    public void onUnitIdle(UnitInPool unitInPool) {

    }

    @Override
    public void onUnitDestroyed(UnitInPool unitInPool) {

    }

    @Override
    public void onGameEnd() {

    }
}