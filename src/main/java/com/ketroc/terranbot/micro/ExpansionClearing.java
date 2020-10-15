package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.UnitUtils;

public class ExpansionClearing {
    public Point2d expansionPos;
    private int defenseStep;
    public BasicAttacker raven;

    public ExpansionClearing(Point2d expansionPos, UnitInPool raven) {
        this.expansionPos = expansionPos;
        this.raven = new BasicAttacker(raven, expansionPos);
    }

    public void onStep() {
        if (UnitUtils.getDistance(raven.attacker.unit(), expansionPos) < 1) {

        }
    }
}
