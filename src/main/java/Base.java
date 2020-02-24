import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;

import java.util.List;

public class Base {
    private Point2d location;
    private Point2d gas1;
    private Point2d gas2;

    public Base(Point2d location) {
        this.location = location;
        initGases();
    }

    public Point2d getLocation() {
        return location;
    }

    public void setLocation(Point2d location) {
        this.location = location;
    }

    public Point2d getGas1() {
        return gas1;
    }

    public void setGas1(Point2d gas1) {
        this.gas1 = gas1;
    }

    public Point2d getGas2() {
        return gas2;
    }

    public void setGas2(Point2d gas2) {
        this.gas2 = gas2;
    }

    public void initGases() {
        List<UnitInPool> gasList = Bot.OBS.getUnits(Alliance.NEUTRAL, gas -> {
            return (gas.unit().getType() == Units.NEUTRAL_VESPENE_GEYSER ||
                    gas.unit().getType() == Units.NEUTRAL_RICH_VESPENE_GEYSER ||
                    gas.unit().getType() == Units.NEUTRAL_SPACE_PLATFORM_GEYSER) &&
                    this.location.distance(gas.unit().getPosition().toPoint2d()) < 10.0; //is gas geyser within 10 distance
        });
        switch (gasList.size()) {
            case 0:
                break;
            case 1:
                this.gas1 = gasList.get(0).unit().getPosition().toPoint2d();
                break;
            default:
                this.gas1 = gasList.get(0).unit().getPosition().toPoint2d();
                this.gas2 = gasList.get(1).unit().getPosition().toPoint2d();
                break;
        }
    }

    public Unit getGasUnit(Point2d pos) {
        return Bot.OBS.getUnits(Alliance.NEUTRAL, gas -> {
            return (gas.unit().getType() == Units.NEUTRAL_VESPENE_GEYSER ||
                    gas.unit().getType() == Units.NEUTRAL_RICH_VESPENE_GEYSER ||
                    gas.unit().getType() == Units.NEUTRAL_SPACE_PLATFORM_GEYSER) &&
                    gas.unit().getPosition().toPoint2d().equals(pos);
        }).get(0).unit();
    }
}