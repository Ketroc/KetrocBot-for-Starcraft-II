package com.ketroc.utils;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.bots.Bot;
import com.ketroc.launchers.Launcher;
import com.ketroc.models.Cost;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ActionIssued { //TODO: handle queued commands
    public static Map<UnitInPool, ActionIssued> lastActionIssued = new HashMap<>();

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
        UnitInPool uip = Bot.OBS.getUnit(unitTag);
        if (uip != null) {
            lastActionIssued.put(uip, new ActionIssued(ability, null, null));
        }
    }

    public static void add(Tag unitTag, Ability ability, Unit target) {
        UnitInPool uip = Bot.OBS.getUnit(unitTag);
        if (uip != null) {
            lastActionIssued.put(Bot.OBS.getUnit(unitTag), new ActionIssued(ability, target.getTag(), null));
        }
    }

    public static void add(Tag unitTag, Ability ability, Point2d targetPos) {
        UnitInPool uip = Bot.OBS.getUnit(unitTag);
        if (uip != null) {
            lastActionIssued.put(Bot.OBS.getUnit(unitTag), new ActionIssued(ability, null, targetPos));
        }
    }

    public static void onStep() {
        if (Launcher.isRealTime) {
            //remove cached actions after a small time period
            lastActionIssued.entrySet().removeIf(entrySet -> {
                int framesToCache = (entrySet.getValue().ability.toString().startsWith("BUILD_") ||
                        entrySet.getValue().ability.toString().startsWith("TRAIN_")) ? 12 : 6;
                return entrySet.getValue() == null || entrySet.getValue().gameFrame + framesToCache < Time.nowFrames();
            });


            //update bank for training/building that has reached unit orders yet
            updateDelayedOrders();
        }
    }

    public static Optional<ActionIssued> getCurOrder(Unit unit) {
        return getCurOrder(Bot.OBS.getUnit(unit.getTag()));
    }

    public static Optional<ActionIssued> getCurOrder(UnitInPool uip) {
        if (uip == null) {
            return Optional.empty();
        }
        if (Launcher.isRealTime) {
            ActionIssued lastAction = lastActionIssued.get(uip);
            if (lastAction != null) {
                return Optional.of(lastAction);
            }
        }
        if (!uip.unit().getOrders().isEmpty()) {
            UnitOrder order = uip.unit().getOrders().get(0);
            return Optional.of(new ActionIssued(
                    order.getAbility(),
                    order.getTargetedUnitTag().orElse(null),
                    order.getTargetedWorldSpacePosition().map(p -> p.toPoint2d()).orElse(null)));
        }
        return Optional.empty();
    }

    public static void updateDelayedOrders() {
        for (Map.Entry<UnitInPool,ActionIssued> entry : lastActionIssued.entrySet()) {
            UnitInPool uip = entry.getKey();
            ActionIssued action = entry.getValue();

            //ignore dead units
            if (!uip.isAlive()) continue;

            //update bank for train/build purchases that aren't in unit orders yet
            if (orderNotInUnitOrdersYet(uip.unit().getOrders(), action, "BUILD_") ||
                    orderNotInUnitOrdersYet(uip.unit().getOrders(), action, "TRAIN_")) {
                Cost.updateBank(action.ability);
            }
        };
    }

    private static boolean orderNotInUnitOrdersYet(List<UnitOrder> orders, ActionIssued action, String abilityStartsWith) {
        return action.ability.toString().contains(abilityStartsWith) &&
                orders.stream().noneMatch(unitOrder -> unitOrder.getAbility().toString().startsWith(abilityStartsWith));
    }


}
