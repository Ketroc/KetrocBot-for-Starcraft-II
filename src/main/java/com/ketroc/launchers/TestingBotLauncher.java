package com.ketroc.launchers;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.protocol.game.AiBuild;
import com.github.ocraft.s2client.protocol.game.Difficulty;
import com.github.ocraft.s2client.protocol.game.LocalMap;
import com.github.ocraft.s2client.protocol.game.Race;
import com.ketroc.bots.TestingBot;

import java.nio.file.Paths;

public class TestingBotLauncher {
    public static void main(String[] args) {
        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .loadSettings(args)
                .setRealtime(true)
                .setWindowLocation(900, 0)
                .setNeedsSupportDir(true)
                .setShowCloaked(true)
                .setShowBurrowed(true)
                .setRawAffectsSelection(true)
                .setTimeoutMS(600 * 1000)
//                .setProcessPath(Paths.get("C:\\Program Files (x86)\\StarCraft II\\Versions\\Base75689\\SC2_x64.exe"))
//                .setDataVersion("B89B5D6FA7CBF6452E721311BFBC6CB2")
                .setParticipants(
                        S2Coordinator.createParticipant(Race.TERRAN, new TestingBot(true, null,false)),
                        S2Coordinator.createComputer(Race.ZERG, Difficulty.VERY_EASY, AiBuild.MACRO))
//                        S2Coordinator.createComputer(Race.TERRAN, Difficulty.VERY_EASY, AiBuild.MACRO))
                .launchStarcraft()
//                .startGame(LocalMap.of(Paths.get("2000AtmospheresAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("BlackburnAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("JagannathaAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("LightshadeAIE.SC2Map")));
                .startGame(LocalMap.of(Paths.get("OxideAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("RomanticideAIE.SC2Map")));


//                .startGame(LocalMap.of(Paths.get("AcropolisLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("AscensiontoAiurLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("CatalystLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("DeathAuraLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("DiscoBloodbathLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("Ephemeron.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("EphemeronLE.SC2Map")));
//                        .startGame(LocalMap.of(Paths.get("EternalEmpireLE.SC2Map")));
        //                .startGame(LocalMap.of(Paths.get("EverDreamLE.SC2Map")));
        //                .startGame(LocalMap.of(Paths.get("GoldenWall506.SC2Map")));
        //                .startGame(LocalMap.of(Paths.get("IceandChromeLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("NightshadeLE.SC2Map")));
        //                .startGame(LocalMap.of(Paths.get("PillarsOfGoldLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("SimulacrumLE.SC2Map")));
        //                .startGame(LocalMap.of(Paths.get("SubmarineLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("ThunderbirdLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("TritonLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("WintersGateLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("WorldofSleepersLE.SC2Map")));
        //        .startGame(LocalMap.of(Paths.get("ZenLE.SC2Map")));

        while (s2Coordinator.update()) {

        }
        s2Coordinator.quit();

    }

}