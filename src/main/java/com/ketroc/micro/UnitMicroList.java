package com.ketroc.micro;

import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.geometry.Position;
import com.ketroc.models.Ignored;
import com.ketroc.models.IgnoredUnit;
import com.ketroc.utils.DebugHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UnitMicroList {
    public static List<BasicUnitMicro> unitMicroList = new ArrayList<>();

    public static void onStep() {
        unitMicroList.stream()
                .filter(basicUnitMicro -> !basicUnitMicro.removeMe)
                .forEach(BasicUnitMicro::onStep);

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
        unitMicroList.removeIf(basicUnitMicro -> basicUnitMicro.unit.getTag().equals(microUnitTag));
    }

    public static <T extends BasicUnitMicro> void removeAll(Class<T> cls) {
        unitMicroList.stream()
                .filter(basicUnitMicro -> basicUnitMicro.getClass().equals(cls))
                .forEach(basicUnitMicro -> Ignored.remove(basicUnitMicro.unit.getTag()));
        unitMicroList.removeIf(basicUnitMicro -> basicUnitMicro.getClass().equals(cls));
    }

    public static <T extends BasicUnitMicro> List<T> getUnitSubList(Class<T> cls) {
        return unitMicroList.stream()
                .filter(basicUnit -> cls.isInstance(basicUnit))
                .map(basicUnit -> cls.cast(basicUnit))
                .collect(Collectors.toList());
    }

    public static <T extends BasicUnitMicro> int numOfUnitClass(Class<T> cls) {
        return (int)unitMicroList.stream()
                .filter(basicUnit -> cls.isInstance(basicUnit))
                .count();
    }
}
