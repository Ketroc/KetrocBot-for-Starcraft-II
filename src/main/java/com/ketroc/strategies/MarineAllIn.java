package com.ketroc.strategies;

import com.github.ocraft.s2client.protocol.game.Race;
import com.ketroc.managers.ArmyManager;
import com.ketroc.micro.MarineOffense;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.utils.Chat;
import com.ketroc.utils.InfluenceMaps;
import com.ketroc.utils.PosConstants;

import java.util.List;

public class MarineAllIn {
    public static int MIN_MARINES_TO_ATTACK = 18;

    public static boolean doAttack;
    public static boolean isInitialBuildUp = true;

    public static void onGameStart() {
        if (PosConstants.opponentRace != Race.ZERG) { //attack earlier vs terran/protoss
            MIN_MARINES_TO_ATTACK = 11;
        }
    }

    public static void onStep() {
        if (!Strategy.MARINE_ALLIN) {
            return;
        }
        setInitialBuildUp();
    }

    private static void setInitialBuildUp() {
        if (isInitialBuildUp && UnitMicroList.numOfUnitClass(MarineOffense.class) >= MIN_MARINES_TO_ATTACK) {
            isInitialBuildUp = false;
        }
    }

    public static boolean getDoOffense() {
        List<MarineOffense> marineList = UnitMicroList.getUnitSubList(MarineOffense.class);
        if (ArmyManager.doOffense && marineList.size() < MIN_MARINES_TO_ATTACK) {
            Chat.chatWithoutSpam("Run away! Run away!", 30);
            return false;
        }
        else if (!ArmyManager.doOffense &&
                marineList.size() >= MarineAllIn.MIN_MARINES_TO_ATTACK + 2 &&
                marineList.stream().allMatch(marine -> InfluenceMaps.getValue(InfluenceMaps.pointInMainBase,
                        marine.unit.unit().getPosition().toPoint2d()))) {
            Chat.chatWithoutSpamInvisToHuman("Hell. It's about time.", 30);
            return true;
        }
        else {
            return ArmyManager.doOffense;
        }
    }
}
