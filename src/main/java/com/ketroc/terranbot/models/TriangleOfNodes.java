package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;

import java.util.List;

public class TriangleOfNodes {
    public UnitInPool inner;
    public UnitInPool outer1;
    public UnitInPool outer2;

    public TriangleOfNodes(Point2d innerPos) {
        updateNodes(innerPos);
    }

    public boolean hasSnapshots() { //TODO: not needed??
        return (inner.unit().getDisplayType() == DisplayType.SNAPSHOT ||
                outer1.unit().getDisplayType() == DisplayType.SNAPSHOT ||
                outer2.unit().getDisplayType() == DisplayType.SNAPSHOT);
    }

    public boolean requiresUpdate() {
        return (inner == null || outer1 == null || outer2 == null ||
                inner.getLastSeenGameLoop() != Bot.OBS.getGameLoop() ||
                outer1.getLastSeenGameLoop() != Bot.OBS.getGameLoop() ||
                outer2.getLastSeenGameLoop() != Bot.OBS.getGameLoop());
    }

    public void updateNodes(Point2d innerPos) {
        if (requiresUpdate()) {
            inner = null; outer1 = null; outer2 = null;
            //find mineral nodes
            List<UnitInPool> mineralNodes = Bot.OBS.getUnits(Alliance.NEUTRAL,
                    node -> UnitUtils.MINERAL_NODE_TYPE.contains(node.unit().getType()) && node.unit().getPosition().toPoint2d().distance(innerPos) < 2.5);
            for (UnitInPool node : mineralNodes) {
                if (innerPos.distance(node.unit().getPosition().toPoint2d()) < 0.5) {
                    inner = node;
                }
                else if (outer1 == null) {
                    outer1 = node;
                }
                else {
                    outer2 = node;
                }
            }
        }
    }


}
