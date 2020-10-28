package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.terranbot.models.Ignored;
import com.ketroc.terranbot.models.IgnoredUnit;

import java.util.ArrayList;
import java.util.List;

public class UnitMicroList {
    public static List<BasicMover> unitMicroList = new ArrayList<>();

    public static void onStep() {
        unitMicroList.forEach(BasicMover::onStep);
        unitMicroList.removeIf(basicMover -> basicMover.removeMe);
    }

    public static void add(BasicMover microUnit) {
        Ignored.add(new IgnoredUnit(microUnit.unit.getTag()));
        unitMicroList.add(microUnit);
    }

    public static void remove(Tag microUnitTag) {
        Ignored.remove(microUnitTag);
        for (int i=0; i<unitMicroList.size(); i++) { //remove first entry only
            if (unitMicroList.get(i).unit.getTag().equals(microUnitTag)) {
                unitMicroList.remove(i);
                return;
            }
        }
    }
}
