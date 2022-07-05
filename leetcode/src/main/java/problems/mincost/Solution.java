package problems.mincost;

import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

public class Solution {
    public int minCostConnectPoints(int[][] points) {
        DisjointSet disjointSet = new DisjointSet();
        PriorityQueue<Pair<Point, Point>> minEdges = new PriorityQueue<>(
                (edgeA, edgeB) -> weight(edgeA) - weight(edgeB)
        );

        for (int i = 0; i < points.length; i++) {
            Point a = new Point(points[i][0], points[i][1]);
            disjointSet.add(a);
            for (int j = i + 1; j < points.length; j++) {
                Point b = new Point(points[j][0], points[j][1]);
                minEdges.offer(Pair.of(a, b));
            }
        }

        int minCost = 0;
        while (disjointSet.componentsCount() > 1) {
            Pair<Point, Point> currEdge = minEdges.poll();
            if (disjointSet.union(currEdge)) {
                minCost += weight(currEdge);
            }
        }
        return minCost;
    }

    private int weight(Pair<Point, Point> edge) {
        Point a = edge.getKey();
        Point b = edge.getValue();
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    private static class DisjointSet {
        final Map<Point, Point> root = new HashMap<>();
        final Map<Point, Integer> rank = new HashMap<>();
        int componentsCount = 0;

        public int componentsCount() {
            return componentsCount;
        }

        public void add(Point x) {
            if (root.containsKey(x)) return;
            root.put(x, x);
            rank.put(x, 1);
            componentsCount++;
        }

        public Point find(Point x) {
            if (x.equals(root.get(x))) {
                return x;
            }

            root.put(x, find(root.get(x)));
            return root.get(x);
        }

        public boolean union(Pair<Point, Point> edge) {
            return union(edge.getKey(), edge.getValue());
        }

        public boolean union(Point x, Point y) {
            Point rootX = find(x);
            Point rootY = find(y);
            if (rootX.equals(rootY)) {
                return false;
            }

            int totalRank = rank.get(rootX) + rank.get(rootY);
            if (rank.get(rootX) > rank.get(rootY)) {
                root.put(rootY, rootX);
                rank.put(rootX, totalRank);
            } else if (rank.get(rootX) < rank.get(rootY)) {
                root.put(rootX, rootY);
                rank.put(rootY, totalRank);
            } else {
                root.put(rootY, rootX);
                rank.put(rootX, totalRank);
            }
            componentsCount--;
            return true;
        }
    }

    private static class Point {
        final int x;
        final int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) return false;
            if (!(o instanceof Point)) return false;
            Point other = (Point) o;
            return this.x == other.x && this.y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    public static void main(String[] args) {
        Solution solution = new Solution();
        int[][] points = {
                {-8,14},
                {16,-18},
                {-19,-13},
                {-18,19},
                {20,20},
                {13,-20},
                {-15,9},
                {-4,-8}
        };
        System.out.println(solution.minCostConnectPoints(points));
    }
}
