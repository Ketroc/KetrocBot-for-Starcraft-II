import com.github.ocraft.s2client.protocol.spatial.Point2d;

public class LocationConstants {
    public static Point2d DEPOT1;
    public static Point2d BARRACKS;
    public static Point2d CC2;
    public static Point2d BUNKER1;
    public static Point2d DEPOT2;
    public static Point2d BUNKER2;
    public static Point2d GAS1;
    public static Point2d GAS2;
    public static Point2d GAS3;
    public static Point2d GAS4;

    public static void init(String mapName, boolean isTopPos) {
        switch (mapName) {
            case MapNames.TRITON:
                if (isTopPos) {
                    DEPOT1 = Point2d.of(71.0f, 154.0f);
                    BARRACKS = Point2d.of(70.5f, 144.5f);
                    CC2 = Point2d.of(82.5f, 160.5f);
                    BUNKER1 = Point2d.of(87.5f, 152.5f);
                    DEPOT2 = Point2d.of(71.0f, 152.0f);
                    BUNKER2 = Point2d.of(73.5f, 146.5f);
                    GAS1 = Point2d.of(59.5f, 164.5f);
                    GAS2 = Point2d.of(48.5f, 154.5f);
                    GAS3 = Point2d.of(89.5f, 163.5f);
                    GAS4 = Point2d.of(86.5f, 167.5f);
                }
                else {
                    DEPOT1 = Point2d.of(145.0f, 50.0f);
                    BARRACKS = Point2d.of(145.5f, 59.5f);
                    CC2 = Point2d.of(133.5f, 43.5f);
                    BUNKER1 = Point2d.of(142.5f, 57.5f);
                    DEPOT2 = Point2d.of(145.0f, 52.0f);
                    BUNKER2 = Point2d.of(142.5f, 57.5f);
                    GAS1 = Point2d.of(167.5f, 49.5f);
                    GAS2 = Point2d.of(156.5f, 39.5f);
                    GAS3 = Point2d.of(126.5f, 40.5f);
                }
        }
    }
}
