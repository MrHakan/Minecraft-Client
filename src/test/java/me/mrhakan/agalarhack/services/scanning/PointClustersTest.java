package me.mrhakan.agalarhack.services.scanning;

import java.util.ArrayList;
import java.util.List;
import me.mrhakan.agalarhack.services.scanning.PointClusters.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PointClustersTest {
    @Test void nearbyPointsBecomeOneCluster() {
        var clusters = PointClusters.group(List.of(new Point(0, 64, 0), new Point(2, 64, 1), new Point(1, 64, 2)), 8, 1);
        assertEquals(1, clusters.size());
        assertEquals(3, clusters.get(0).size());
    }

    @Test void distantGroupsStaySeparate() {
        var clusters = PointClusters.group(List.of(
                new Point(0, 64, 0), new Point(1, 64, 1),
                new Point(500, 64, 500), new Point(501, 64, 501)), 8, 1);
        assertEquals(2, clusters.size());
    }

    @Test void singleLinkJoinsAChainThatNoSinglePairSpans() {
        // Each step is within the radius even though the ends are far apart: a corridor of chests
        // should read as one base, not several.
        var points = List.of(new Point(0, 64, 0), new Point(8, 64, 0), new Point(16, 64, 0), new Point(24, 64, 0));
        var clusters = PointClusters.group(points, 8, 1);
        assertEquals(1, clusters.size());
        assertEquals(4, clusters.get(0).size());
    }

    @Test void clustersBelowTheMinimumAreDiscarded() {
        var clusters = PointClusters.group(List.of(
                new Point(0, 64, 0), new Point(1, 64, 0), new Point(2, 64, 0),
                new Point(900, 64, 900)), 8, 3);
        assertEquals(1, clusters.size());
        assertEquals(3, clusters.get(0).size());
    }

    @Test void theCentreIsTheMeanOfItsMembers() {
        var clusters = PointClusters.group(List.of(new Point(0, 60, 0), new Point(10, 70, 20)), 64, 1);
        assertEquals(5, clusters.get(0).centerX());
        assertEquals(65, clusters.get(0).centerY());
        assertEquals(10, clusters.get(0).centerZ());
    }

    @Test void largestClusterComesFirst() {
        var points = new ArrayList<Point>();
        points.add(new Point(0, 64, 0));
        for (int i = 0; i < 5; i++) points.add(new Point(500 + i, 64, 500));
        var clusters = PointClusters.group(points, 8, 1);
        assertEquals(5, clusters.get(0).size());
        assertEquals(1, clusters.get(1).size());
    }

    @Test void heightDoesNotSplitAClusterBecauseBasesAreVertical() {
        var clusters = PointClusters.group(List.of(new Point(0, 10, 0), new Point(1, 250, 1)), 8, 1);
        assertEquals(1, clusters.size());
    }

    @Test void emptyAndNullInputProduceNoClusters() {
        assertTrue(PointClusters.group(null, 8, 1).isEmpty());
        assertTrue(PointClusters.group(List.of(), 8, 1).isEmpty());
    }

    @Test void degenerateRadiusAndMinimumAreClampedRatherThanCrashing() {
        var adjacent = List.of(new Point(0, 64, 0), new Point(1, 64, 0));
        assertDoesNotThrow(() -> PointClusters.group(adjacent, 0, 0));
        // Radius clamps to 1, so points exactly one block apart still connect.
        assertEquals(1, PointClusters.group(adjacent, -5, -5).size());
        var apart = List.of(new Point(0, 64, 0), new Point(3, 64, 0));
        assertEquals(2, PointClusters.group(apart, -5, -5).size(), "beyond the clamped radius they separate");
    }

    @Test void outputIsBoundedAndImmutable() {
        var points = new ArrayList<Point>();
        for (int i = 0; i < PointClusters.MAX_CLUSTERS * 3; i++) points.add(new Point(i * 1000, 64, 0));
        var clusters = PointClusters.group(points, 4, 1);
        assertEquals(PointClusters.MAX_CLUSTERS, clusters.size());
        assertThrows(UnsupportedOperationException.class, () -> clusters.add(null));
    }

    /**
     * A full-size input still groups by connectivity, not by how the points are walked.
     *
     * <p>The grouping was rewritten to bucket points into cells a radius across, because comparing
     * every member against every remaining point cost half a client tick at this size. Single-link
     * clustering produces connected components, so the answer cannot depend on traversal order - but
     * that is the kind of claim worth holding a test against, and a dense input is where a
     * neighbourhood search goes wrong if the cell size and the radius ever drift apart.
     */
    @Test void aFullSizeInputGroupsByConnectivity() {
        List<PointClusters.Point> points = new ArrayList<>();
        // Eight tight blobs far enough apart that nothing bridges them, plus scattered singles.
        for (int blob = 0; blob < 8; blob++) {
            for (int member = 0; member < 40; member++) {
                points.add(new PointClusters.Point(blob * 500 + (member % 7), 64, blob * 500 + (member / 7)));
            }
        }
        for (int stray = 0; stray < 200; stray++) {
            points.add(new PointClusters.Point(100_000 + stray * 90, 64, 100_000 + stray * 90));
        }

        var clusters = PointClusters.group(points, 12, 6);
        assertEquals(8, clusters.size(), "each blob is one cluster and no stray reaches the minimum");
        for (var cluster : clusters) {
            assertEquals(40, cluster.size(), "a blob must not be split by the cell boundaries it straddles");
        }
    }

    /** A radius wider than a cell must still find neighbours; the cell size is derived from it. */
    @Test void aLargeRadiusStillJoinsPointsAcrossCells() {
        List<PointClusters.Point> points = List.of(
                new PointClusters.Point(0, 64, 0),
                new PointClusters.Point(40, 64, 0),
                new PointClusters.Point(80, 64, 0));
        var clusters = PointClusters.group(points, 45, 3);
        assertEquals(1, clusters.size());
        assertEquals(3, clusters.get(0).size());
    }
}
