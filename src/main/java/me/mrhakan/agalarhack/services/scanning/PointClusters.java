package me.mrhakan.agalarhack.services.scanning;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups nearby points into clusters.
 *
 * <p>Used to turn a scatter of storage blocks into "there is a base here". Single-link grouping is
 * enough for that and is cheap: a point joins a cluster when it is within the radius of any member,
 * so a corridor of chests reads as one base rather than several.
 *
 * <p>Bounded in both input and output, and free of Minecraft types so the grouping is tested directly.
 */
public final class PointClusters {
    private PointClusters() { }

    public static final int MAX_POINTS = 4096;
    public static final int MAX_CLUSTERS = 64;

    /** A group of points, with its centre and extent. */
    public record Cluster(int centerX, int centerY, int centerZ, int size) { }

    /** One point to group; y is carried for the centre but not used for the distance test. */
    public record Point(int x, int y, int z) { }

    /**
     * @param radius   horizontal distance within which two points count as connected
     * @param minimum  clusters smaller than this are discarded
     * @return clusters, largest first, bounded in count
     */
    public static List<Cluster> group(List<Point> points, int radius, int minimum) {
        if (points == null || points.isEmpty()) return List.of();
        int bound = Math.max(1, radius);
        long radiusSquared = (long) bound * bound;
        int limit = Math.min(points.size(), MAX_POINTS);

        boolean[] taken = new boolean[limit];
        List<Cluster> clusters = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            if (taken[i]) continue;
            List<Point> members = new ArrayList<>();
            members.add(points.get(i));
            taken[i] = true;
            // Single-link expansion: each newly added member can pull in further points.
            for (int cursor = 0; cursor < members.size(); cursor++) {
                Point current = members.get(cursor);
                for (int j = 0; j < limit; j++) {
                    if (taken[j]) continue;
                    if (withinSquared(current, points.get(j), radiusSquared)) {
                        taken[j] = true;
                        members.add(points.get(j));
                    }
                }
            }
            if (members.size() >= Math.max(1, minimum)) clusters.add(centre(members));
        }
        clusters.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return List.copyOf(clusters.subList(0, Math.min(clusters.size(), MAX_CLUSTERS)));
    }

    private static boolean withinSquared(Point a, Point b, long radiusSquared) {
        long dx = (long) a.x() - b.x();
        long dz = (long) a.z() - b.z();
        return dx * dx + dz * dz <= radiusSquared;
    }

    private static Cluster centre(List<Point> members) {
        long x = 0;
        long y = 0;
        long z = 0;
        for (Point point : members) { x += point.x(); y += point.y(); z += point.z(); }
        int size = members.size();
        return new Cluster((int) (x / size), (int) (y / size), (int) (z / size), size);
    }
}
