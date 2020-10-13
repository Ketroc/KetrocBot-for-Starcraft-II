package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.unit.Tag;

import java.util.ArrayList;
import java.util.List;

public abstract class Ignored {
    public static List<Ignored> ignoredUnits = new ArrayList<>();
    public Tag unitTag;

    public Ignored(Tag unitTag) {
        this.unitTag = unitTag;
    }

    public abstract boolean doReleaseUnit();

    // *********** STATIC METHODS *************
    public static void remove(Tag unitToRemove) {
        for (int i=0; i<ignoredUnits.size(); i++) {
            if (ignoredUnits.get(i).unitTag.equals(unitToRemove)) {
                ignoredUnits.remove(i);
                return;
            }
        }
    }

    public static boolean contains(Tag unitToCheck) {
        return ignoredUnits.stream().anyMatch(ignoredUnit -> ignoredUnit.unitTag.equals(unitToCheck));
    }

    public static void add(Ignored unitToAdd) {
        ignoredUnits.add(unitToAdd);
    }

    public static void onStep() {
        ignoredUnits.removeIf(ignoredUnit -> ignoredUnit.doReleaseUnit());
    }

}
