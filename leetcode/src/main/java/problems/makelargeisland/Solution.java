package problems.makelargeisland;

import java.util.*;

public class Solution {
    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {-1, 0}, {0, -1}};

    public int largestIsland(int[][] grid) {
        final int n = grid.length;
        int maxIsland = 0;
        DisjointSet land = new DisjointSet(n);
        Queue<Point> candidates = new LinkedList<>();
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                Point p = new Point(row, col);
                if (grid[row][col] == 1) {
                    int currIsland = land.add(p);
                    maxIsland = Math.max(currIsland, maxIsland);
                } else {
                    candidates.offer(p);
                }
            }
        }

        while (!candidates.isEmpty()) {
            int newSize = land.evaluateAdd(candidates.poll());
            maxIsland = Math.max(newSize, maxIsland);
        }

        return maxIsland;
    }

    private static class DisjointSet {
        final Map<Point, Point> roots = new HashMap<>();
        final Map<Point, Integer> ranks = new HashMap<>();
        final int n;

        private DisjointSet(int n) {
            this.n = n;
        }

        public Point find(Point p) {
            Point found = roots.get(p);
            if (found == null || found.equals(p)) {
                return found;
            }

            roots.put(p, find(roots.get(p)));
            return roots.get(p);
        }

        public int evaluateAdd(Point p) {
            Set<Point> seenIslands = new HashSet<>();
            int newSize = 1;

            for (int[] d: DIRECTIONS) {
                int row = p.row + d[0];
                int col = p.col + d[1];
                if (row < 0 || col < 0 || row >= n || col >= n) {
                    continue;
                }

                Point adj = new Point(row, col);
                Point adjRoot = find(adj);
                if (adjRoot == null || !seenIslands.add(adjRoot)) {
                    continue;
                }

                newSize += ranks.get(adjRoot);
            }

            return newSize;
        }

        public int add(Point p) {
            roots.put(p, p);
            ranks.put(p, 1);

            int maxRank = 1;
            for (int[] d: DIRECTIONS) {
                int row = p.row + d[0];
                int col = p.col + d[1];
                if (row < 0 || col < 0 || row >= n || col >= n) {
                    continue;
                }

                Point p2 = new Point(row, col);
                if (find(p2) == null) {
                    continue;
                }

                int currRank = union(p, p2);
                maxRank = Math.max(currRank, maxRank);
            }

            return maxRank;
        }

        private int union(Point x, Point y) {
            Point rootX = find(x);
            Point rootY = find(y);

            if (rootX.equals(rootY)) {
                return ranks.get(rootX);
            }

            int rankX = ranks.get(rootX);
            int rankY = ranks.get(rootY);
            int totalRank = rankX + rankY;
            if (rankX >= rankY) {
                roots.put(rootY, rootX);
                ranks.put(rootX, totalRank);
            } else {
                roots.put(rootX, rootY);
                ranks.put(rootY, totalRank);
            }

            return totalRank;
        }

    }

    private static final class Point {
        final int row;
        final int col;

        private Point(int row, int col) {
            this.row = row;
            this.col = col;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Point)) return false;
            Point other = (Point) o;
            return this.row == other.row && this.col == other.col;
        }

        @Override
        public int hashCode() {
            return Objects.hash(row, col);
        }
    }

    public static void main(String[] args) {
        Solution solution = new Solution();
        int[][] map = {
                {1, 0},
                {0, 1}
        };
        assert solution.largestIsland(map) == 3;
    }
}
