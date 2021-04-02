package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.ArrayList;
import java.util.List;

public class Gas {
    private Unit geyser;
    private Unit refinery;
    private List<UnitInPool> scvs = new ArrayList<>();
    private Point2d gasPos;
    private Point2d ccPos;
    private Point2d byMineral;
    private float distanceToHarvest = 1.38f + (Strategy.STEP_SIZE > 2 ? 0.5f : 0);
    private float distanceToCC = 3f + (Strategy.STEP_SIZE > 2 ? 0.5f : 0);
    private Point2d byCC;


    // ========= CONSTRUCTORS ===========

    public Gas(Unit geyser, Point2d ccPos) {
        this.geyser = geyser;
        this.ccPos = ccPos;
        this.gasPos = geyser.getPosition().toPoint2d();
    }

    // ========= GETTERS AND SETTERS =========

    public Unit getGeyser() {
        return geyser;
    }

    public void setGeyser(Unit geyser) {
        this.geyser = geyser;
    }

    public Unit getRefinery() {
        return refinery;
    }

    public void setRefinery(Unit refinery) {
        this.refinery = refinery;
    }

    public Point2d getGasPos() {
        return gasPos;
    }

    public void setGasPos(Point2d gasPos) {
        this.gasPos = gasPos;
    }

    public List<UnitInPool> getScvs() {
        return scvs;
    }

    public void setScvs(List<UnitInPool> scvs) {
        this.scvs = scvs;
    }

    // ============= METHODS ==============

    public boolean isAvailable() {
        return refinery == null &&
                geyser.getVespeneContents().orElse(0) > Strategy.MIN_GAS_FOR_REFINERY &&
                UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.GAS_STRUCTURE_TYPES, gasPos, 1).isEmpty();
    }

}
