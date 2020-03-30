import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.game.*;
import com.github.ocraft.s2client.protocol.unit.Unit;

import java.nio.file.Paths;

public class TestingBot {

    public static void main(String[] args) {
        Bot bot = new Bot();
        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .loadSettings(args)
                .setRealtime(true)
                .setParticipants(
                        S2Coordinator.createParticipant(Race.TERRAN, bot),
                        S2Coordinator.createComputer(Race.TERRAN, Difficulty.VERY_EASY, AiBuild.MACRO))
                .launchStarcraft()
                //.startGame(BattlenetMap.of("Triton LE"));
                .startGame(LocalMap.of(Paths.get("TritonLE.SC2Map")));

        while (s2Coordinator.update()) {
        }

        s2Coordinator.quit();
    }

    private static class Bot extends S2Agent {
        public boolean afterTime(String time) {
            long seconds = convertStringToSeconds(time);
            return observation().getGameLoop() / 22.4 > seconds;
        }

        boolean beforeTime(String time) {
            long seconds = convertStringToSeconds(time);
            return observation().getGameLoop() / 22.4 < seconds;
        }

        long convertStringToSeconds(String time) {
            String[] arrTime = time.split(":");
            return Integer.parseInt(arrTime[0]) * 60 + Integer.parseInt(arrTime[1]);
        }

        String convertGameLoopToStringTime(long gameLoop) {
            return convertSecondsToString(Math.round(gameLoop / 22.4));
        }

        String convertSecondsToString(long seconds) {
            return seconds / 60 + ":" + String.format("%02d", seconds % 60);
        }

        String currentGameTime() {
            return convertGameLoopToStringTime(observation().getGameLoop());
        }

        @Override
        public void onGameStart() {
        }

        @Override
        public void onUnitCreated(UnitInPool unitInPool) {
        }

        @Override
        public void onBuildingConstructionComplete(UnitInPool unitInPool) {
            Unit unit = unitInPool.unit();
            System.out.println(unit.getType() + ".add(Point2d.of(" + unit.getPosition().getX() + "f, " + unit.getPosition().getY() +
                    "f));");
        }

        @Override
        public void onStep() {
        } // end onstep() method

        @Override
        public void onUnitIdle(UnitInPool unitInPool) {
        }
    }
}