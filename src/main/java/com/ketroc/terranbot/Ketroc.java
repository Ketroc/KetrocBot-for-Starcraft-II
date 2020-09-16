package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.protocol.game.*;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.bots.TestingBot;


import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Ketroc {
    public static void main(String[] args) {
        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .loadSettings(args)
//                .setRealtime(true)
                .setWindowLocation(900, 0)
                .setNeedsSupportDir(true)
                .setShowCloaked(true)
                .setShowBurrowed(true)
                .setRawAffectsSelection(false)
                .setTimeoutMS(600 * 1000)
                //.setProcessPath(Paths.get("C:\\Ladder\\4.8.4\\StarCraft II\\Versions\\Base73286\\SC2_x64.exe"))
                .setParticipants(
                        S2Coordinator.createParticipant(Race.TERRAN, new Bot(true, false)),
                        S2Coordinator.createComputer(Race.PROTOSS, Difficulty.CHEAT_INSANE))
//                        S2Coordinator.createParticipant(Race.TERRAN, new TestingBot()),
//                        S2Coordinator.createComputer(Race.TERRAN, Difficulty.VERY_EASY, AiBuild.MACRO))
                .launchStarcraft()
//                .startGame(LocalMap.of(Paths.get("AcropolisLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("DeathAuraLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("DiscoBloodbathLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("Ephemeron.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("EphemeronLE.SC2Map")));
        //                .startGame(LocalMap.of(Paths.get("EternalEmpireLE.SC2Map")));
        //                .startGame(LocalMap.of(Paths.get("EverDreamLE.SC2Map")));
        //                .startGame(LocalMap.of(Paths.get("GoldenWallLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("IceandChromeLE.SC2Map")));
        //                .startGame(LocalMap.of(Paths.get("NightshadeLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("PillarsOfGoldLE.SC2Map")));
        //                .startGame(LocalMap.of(Paths.get("SimulacrumLE.SC2Map")));
                        .startGame(LocalMap.of(Paths.get("ThunderbirdLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("TritonLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("WintersGateLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("WorldofSleepersLE.SC2Map")));
        //                .startGame(LocalMap.of(Paths.get("ZenLE.SC2Map")));

        while (s2Coordinator.update()) {

        }
        s2Coordinator.quit();

    }

}