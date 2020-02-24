import com.github.ocraft.s2client.protocol.spatial.Point2d;

public class LocationConstants {
    public static Point2d DEPOT1;
    public static Point2d BARRACKS;
    public static Point2d BUNKER1;
    public static Point2d DEPOT2;
    public static Point2d BUNKER2;
    public static Base BASE1;
    public static Base BASE2;

    public static void init(String mapName, boolean isTopPos) {
        switch (mapName) {
            case MapNames.TRITON:
                if (isTopPos) {
                    BASE2 = new Base(Point2d.of(82.5f, 160.5f));
                    DEPOT1 = Point2d.of(71.0f, 154.0f);
                    BARRACKS = Point2d.of(70.5f, 144.5f);
                    BUNKER1 = Point2d.of(87.5f, 152.5f);
                    DEPOT2 = Point2d.of(71.0f, 152.0f);
                    BUNKER2 = Point2d.of(73.5f, 146.5f);
                }
                else {
                    BASE2 = new Base(Point2d.of(133.5f, 43.5f));
                    DEPOT1 = Point2d.of(145.0f, 50.0f);
                    BARRACKS = Point2d.of(145.5f, 59.5f);
                    BUNKER1 = Point2d.of(128.5f, 51.5f);
                    DEPOT2 = Point2d.of(145.0f, 52.0f);
                    BUNKER2 = Point2d.of(142.5f, 57.5f);
                }
        }
    }
}
