package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point;

public class Gas {
    private UnitInPool geyser;
    private UnitInPool refinery;
    private Point location;

    // ========= CONSTRUCTORS ===========

    public Gas(UnitInPool geyser) {
        this.geyser = geyser;
        this.location = geyser.unit().getPosition();
    }

    // ========= GETTERS AND SETTERS =========

    public UnitInPool getGeyser() {
        return geyser;
    }

    public void setGeyser(UnitInPool geyser) {
        this.geyser = geyser;
    }

    public UnitInPool getRefinery() {
        return refinery;
    }

    public void setRefinery(UnitInPool refinery) {
        this.refinery = refinery;
    }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
    }
}
