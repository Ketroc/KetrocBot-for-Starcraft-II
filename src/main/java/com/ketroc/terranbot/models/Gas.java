package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.UnitUtils;

public class Gas {
    private Unit geyser;
    private Unit refinery;
    private Point2d location;

    // ========= CONSTRUCTORS ===========

    public Gas(Unit geyser) {
        this.geyser = geyser;
        this.location = geyser.getPosition().toPoint2d();
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

    public Point2d getLocation() {
        return location;
    }

    public void setLocation(Point2d location) {
        this.location = location;
    }


    // ============= METHODS ==============

    public boolean isAvailable() {
        return refinery == null &&
                geyser.getVespeneContents().orElse(0) > Strategy.MIN_GAS_FOR_REFINERY &&
                UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.GAS_STRUCTURE_TYPES, location, 1).isEmpty();
    }

}
