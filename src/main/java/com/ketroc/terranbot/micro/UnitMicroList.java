package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.terranbot.models.Ignored;
import com.ketroc.terranbot.models.IgnoredUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UnitMicroList {
    public static List<BasicUnitMicro> unitMicroList = new ArrayList<>();

    public static void onStep() {
        unitMicroList.forEach(BasicUnitMicro::onStep);
        List<Tag> removeList = unitMicroList.stream()
                .filter(basicUnitMicro -> basicUnitMicro.removeMe)
                .map(basicUnitMicro -> basicUnitMicro.unit.getTag())
                .collect(Collectors.toList());
        removeList.forEach(tag -> UnitMicroList.remove(tag));
    }

    public static void add(BasicUnitMicro microUnit) {
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
