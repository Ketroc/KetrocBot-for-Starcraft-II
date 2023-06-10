package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.data.Buff;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.utils.UnitUtils;

import java.util.List;

public class BasicUnit {
    UnitInPool uip;

    public BasicUnit(UnitInPool uip) {
        this.uip = uip;
    }

    public boolean hasBuff(Buff buff) {
        return uip.unit().getBuffs().contains(buff);
    }

    public boolean hasOrder(Ability ability) {
        return UnitUtils.hasOrder(uip.unit(), ability);
    }

    public float distance(Point2d point2d) {
        return (float)uip.unit().getPosition().toPoint2d().distance(point2d);
    }

    public float distance(Unit unit) {
        return distance(unit.getPosition().toPoint2d());
    }

    public float distance(UnitInPool unit) {
        return distance(unit.unit().getPosition().toPoint2d());
    }

    public boolean isAlive() {
        return uip.isAlive();
    }

    public List<UnitOrder> getOrders() {
        return uip.unit().getOrders();
    }
}
