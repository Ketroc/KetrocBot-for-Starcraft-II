package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.utils.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MannerMule {
    private static final int NUM_MULES_REQUIRED = 24; //24
    public static boolean doTrollMule;
    public static long lastScanFrame;
    private static long lastMulingFrame;
    private static final float[][] G = new float[][]{{3.3024902f,6.704834f}, {1.9221191f,6.704834f}, {0.59814453f,5.6484375f}, {1.5634766f,0.030029297f}, {4.395996f,0.8918457f}, {3.0510254f,0.0f}, {2.8840332f,3.7207031f}, {0.0f,2.555664f}, {0.36108398f,1.0817871f}, {4.4553223f,3.869873f}, {0.07910156f,4.234619f}, {4.459717f,5.4296875f}};
    private static final float[][] E = new float[][]{{1.6293945f,6.769287f}, {0.18457031f,6.7944336f}, {0.23486328f,5.104004f}, {1.5522461f,0.14111328f}, {4.343506f,6.6799316f}, {2.9819336f,0.08496094f}, {4.270996f,0.14111328f}, {0.04321289f,1.7817383f}, {0.0f,0.0f}, {1.713623f,3.453125f}, {0.09692383f,3.4106445f}, {3.0732422f,6.7944336f}};
    private static final float[][] Z = new float[][]{{1.6293945f,6.769287f}, {0.18457031f,6.7944336f}, {0.92211914f,5.1899414f}, {1.5522461f,0.14111328f}, {4.343506f,6.6799316f}, {2.9819336f,0.08496094f}, {4.270996f,0.14111328f}, {3.467041f,1.498291f}, {0.0f,0.0f}, {2.4111328f,2.8762207f}, {1.6601562f,4.029541f}, {3.0732422f,6.7944336f}};
    private static final float[][] SMILEY = new float[][]{{0.5969238f,2.7768555f},{6.6813965f,7.114502f}, {3.4831543f,6.9501953f}, {7.0219727f,0.38134766f}, {9.880615f,6.991455f}, {0.5461426f,6.911133f}, {6.5024414f,3.1728516f}, {0.0f,4.8327637f}, {7.4853516f,5.998535f}, {3.0566406f,0.50146484f}, {2.5610352f,6.1054688f}, {1.6032715f,8.69751f}, {7.057373f,9.885254f}, {1.5270996f,1.2824707f}, {3.2993164f,9.803223f}, {5.1660156f,0.0f}, {5.0498047f,10.387939f}, {10.333984f,4.9470215f}, {8.627441f,8.486816f}, {4.4396973f,7.5134277f}, {5.496826f,7.6706543f}, {3.4592285f,3.1867676f} ,{9.613281f,3.0327148f}, {8.612793f,1.4697266f}};
    private static List<Point2d> ggPosList;
    private static List<Point2d> ezPosList;
    private static List<Point2d> smileyPosList;
    private static Point2d scanPos;
    private static List<UnitInPool> mules = new ArrayList<>();

    public static void onGameStart() {
        if (PosConstants.muleLetterPosList.isEmpty()) {
            return;
        }
        scanPos = getMidPoint();
        ggPosList = getMulePositions(PosConstants.muleLetterPosList.get(0), G);
        ggPosList.addAll(getMulePositions(PosConstants.muleLetterPosList.get(1), G));
        ezPosList = getMulePositions(PosConstants.muleLetterPosList.get(0), E);
        ezPosList.addAll(getMulePositions(PosConstants.muleLetterPosList.get(1), Z));
        smileyPosList = getMulePositions(scanPos.add(-5, 6.5f), SMILEY);
    }

    private static List<Point2d> getMulePositions(Point2d topLeft, float[][] symbol) {
        List<Point2d> symbolPositions =  new ArrayList<>();
        for (float[] xy : symbol) {
            symbolPositions.add(Point2d.of(topLeft.getX()+xy[0], topLeft.getY()-xy[1]));
        }
        return symbolPositions;
    }

    public static void onStep() {
        if (PosConstants.muleLetterPosList.isEmpty()) {
            return;
        }

        if (!doTrollMule ||
                (!hasMuled() && UnitUtils.numScansAvailable() < NUM_MULES_REQUIRED + (hasScanned() ? 0 : 1))) {
            return;
        }

        //start new message
        writeMessage();
        Chat.tag("mule_message");

        initializeMuleList();
        if (mules.size() != 24) {
            return;
        }

        //change to EZ and smiley
        if (Time.nowFrames() > lastMulingFrame + Time.toFrames(20)) {
            List<Point2d> posList = (Time.nowFrames() > lastMulingFrame + Time.toFrames(35)) ? smileyPosList : ezPosList;
            for (int i=0; i<posList.size(); i++) {
                if (ActionIssued.getCurOrder(mules.get(i)).isEmpty() &&
                        UnitUtils.getDistance(mules.get(i).unit(), posList.get(i)) > 0.1f) {
                    ActionHelper.unitCommand(mules.get(i).unit(), Abilities.MOVE, posList.get(i), false);
                }
            }
        }
    }

    private static void initializeMuleList() {
        //initialize mules when called down
        if (mules.isEmpty() && hasMuled()) {
            List<UnitInPool> nearbyMules = Bot.OBS.getUnits(Alliance.SELF, mule ->
                    mule.unit().getType() == Units.TERRAN_MULE &&
                    UnitUtils.getDistance(mule.unit(), scanPos) < 18);
            if (nearbyMules.size() == 24) {
                mules = nearbyMules;
            }
        }

        //quit when first mule dies
        if (!mules.isEmpty() &&
                mules.stream().anyMatch(mule -> !mule.isAlive())) {
            mules.clear();
        }
    }

    public static void writeMessage() {
        List<Unit> ocList = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_ORBITAL_COMMAND)
                .stream()
                .filter(u -> u.unit().getEnergy().orElse(0f) >= 50)
                .map(UnitInPool::unit)
                .collect(Collectors.toList());

        //scan first
        if (!hasScanned()) {
            Unit oc = ocList.stream()
                    .max(Comparator.comparing(u -> u.getEnergy().orElse(0f)))
                    .get();
            ActionHelper.unitCommand(oc, Abilities.EFFECT_SCAN, scanPos, false);
            lastScanFrame = Time.nowFrames();
            return;
        }

        //calldown mules
        if (isScanActive() && !hasMuled()) {
            ggPosList.forEach(p -> ActionHelper.unitCommand(ocList, Abilities.EFFECT_CALL_DOWN_MULE, p, false));
            lastMulingFrame = Time.nowFrames();
        }
    }

    private static boolean isScanActive() {
        return Bot.OBS.getEffects().stream()
                .anyMatch(effect -> effect.getEffect() == Effects.SCANNER_SWEEP &&
                        effect.getPositions().stream().anyMatch(p -> p.distance(scanPos) < 1));
    }

    private static Point2d getMidPoint() {
        return PosConstants.muleLetterPosList.get(0)
                .add(PosConstants.muleLetterPosList.get(1))
                .div(2)
                .add(2f, -5f);
    }

    private static boolean hasScanned() {
        return Time.nowFrames() <= lastScanFrame + Time.toFrames(73);
    }

    private static boolean hasMuled() {
        return Time.nowFrames() <= lastMulingFrame + Time.toFrames(70);
    }

    public static void checkIfGameIsWon() {
        if (Bot.OBS.getFoodUsed() > 165 && Base.numEnemyBases() <= 4 && Bot.OBS.getFoodUsed() > UnitUtils.getEnemySupply() + 110) {
            if (Bot.OBS.getMinerals() > 6000) {
                Chat.chatNeverRepeat("I wonder if I can convert my minerals into bitcoin");
            } else if (Bot.OBS.getVespene() > 6000) {
                Chat.chatNeverRepeat("I wonder if I can convert my gas into bitcoin");
            } else {
                Chat.chatNeverRepeat(Chat.WINNING_BM_CHAT);
            }
            doTrollMule = true;
        } else {
            doTrollMule = false;
        }
    }
}
