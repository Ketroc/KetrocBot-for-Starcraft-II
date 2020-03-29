package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.protocol.game.BattlenetMap;
import com.github.ocraft.s2client.protocol.game.Difficulty;
import com.github.ocraft.s2client.protocol.game.LocalMap;
import com.github.ocraft.s2client.protocol.game.Race;

import java.nio.file.Paths;

/*
onBuildingConstructionComplete(UnitInPool unitInPool)
onStep()
onUnitCreated(UnitInPool unitInPool)
onUnitIdle(UnitInPool unitInPool)
onAlert(Alert alert)

onUpgradeCompleted(Upgrade upgrade)
onUnitEnterVision(UnitInPool unitInPool)
onUnitDestroyed(UnitInPool unitInPool)
onError(java.util.List<ClientError> clientErrors, java.util.List<java.lang.String> protocolErrors)
onGameEnd()
onGameFullStart()
onGameStart()
onNuclearLaunchDetected()
onNydusDetected()


19 = (145.0, 50.0) depot
21 = (145.5, 59.5) barracks
18 = (133.5, 43.5) cc
24 = (128.5, 51.5) bunker
19 = (145.0, 52.0) depot
24 = (142.5, 57.5) bunker
20 = (167.5, 49.5) gas
20 = (156.5, 39.5) gas
20 = (126.5, 40.5) gas
 */

public class Ketroc {
    public static void main(String[] args) {
        Bot bot = new Bot();
        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .loadSettings(args)
                .setRealtime(false)
                .setWindowLocation(900, 0)
                .setNeedsSupportDir(true)
                .setShowCloaked(true)
                .setRawAffectsSelection(true)
                .setTimeoutMS(600 * 1000)
                //.setProcessPath(Paths.get("C:\\Ladder\\4.8.4\\StarCraft II\\Versions\\Base73286\\SC2_x64.exe"))
                .setParticipants(
                        S2Coordinator.createParticipant(Race.TERRAN, bot),
                        S2Coordinator.createComputer(Race.TERRAN, Difficulty.CHEAT_INSANE))
                .launchStarcraft()
                .startGame(LocalMap.of(Paths.get("TritonLE.SC2Map")));

//        S2Coordinator s2Coordinator = S2Coordinator.setup()
//                .setRawAffectsSelection(true)
//                .loadLadderSettings(args)
//                .setParticipants(S2Coordinator.createParticipant(Race.TERRAN, bot))
//                .connectToLadder()
//                .joinGame();

        while (s2Coordinator.update()) {

        }
        s2Coordinator.quit();
    }
}