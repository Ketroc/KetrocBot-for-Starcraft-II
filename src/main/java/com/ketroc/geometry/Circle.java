package com.ketroc.geometry;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;

import java.util.Collections;
import java.util.Set;

public class Circle {
    private Point2d center;
    private float radius;

    public Circle(Point2d center, float radius) {
        this.center = center;
        this.radius = radius;
    }

    public Circle(UnitInPool uip, float radius) {
        this(uip.unit(), radius);
    }

    public Circle(Unit unit, float radius) {
        this(unit.getPosition().toPoint2d(), radius);
    }

    public Circle(float x, float y, float radius) {
        this(Point2d.of(x, y), radius);
    }

    public Set<Point2d> intersection(Circle c2) {
        float d = (float)center.distance(c2.center);
        if (d > radius + c2.radius) { //not intersecting
            return Collections.emptySet();
        }

        float x0 = center.getX();
        float x1 = c2.center.getX();
        float y0 = center.getY();
        float y1 = c2.center.getY();
        float r0 = radius;
        float r1 = c2.radius;

        float a = (r0*r0 - r1*r1 + d*d) / (2*d);
        float h = (float)Math.sqrt(r0*r0 - a*a);
        float x2 = x0 + a*(x1-x0)/d;
        float y2 = y0 + a*(y1-y0)/d;
        float x3 = x2 + h*(y1-y0)/d;
        float y3 = y2 - h*(x1-x0)/d;
        float x4 = x2 - h*(y1-y0)/d;
        float y4 = y2 + h*(x1-x0)/d;

        return Set.of(Point2d.of(x3, y3), Point2d.of(x4, y4));
    }
}
