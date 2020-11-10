package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.terranbot.bots.Bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class Ignored {
    public static List<Ignored> ignoredUnits = new ArrayList<>();
    public Tag unitTag;

    public Ignored(Tag unitTag) {
        this.unitTag = unitTag;
    }

    public abstract boolean doReleaseUnit();

    // *********** STATIC METHODS *************
    public static void remove(Tag unitToRemove) {
        for (int i=0; i<ignoredUnits.size(); i++) { //remove first entry only
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

    public static int numOfType(Set<Units> unitTypes) {
        return (int)ignoredUnits.stream()
                .map(ignored -> Bot.OBS.getUnit(ignored.unitTag))
                .filter(u -> u != null && unitTypes.contains(u.unit().getType()))
                .count();
    }

    public static int numOfType(Units unitType) {
        return (int)ignoredUnits.stream()
                .map(ignored -> Bot.OBS.getUnit(ignored.unitTag))
                .filter(u -> unitType == (Units)u.unit().getType())
                .count();
    }

}
