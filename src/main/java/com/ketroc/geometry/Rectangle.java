package com.ketroc.geometry;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.utils.DebugHelper;

import java.util.*;

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

    public Rectangle(Point2d squareCenterPos, float radius) {
        this (squareCenterPos.getY()+radius, squareCenterPos.getY()-radius,
                squareCenterPos.getX()-radius, squareCenterPos.getX()+radius);
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

    public List<Line> getLines() {
        return List.of(new Line(Point2d.of(left, top), Point2d.of(right, top)),
                new Line(Point2d.of(right, top), Point2d.of(right, bottom)),
                new Line(Point2d.of(right, bottom), Point2d.of(left, bottom)),
                new Line(Point2d.of(left, bottom), Point2d.of(left, top)));
    }

    public Set<Point2d> intersection(Rectangle r2) {
        if (top < r2.bottom || bottom > r2.top || left > r2.right || right < r2.left) {
            return Collections.emptySet();
        }

        Set<Point2d> intersectPoints = new HashSet<>();
        r2.getLines().forEach(line -> intersectPoints.addAll(intersection(line)));
        return intersectPoints;
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
