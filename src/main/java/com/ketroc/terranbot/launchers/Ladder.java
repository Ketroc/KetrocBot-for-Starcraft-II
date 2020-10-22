package com.ketroc.terranbot.launchers;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.protocol.game.Race;
import com.ketroc.terranbot.bots.KetrocBot;
import com.ketroc.terranbot.bots.Bot;

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

public class Ladder {
    public static void main(String[] args) {
        boolean realTime = false;
        String opponentId = null;
        for (int i=0; i<args.length; i++) {
            String arg = args[i];
            if (arg.equals("--RealTime")) {
                realTime = true;
                break;
            }
            if (arg.contains("--OpponentId")) {
                opponentId = args[i+1];
            }
        }
        Bot bot = new KetrocBot(false, opponentId, realTime);
        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .setTimeoutMS(300000) //5min
                .setRawAffectsSelection(false)
                .loadLadderSettings(args)
                .setShowCloaked(true)
                .setShowBurrowed(true)
                .setParticipants(S2Coordinator.createParticipant(Race.TERRAN, bot))
                .connectToLadder()
                .joinGame();

        while (s2Coordinator.update()) {

        }
        s2Coordinator.quit();
    }
}