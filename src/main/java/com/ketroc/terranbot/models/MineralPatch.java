package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.bots.TestingBot;
import com.ketroc.terranbot.utils.Position;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.ArrayList;
import java.util.List;

public class MineralPatch {
    private Unit unit;
    private List<UnitInPool> scvs = new ArrayList<>();
    private Point2d ccPos;
    private Point2d byMineral;
    private float distanceToHarvest = 1.38f;
    private Point2d mineralPos;
    private Point2d byCC;

    public MineralPatch(Unit mineralPatch, Point2d ccPos) {
        this.ccPos = ccPos;
        this.unit = mineralPatch;
        mineralPos = mineralPatch.getPosition().toPoint2d();
        byMineral = Position.towards(mineralPos, ccPos, 0.2f);
        byCC = Position.towards(ccPos, mineralPos, 2.15f);
        float angle = Position.getAngle(byCC, byMineral);
        if ((angle > 70 && angle < 120) || (angle > 240 && angle < 300)) { //mining angle is up or down
            byMineral = mineralPos;
            //byMineralTweak = -0.55f;
        }
        else if ((angle > 130 && angle < 230) || angle > 310 || angle < 50) { //mining angle is left or right or diagonal
            distanceToHarvest = 1.5f;
        }
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public List<UnitInPool> getScvs() {
        return scvs;
    }

    public void setScvs(List<UnitInPool> scvs) {
        this.scvs = scvs;
    }

    public Point2d getByMineral() {
        return byMineral;
    }

    public void setByMineral(Point2d byMineral) {
        this.byMineral = byMineral;
    }

    public Point2d getMineralPos() {
        return mineralPos;
    }

    public void setMineralPos(Point2d mineralPos) {
        this.mineralPos = mineralPos;
    }

    public Point2d getByCC() {
        return byCC;
    }

    public void setByCC(Point2d byCC) {
        this.byCC = byCC;
    }

    public void harvestMicro(Unit scv) {
        float distToPatch = UnitUtils.getDistance(scv, mineralPos);
        if (UnitUtils.getOrder(scv) == Abilities.HARVEST_GATHER) {
            //start speed MOVE
            if (distToPatch < 3.1f && distToPatch > distanceToHarvest) {
                Bot.ACTION.unitCommand(scv, Abilities.MOVE, byMineral, false);
            }
            //fix bounce
            else if (!unit.getTag().equals(UnitUtils.getTargetUnitTag(scv))) {
                Bot.ACTION.unitCommand(scv, Abilities.HARVEST_GATHER, unit, false);
            }
        }
        else if (UnitUtils.getOrder(scv) == Abilities.MOVE) {
            //end speed MOVE
            if (distToPatch <= distanceToHarvest) {
                Bot.ACTION.unitCommand(scv, Abilities.HARVEST_GATHER, unit, false);
            }
        }
        else {
            //put wayward scv back to work
            Bot.ACTION.unitCommand(scv, Abilities.HARVEST_GATHER, unit, false);

        }
    }

    public void distanceHarvestMicro(Unit scv) {
        if (UnitUtils.getOrder(scv) == Abilities.HARVEST_GATHER) {
            if (!unit.getTag().equals(UnitUtils.getTargetUnitTag(scv))) {
                Bot.ACTION.unitCommand(scv, Abilities.HARVEST_GATHER, unit, false);
            }
        }
        else {
            //put wayward scv back to work
            Bot.ACTION.unitCommand(scv, Abilities.HARVEST_GATHER, unit, false);
        }
    }

    public void returnMicro(Unit scv) {
        float distToCC = UnitUtils.getDistance(scv, ccPos);
        if (UnitUtils.getOrder(scv) == Abilities.HARVEST_RETURN) {
            //start speed MOVE
            if (distToCC < 4.7f && distToCC > 3f) {
                Bot.ACTION.unitCommand(scv, Abilities.MOVE, byCC, false);
            }
        }
        else if (UnitUtils.getOrder(scv) == Abilities.MOVE) {
            //end speed MOVE
            if (distToCC <= 3f) {
                Bot.ACTION.unitCommand(scv, Abilities.HARVEST_RETURN, false);
            }
        }
        //put wayward scv back to work
        else {
            Bot.ACTION.unitCommand(scv, Abilities.HARVEST_RETURN, false);
        }
    }

    public void distanceReturnMicro(Unit scv) {
        if (UnitUtils.getOrder(scv) != Abilities.HARVEST_RETURN) {
            Bot.ACTION.unitCommand(scv, Abilities.HARVEST_RETURN, false);
        }
    }

    public void updateUnit() {
        unit = Bot.OBS.getUnits(Alliance.NEUTRAL, u -> UnitUtils.getDistance(u.unit(), mineralPos) < 0.5f).stream()
                .map(UnitInPool::unit)
                .findFirst()
                .orElse(null);
        if (unit == null) {
            onMineralDeath();
        }
    }

    private void onMineralDeath() {
        if (!scvs.isEmpty()) {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvs), Abilities.STOP, false);
        }
    }
}
