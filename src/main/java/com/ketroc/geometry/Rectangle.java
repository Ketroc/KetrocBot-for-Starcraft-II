package com.ketroc.geometry;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.utils.DebugHelper;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Rectangle {
    private float top;
    private float bottom;
    private float left;
    private float right;

    public Rectangle(float top, float bottom, float left, float right) {
        this.top = top;
        this.bottom = bottom;
        this.left = left;
        this.right = right;
    }

    public Rectangle(Point2d mineralPos) {
        this (mineralPos.getY()+0.975f, mineralPos.getY()-0.975f,
                mineralPos.getX()-1.475f, mineralPos.getX()+1.475f);
    }

    public Rectangle(Point2d squareCenterPos, float radius) {
        this (squareCenterPos.getY()+radius, squareCenterPos.getY()-radius,
                squareCenterPos.getX()-radius, squareCenterPos.getX()+radius);
    }

    public Rectangle(UnitInPool mineral) {
        this(mineral.unit());
    }

    public Rectangle(Unit mineral) {
        this(mineral.getPosition().toPoint2d());
    }

    public float getTop() {
        return top;
    }

    public void setTop(float top) {
        this.top = top;
    }

    public float getBottom() {
        return bottom;
    }

    public void setBottom(float bottom) {
        this.bottom = bottom;
    }

    public float getLeft() {
        return left;
    }

    public void setLeft(float left) {
        this.left = left;
    }

    public float getRight() {
        return right;
    }

    public void setRight(float right) {
        this.right = right;
    }

    public boolean contains(Point2d p) {
        return top > p.getY() && bottom < p.getY() && left < p.getX() && right > p.getX();
    }

    public Set<Point2d> intersection(Rectangle r2) {
        if (top < r2.bottom || bottom > r2.top || left > r2.right || right < r2.left) {
            return Collections.emptySet();
        }

        float x1, x2, y1, y2;
        if (top < r2.top && top > r2.bottom) {
            y1 = top;
            y2 = r2.bottom;
        }
        else {
            y1 = bottom;
            y2 = r2.top;
        }
        if (left > r2.left && left < r2.right) {
            x1 = left;
            x2 = r2.right;
        }
        else {
            x1 = right;
            x2 = r2.left;
        }

        return Set.of(Point2d.of(x1, y2), Point2d.of(x2, y1));
    }

    public Set<Point2d> intersection(Line line) {
        Set<Point2d> intersectionPoints = new HashSet<>();
        Line rectLineTop = new Line(left, top, right, top);
        Line rectLineBottom = new Line(left, bottom, right, bottom);
        Line rectLineLeft = new Line(left, bottom, left, top);
        Line rectLineRight = new Line(right, bottom, right, top);
        rectLineTop.intersection(line).ifPresent(p -> intersectionPoints.add(p));
        rectLineBottom.intersection(line).ifPresent(p -> intersectionPoints.add(p));
        rectLineLeft.intersection(line).ifPresent(p -> intersectionPoints.add(p));
        rectLineRight.intersection(line).ifPresent(p -> intersectionPoints.add(p));
        return intersectionPoints;
    }

    public void draw(Color color) {
        DebugHelper.drawRect(top, bottom, left, right, color);
    }
}
