package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.*;
import java.util.stream.Collectors;

public class MuleMessages {
    private static final Map<Character, Set<Point2d>> muleLetterPosTable = new Hashtable<>();
    private static char[] message;
    public static boolean doTrollMule;
    private static long lastMuleMessageFrame;
    static {
        muleLetterPosTable.put('E', new HashSet<>(Set.of(Point2d.of(0.0f, 2.0f), Point2d.of(0.0f, 3.0f), Point2d.of(0.0f, 4.0f), Point2d.of(0.0f, 6.0f), Point2d.of(0.0f, 5.0f), Point2d.of(0.0f, 1.0f), Point2d.of(0.0f, 8.0f), Point2d.of(0.0f, 9.0f), Point2d.of(0.0f, 7.0f), Point2d.of(4.0f, 1.0f), Point2d.of(2.0f, 1.0f), Point2d.of(5.0f, 1.0f), Point2d.of(1.0f, 1.0f), Point2d.of(3.0f, 1.0f), Point2d.of(6.0f, 1.0f), Point2d.of(1.0f, 9.0f), Point2d.of(2.0f, 9.0f), Point2d.of(3.0f, 9.0f), Point2d.of(4.0f, 9.0f), Point2d.of(6.0f, 9.0f), Point2d.of(5.0f, 9.0f), Point2d.of(1.0f, 5.0f), Point2d.of(2.0f, 5.0f), Point2d.of(3.0f, 5.0f), Point2d.of(4.0f, 5.0f), Point2d.of(5.0f, 5.0f))));
        muleLetterPosTable.put('G', new HashSet<>(Set.of(Point2d.of(1.0f, 1.0f), Point2d.of(0.0f, 2.0f), Point2d.of(0.0f, 4.0f), Point2d.of(0.0f, 3.0f), Point2d.of(1.0f, 7.0f), Point2d.of(0.0f, 6.0f), Point2d.of(0.0f, 5.0f), Point2d.of(4.0f, 8.0f), Point2d.of(2.0f, 8.0f), Point2d.of(6.0f, 7.0f), Point2d.of(3.0f, 8.0f), Point2d.of(5.0f, 8.0f), Point2d.of(5.0f, 1.0f), Point2d.of(2.0f, 0.0f), Point2d.of(3.0f, 0.0f), Point2d.of(6.0f, 3.0f), Point2d.of(6.0f, 2.0f), Point2d.of(4.0f, 0.0f), Point2d.of(4.0f, 4.0f), Point2d.of(5.0f, 4.0f), Point2d.of(6.0f, 4.0f))));
        muleLetterPosTable.put('H', new HashSet<>(Set.of(Point2d.of(0.0f, 1.0f), Point2d.of(0.0f, 2.0f), Point2d.of(0.0f, 3.0f), Point2d.of(0.0f, 5.0f), Point2d.of(0.0f, 4.0f), Point2d.of(0.0f, 6.0f), Point2d.of(0.0f, 7.0f), Point2d.of(0.0f, 8.0f), Point2d.of(1.0f, 5.0f), Point2d.of(0.0f, 9.0f), Point2d.of(2.0f, 5.0f), Point2d.of(4.0f, 5.0f), Point2d.of(3.0f, 5.0f), Point2d.of(5.0f, 5.0f), Point2d.of(6.0f, 5.0f), Point2d.of(6.0f, 3.0f), Point2d.of(6.0f, 2.0f), Point2d.of(6.0f, 4.0f), Point2d.of(6.0f, 1.0f), Point2d.of(6.0f, 6.0f), Point2d.of(6.0f, 7.0f), Point2d.of(6.0f, 8.0f), Point2d.of(6.0f, 9.0f))));
        muleLetterPosTable.put('I', new HashSet<>(Set.of(Point2d.of(3.0f, 1.0f), Point2d.of(3.0f, 2.0f), Point2d.of(3.0f, 3.0f), Point2d.of(3.0f, 4.0f), Point2d.of(3.0f, 5.0f), Point2d.of(3.0f, 6.0f), Point2d.of(3.0f, 7.0f), Point2d.of(3.0f, 9.0f), Point2d.of(3.0f, 8.0f))));
        muleLetterPosTable.put('Z', new HashSet<>(Set.of(Point2d.of(0.0f, 1.0f), Point2d.of(2.0f, 1.0f), Point2d.of(3.0f, 1.0f), Point2d.of(1.0f, 1.0f), Point2d.of(5.0f, 1.0f), Point2d.of(4.0f, 1.0f), Point2d.of(6.0f, 1.0f), Point2d.of(1.0f, 3.0f), Point2d.of(0.0f, 2.0f), Point2d.of(2.0f, 4.0f), Point2d.of(3.0f, 5.0f), Point2d.of(4.0f, 6.0f), Point2d.of(6.0f, 8.0f), Point2d.of(5.0f, 7.0f), Point2d.of(5.0f, 9.0f), Point2d.of(6.0f, 9.0f), Point2d.of(4.0f, 9.0f), Point2d.of(2.0f, 9.0f), Point2d.of(3.0f, 9.0f), Point2d.of(0.0f, 9.0f), Point2d.of(1.0f, 9.0f))));
    }
    private static String[] messageOptions = {"GG", "EZ", "HI"};
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

        //new message
        if (lastMuleMessageFrame + Time.toFrames(90) < Time.nowFrames() &&
                GameCache.ccList.stream().anyMatch(cc -> cc.getEnergy().orElse(0f) > 199)) {
            setRandomMessage();
            writeMessage();
        }
    }

    private static void setRandomMessage() {
        message = messageOptions[(int)(Math.random()*3)].toCharArray();
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
            Bot.ACTION.unitCommand(ocList.remove(0).unit(), Abilities.EFFECT_CALL_DOWN_MULE, remainingMulePositions.remove(0), false);
        }

        //finished message
        if (remainingMulePositions.isEmpty()) {
            lastMuleMessageFrame = Time.nowFrames();
        }
    }

    private static void scanLetterPositions(char[] message, List<UnitInPool> ocList) {
        for (int i=0; i<message.length; i++) {
            Bot.ACTION.unitCommand(ocList.get(i).unit(), Abilities.EFFECT_SCAN,
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
