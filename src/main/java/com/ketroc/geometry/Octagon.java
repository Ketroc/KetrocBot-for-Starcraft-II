package com.ketroc.geometry;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.utils.DebugHelper;

import java.util.*;

public class Octagon {
    private static final float RADIUS_TO_LINE_LENGTH_RATIO = 0.828427f;
    private Point2d centerPos;
    private float radius;
    List<Line> lines;

    public Octagon(Point2d centerPos, float radius) {
        this.centerPos = centerPos;
        this.radius = radius;

        float halflineLength = radius / 2.5f; //for command structure.  for regular octagon, it's: radius * RADIUS_TO_LINE_LENGTH_RATIO / 2;
        float x = centerPos.getX();
        float y = centerPos.getY();

        //get Octagon corner points
        Point2d topLeft = Point2d.of(x-halflineLength, y+radius);
        Point2d topRight = Point2d.of(x+halflineLength, y+radius);
        Point2d botLeft = Point2d.of(x-halflineLength, y-radius);
        Point2d botRight = Point2d.of(x+halflineLength, y-radius);
        Point2d leftTop = Point2d.of(x-radius, y+halflineLength);
        Point2d leftBot = Point2d.of(x-radius, y-halflineLength);
        Point2d rightTop = Point2d.of(x+radius, y+halflineLength);
        Point2d rightBot = Point2d.of(x+radius, y-halflineLength);

        lines = new ArrayList<>();
        lines.add(new Line(topLeft, topRight));
        lines.add(new Line(topRight, rightTop));
        lines.add(new Line(rightTop, rightBot));
        lines.add(new Line(rightBot, botRight));
        lines.add(new Line(botRight, botLeft));
        lines.add(new Line(botLeft, leftBot));
        lines.add(new Line(leftBot, leftTop));
        lines.add(new Line(leftTop, topLeft));
    }

    public Octagon(UnitInPool commandStructure) {
        this(commandStructure.unit());
    }

    public Octagon(Unit commandStructure) {
        this(commandStructure.getPosition().toPoint2d());
    }

    public Octagon(Point2d commandStructurePos) {
        this(commandStructurePos, 3.075f);
    }

    public Set<Point2d> intersection(Line line) {
        Set<Point2d> intersectionPoints = new HashSet<>();
        lines.forEach(octLine -> octLine.intersection(line).ifPresent(p -> intersectionPoints.add(p)));
        return intersectionPoints;
    }

    public void draw(Color color) {
        lines.forEach(line -> DebugHelper.drawLine(line.getP1(), line.getP2(), color));
    }
}
