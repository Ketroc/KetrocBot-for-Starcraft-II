package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.utils.UnitUtils;

import java.util.List;

public class TriangleOfNodes {
    private Point2d midPos;
    private UnitInPool middle;
    private UnitInPool inner;
    private UnitInPool outer;
    private Point2d clusterPos;

    public Point2d getClusterPos() {
        if (clusterPos == null) {
            updateNodes();
        }
        return clusterPos;
    }

    public void setClusterPos(Point2d clusterPos) {
        this.clusterPos = clusterPos;
    }


    public UnitInPool getMiddle() {
        if (middle == null) {
            updateNodes();
        }
        return middle;
    }

    public void setMiddle(UnitInPool middle) {
        this.middle = middle;
    }

    public UnitInPool getInner() {
        if (inner == null) {
            updateNodes();
        }
        return inner;
    }

    public void setInner(UnitInPool inner) {
        this.inner = inner;
    }

    public UnitInPool getOuter() {
        if (outer == null) {
            updateNodes();
        }

        return outer;
    }

    public void setOuter(UnitInPool outer) {
        this.outer = outer;
    }

    public TriangleOfNodes(Point2d midPos) {
        this.midPos = midPos;
    }

    public boolean hasSnapshots() { //TODO: not needed??
        return (getMiddle().unit().getDisplayType() == DisplayType.SNAPSHOT ||
                getInner().unit().getDisplayType() == DisplayType.SNAPSHOT ||
                getOuter().unit().getDisplayType() == DisplayType.SNAPSHOT);
    }

    public boolean requiresUpdate() {
        return (middle == null || inner == null || outer == null ||
                UnitUtils.isInFogOfWar(getMiddle()) || UnitUtils.isInFogOfWar(getInner()) || UnitUtils.isInFogOfWar(getOuter()));
    }

    public void updateNodes() {
        if (requiresUpdate()) {
            middle = null; inner = null; outer = null; clusterPos = null;
            //find mineral nodes
            List<UnitInPool> mineralNodes = Bot.OBS.getUnits(Alliance.NEUTRAL,
                    node -> UnitUtils.MINERAL_NODE_TYPE.contains(node.unit().getType()) && node.unit().getPosition().toPoint2d().distance(midPos) < 2.5);
            for (int i=0; i< mineralNodes.size(); i++) {
                if (UnitUtils.getDistance(mineralNodes.get(i).unit(), midPos) < 0.5) {
                    setMiddle(mineralNodes.remove(i));
                    break;
                }
            }
            UnitInPool node1 = mineralNodes.get(0);
            UnitInPool node2 = mineralNodes.get(1);
            Point2d midMineralLine = (UnitUtils.getDistance(node1.unit(), GameCache.baseList.get(0).getResourceMidPoint()) < 10) ?
                    GameCache.baseList.get(0).getResourceMidPoint() :
                    GameCache.baseList.get(GameCache.baseList.size()-1).getResourceMidPoint();
            if (UnitUtils.getDistance(node1.unit(), midMineralLine) < UnitUtils.getDistance(node2.unit(), midMineralLine)) {
                setInner(node1);
                setOuter(node2);
            }
            else {
                setInner(node2);
                setOuter(node1);
            }
            clusterPos = Position.midPoint(inner.unit().getPosition().toPoint2d(), outer.unit().getPosition().toPoint2d());
        }
    }

}
