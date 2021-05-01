package com.ketroc.utils;

import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.bots.Bot;
import com.ketroc.strategies.Strategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ActionIssued {
    public static Map<Tag, ActionIssued> lastActionIssued = new HashMap<>();

    public Ability ability;
    public Tag targetTag;
    public Point2d targetPos;
    public long gameFrame;

    public ActionIssued(Ability ability, Tag targetTag, Point2d targetPos) {
        this.ability = ability;
        this.targetTag = targetTag;
        this.targetPos = targetPos;
        gameFrame = Time.nowFrames();
    }

    public static void add(Tag unitTag, Ability ability) {
        lastActionIssued.put(unitTag, new ActionIssued(ability, null, null));
    }

    public static void add(Tag unitTag, Ability ability, Unit target) {
        lastActionIssued.put(unitTag, new ActionIssued(ability, target.getTag(), null));
    }

    public static void add(Tag unitTag, Ability ability, Point2d targetPos) {
        lastActionIssued.put(unitTag, new ActionIssued(ability, null, targetPos));
    }

    public static void onStep() {
        //only save actions for 1 played step
        if (Bot.isRealTime) {
            lastActionIssued.entrySet().removeIf(entrySet -> entrySet.getValue() == null || entrySet.getValue().gameFrame + Strategy.STEP_SIZE < Time.nowFrames());
        }
    }

    public static Optional<ActionIssued> getCurOrder(Unit unit) {
        Tag unitTag = unit.getTag();
        if (Bot.isRealTime) {
            ActionIssued lastAction = lastActionIssued.get(unitTag);
            if (lastAction != null) {
                return Optional.of(lastAction);
            }
        }
        if (!unit.getOrders().isEmpty()) {
            UnitOrder order = unit.getOrders().get(0);
            return Optional.of(new ActionIssued(
                    order.getAbility(),
                    order.getTargetedUnitTag().orElse(null),
                    order.getTargetedWorldSpacePosition().map(p -> p.toPoint2d()).orElse(null)));
        }
        return Optional.empty();
    }
}
