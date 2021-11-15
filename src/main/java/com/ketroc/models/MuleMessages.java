package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.bots.Bot;
import com.ketroc.utils.*;

import java.util.*;
import java.util.stream.Collectors;

public class MuleMessages {
    private static final int MAX_MULES_REQUIRED = 35;

    public static final Map<Character, Set<Point2d>> muleLetterPosTable = new Hashtable<>();
    private static char[] message;
    public static boolean doTrollMule;
    private static long lastMuleMessageFrame;
    static {
        //muleLetterPosTable.put('E', new HashSet<>(Set.of(Point2d.of(0.0f, 2.0f), Point2d.of(0.0f, 3.0f), Point2d.of(0.0f, 4.0f), Point2d.of(0.0f, 6.0f), Point2d.of(0.0f, 5.0f), Point2d.of(0.0f, 1.0f), Point2d.of(0.0f, 8.0f), Point2d.of(0.0f, 9.0f), Point2d.of(0.0f, 7.0f), Point2d.of(4.0f, 1.0f), Point2d.of(2.0f, 1.0f), Point2d.of(5.0f, 1.0f), Point2d.of(1.0f, 1.0f), Point2d.of(3.0f, 1.0f), Point2d.of(6.0f, 1.0f), Point2d.of(1.0f, 9.0f), Point2d.of(2.0f, 9.0f), Point2d.of(3.0f, 9.0f), Point2d.of(4.0f, 9.0f), Point2d.of(6.0f, 9.0f), Point2d.of(5.0f, 9.0f), Point2d.of(1.0f, 5.0f), Point2d.of(2.0f, 5.0f), Point2d.of(3.0f, 5.0f), Point2d.of(4.0f, 5.0f), Point2d.of(5.0f, 5.0f))));
        //muleLetterPosTable.put('G', new HashSet<>(Set.of(Point2d.of(1.0f, 1.0f), Point2d.of(0.0f, 2.0f), Point2d.of(0.0f, 4.0f), Point2d.of(0.0f, 3.0f), Point2d.of(1.0f, 7.0f), Point2d.of(0.0f, 6.0f), Point2d.of(0.0f, 5.0f), Point2d.of(4.0f, 8.0f), Point2d.of(2.0f, 8.0f), Point2d.of(6.0f, 7.0f), Point2d.of(3.0f, 8.0f), Point2d.of(5.0f, 8.0f), Point2d.of(5.0f, 1.0f), Point2d.of(2.0f, 0.0f), Point2d.of(3.0f, 0.0f), Point2d.of(6.0f, 3.0f), Point2d.of(6.0f, 2.0f), Point2d.of(4.0f, 0.0f), Point2d.of(4.0f, 4.0f), Point2d.of(5.0f, 4.0f), Point2d.of(6.0f, 4.0f))));
        //muleLetterPosTable.put('Z', new HashSet<>(Set.of(Point2d.of(0.0f, 1.0f), Point2d.of(2.0f, 1.0f), Point2d.of(3.0f, 1.0f), Point2d.of(1.0f, 1.0f), Point2d.of(5.0f, 1.0f), Point2d.of(4.0f, 1.0f), Point2d.of(6.0f, 1.0f), Point2d.of(1.0f, 3.0f), Point2d.of(0.0f, 2.0f), Point2d.of(2.0f, 4.0f), Point2d.of(3.0f, 5.0f), Point2d.of(4.0f, 6.0f), Point2d.of(6.0f, 8.0f), Point2d.of(5.0f, 7.0f), Point2d.of(5.0f, 9.0f), Point2d.of(6.0f, 9.0f), Point2d.of(4.0f, 9.0f), Point2d.of(2.0f, 9.0f), Point2d.of(3.0f, 9.0f), Point2d.of(0.0f, 9.0f), Point2d.of(1.0f, 9.0f))));

        muleLetterPosTable.put('E', new HashSet<>(Set.of(Point2d.of(6.0f, 9.0f), Point2d.of(0.0f, 9.0f), Point2d.of(1.5f, 9.0f), Point2d.of(3.0f, 9.0f), Point2d.of(4.5f, 9.0f), Point2d.of(2.0f, 5.0f), Point2d.of(3.5f, 5.0f), Point2d.of(6.0f, 1.0f), Point2d.of(4.5f, 1.0f), Point2d.of(0.0f, 4.0f), Point2d.of(0.0f, 1.0f), Point2d.of(0.0f, 2.5f), Point2d.of(1.5f, 1.0f), Point2d.of(3.0f, 1.0f), Point2d.of(0.0f, 5.584f), Point2d.of(0.0f, 7.376f))));
        muleLetterPosTable.put('G', new HashSet<>(Set.of(Point2d.of(5.991211f, 6.385498f), Point2d.of(6.0f, 3.5f), Point2d.of(3.8833008f, 7.9995117f), Point2d.of(2.501709f, 7.962158f), Point2d.of(1.1789551f, 7.3415527f), Point2d.of(4.5f, 0.0f), Point2d.of(1.607666f, 0.28808594f), Point2d.of(0.5f, 6.0f), Point2d.of(0.73046875f, 1.5722656f), Point2d.of(0.5f, 3.0f), Point2d.of(3.1325684f, -0.24389648f), Point2d.of(5.6501465f, 0.9868164f), Point2d.of(5.95874f, 2.092041f), Point2d.of(5.048584f, 7.5722656f), Point2d.of(0.5f, 4.5f), Point2d.of(4.3918457f, 3.566162f))));
        muleLetterPosTable.put('Z', new HashSet<>(Set.of(Point2d.of(6.0f, 1.0f), Point2d.of(1.5f, 1.0f), Point2d.of(1.5f, 9.0f), Point2d.of(3.0f, 9.0f), Point2d.of(0.0f, 9.0f), Point2d.of(6.0f, 9.0f), Point2d.of(4.5f, 9.0f), Point2d.of(4.0f, 6.5f), Point2d.of(5.0f, 7.5f), Point2d.of(3.0f, 5.0f), Point2d.of(4.5f, 1.0f), Point2d.of(3.0f, 1.0f), Point2d.of(2.0f, 4.0f), Point2d.of(0.0f, 1.0f), Point2d.of(1.0f, 2.5f))));

        //TODO: lessen mule count
        muleLetterPosTable.put('H', new HashSet<>(Set.of(Point2d.of(0.0f, 1.0f), Point2d.of(0.0f, 2.0f), Point2d.of(0.0f, 3.0f), Point2d.of(0.0f, 5.0f), Point2d.of(0.0f, 4.0f), Point2d.of(0.0f, 6.0f), Point2d.of(0.0f, 7.0f), Point2d.of(0.0f, 8.0f), Point2d.of(1.0f, 5.0f), Point2d.of(0.0f, 9.0f), Point2d.of(2.0f, 5.0f), Point2d.of(4.0f, 5.0f), Point2d.of(3.0f, 5.0f), Point2d.of(5.0f, 5.0f), Point2d.of(6.0f, 5.0f), Point2d.of(6.0f, 3.0f), Point2d.of(6.0f, 2.0f), Point2d.of(6.0f, 4.0f), Point2d.of(6.0f, 1.0f), Point2d.of(6.0f, 6.0f), Point2d.of(6.0f, 7.0f), Point2d.of(6.0f, 8.0f), Point2d.of(6.0f, 9.0f))));
        muleLetterPosTable.put('I', new HashSet<>(Set.of(Point2d.of(3.0f, 1.0f), Point2d.of(3.0f, 2.0f), Point2d.of(3.0f, 3.0f), Point2d.of(3.0f, 4.0f), Point2d.of(3.0f, 5.0f), Point2d.of(3.0f, 6.0f), Point2d.of(3.0f, 7.0f), Point2d.of(3.0f, 9.0f), Point2d.of(3.0f, 8.0f))));

    }
    private static String[] messageOptions = {"GG", "EZ"}; //TODO: re-add HI
    public static List<Point2d> remainingMulePositions = new ArrayList<>();

    public static void onStep() {
        if (!doTrollMule || LocationConstants.muleLetterPosList.isEmpty()) {
            return;
        }

        //finish incomplete message
        if (!remainingMulePositions.isEmpty()) {
            writeMessage();
            return;
        }

        //start new message
        if (Time.nowFrames() > lastMuleMessageFrame + Time.toFrames(90) &&
                UnitUtils.numScansAvailable() >= MAX_MULES_REQUIRED) {
            setRandomMessage();
            writeMessage();
            Chat.tag("mule_message");
        }
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
            MuleMessages.doTrollMule = true;
        } else {
            MuleMessages.doTrollMule = false;
        }
    }

    private static void setRandomMessage() {
        message = messageOptions[(int)(Math.random()*messageOptions.length)].toCharArray();
    }

    public static void writeMessage() {
        List<UnitInPool> ocList = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_ORBITAL_COMMAND)
                .stream()
                .filter(u -> u.unit().getEnergy().orElse(0f) >= 50)
                .collect(Collectors.toList());

        //set all mule positions
        if (remainingMulePositions.isEmpty()) {
            setMessagePositions(message, ocList);
            if (remainingMulePositions.isEmpty()) {
                return;
            }
        }

        //scan first
        if (Bot.OBS.getEffects().stream()
                .noneMatch(e -> e.getEffect() == Effects.SCANNER_SWEEP &&
                        e.getPositions().iterator().next().distance(LocationConstants.muleLetterPosList.get(0)) < 10)) {
            scanLetterPositions(message, ocList);
            return;
        }

        //calldown mules
        while (!remainingMulePositions.isEmpty() && !ocList.isEmpty()) {
            ActionHelper.unitCommand(ocList.remove(0).unit(), Abilities.EFFECT_CALL_DOWN_MULE, remainingMulePositions.remove(0), false);
        }

        //finished message
        if (remainingMulePositions.isEmpty()) {
            lastMuleMessageFrame = Time.nowFrames();
        }
    }

    private static void scanLetterPositions(char[] message, List<UnitInPool> ocList) {
        for (int i=0; i<message.length; i++) {
            ActionHelper.unitCommand(ocList.get(i).unit(), Abilities.EFFECT_SCAN,
                    LocationConstants.muleLetterPosList.get(i).add(Point2d.of(3f, 5f)), false);
        }
    }

    private static void setMessagePositions(char[] message, List<UnitInPool> ocList) {
        int numMulesAvailable = ocList.stream()
                .mapToInt(oc -> (int)(oc.unit().getEnergy().orElse(1f) / 50))
                .sum();
        for (int i=0; i<message.length; i++) {
            char letter = message[i];
            Set<Point2d> mulePosList = muleLetterPosTable.get(letter);
            Point2d botCornerPos = LocationConstants.muleLetterPosList.get(i);
            for (Point2d mulePos : mulePosList) {
                remainingMulePositions.add(botCornerPos.add(mulePos));
            }
        }
        if (remainingMulePositions.size() > numMulesAvailable) {
            remainingMulePositions.clear();
        }
    }
}
