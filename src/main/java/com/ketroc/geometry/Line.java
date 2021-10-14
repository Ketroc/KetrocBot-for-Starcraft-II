package com.ketroc.geometry;

import com.github.ocraft.s2client.protocol.spatial.Point2d;

import java.util.Optional;

public class Line {
    private Point2d p1;
    private Point2d p2;

    public Line(Point2d p1, Point2d p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    public Line(float p1X, float p1Y, float p2X, float p2Y) {
        this.p1 = Point2d.of(p1X, p1Y);
        this.p2 = Point2d.of(p2X, p2Y);
    }

    public Point2d getP1() {
        return p1;
    }

    public void setP1(Point2d p1) {
        this.p1 = p1;
    }

    public Point2d getP2() {
        return p2;
    }

    public void setP2(Point2d p2) {
        this.p2 = p2;
    }

    public Optional<Point2d> intersection(Line line2) {
        float s02_x, s02_y, s10_x, s10_y, s32_x, s32_y, s_numer, t_numer, denom, t;
        s10_x = p2.getX() - p1.getX();
        s10_y = p2.getY() - p1.getY();
        s32_x = line2.p2.getX() - line2.p1.getX();
        s32_y = line2.p2.getY() - line2.p1.getY();

        denom = s10_x * s32_y - s32_x * s10_y;
        if (denom == 0) {
            return Optional.empty(); // Collinear
        }
        boolean denomPositive = denom > 0;

        s02_x = p1.getX() - line2.p1.getX();
        s02_y = p1.getY() - line2.p1.getY();
        s_numer = s10_x * s02_y - s10_y * s02_x;
        if ((s_numer < 0) == denomPositive) {
            return Optional.empty(); // No collision
        }

        t_numer = s32_x * s02_y - s32_y * s02_x;
        if ((t_numer < 0) == denomPositive) {
            return Optional.empty(); // No collision
        }

        if (((s_numer > denom) == denomPositive) || ((t_numer > denom) == denomPositive)) {
            return Optional.empty(); // No collision
        }

        // Collision detected
        t = t_numer / denom;
        return Optional.of(Point2d.of(p1.getX() + (t * s10_x), p1.getY() + (t * s10_y)));
    }
}
