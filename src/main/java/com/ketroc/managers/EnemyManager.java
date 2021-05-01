package com.ketroc.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.utils.Chat;
import com.ketroc.utils.LocationConstants;
import com.ketroc.utils.UnitUtils;

import java.util.List;

public class EnemyManager {
    public static void onStep() {
        realPhoenixCheck();
    }

    //TODO: check for damage dealt
    //checks for 3+ phoenix, detected phoenix, and graviton beam use
    private static void realPhoenixCheck() {
        if (LocationConstants.opponentRace == Race.PROTOSS && !Switches.phoenixAreReal) {
            //find any phoenix in range of my detection
            List<UnitInPool> enemyPhoenixList = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> enemy.unit().getType() == Units.PROTOSS_PHOENIX);

            if (enemyPhoenixList.size() >= 3 ||
                    enemyPhoenixList.stream()
                            .anyMatch(enemy -> UnitUtils.isEnemyEnteringDetection(enemy.unit()) &&
                                    !enemy.unit().getHallucination().orElse(true)) ||
                    !Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getBuffs().contains(Buffs.GRAVITON_BEAM)).isEmpty()
            ) {
                Switches.phoenixAreReal = true;
                UnitUtils.EVIDENCE_OF_AIR.add(Units.PROTOSS_PHOENIX);
                Chat.chat("Just realizing now that these phoenix aren't hallucinations");
            }
        }
    }
}
