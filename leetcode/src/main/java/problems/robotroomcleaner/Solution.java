package problems.robotroomcleaner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * // This is the robot's control interface.
 * // You should not implement it, or speculate about its implementation
 * interface Robot {
 *     // Returns true if the cell in front is open and robot moves into the cell.
 *     // Returns false if the cell in front is blocked and robot stays in the current cell.
 *     public boolean move();
 *
 *     // Robot will stay in the same cell after calling turnLeft/turnRight.
 *     // Each turn will be 90 degrees.
 *     public void turnLeft();
 *     public void turnRight();
 *
 *     // Clean the current cell.
 *     public void clean();
 * }
 */

class Solution {
    private Map<Integer, Set<Integer>> visited;

    public void cleanRoom(Robot robot) {
        visited = new HashMap<>();
        cleanCell(robot, 0, 0);
        cleanRoomFrom(robot, 0, 0);
    }

    private void cleanRoomFrom(Robot robot, int row, int col) {
        for (Direction direction: Direction.values()) {
            int candRow = row + direction.dRow;
            int candCol = col + direction.dCol;
            if (isDirty(candRow, candCol) && canMove(robot, direction)) {
                move(robot, direction);
                cleanCell(robot, candRow, candCol);
                cleanRoomFrom(robot, candRow, candCol);
                move(robot, direction.opposite());
            }
        }
    }

    /**
     * Robot is always facing UP in order to simplify interfaces
     */
    private void move(Robot robot, Direction to) {
        turn(robot, Direction.UP, to);
        robot.move();
        turn(robot, to, Direction.UP);
    }

    /**
     * Due to the Robot interface in order to check if it can move
     * One has to try move and get robot back, additionally restoring
     * the initial direction UP, which we keep as an invariant.
     */
    private boolean canMove(Robot robot, Direction to) {
        turn(robot, Direction.UP, to);
        boolean moved = robot.move();
        turn(robot, to, to.opposite());
        if (moved) {
            robot.move();
        }
        turn(robot, to.opposite(), Direction.UP);
        return moved;
    }

    private void turn(Robot robot, Direction from, Direction to) {
        if (from == to) {
            return;
        }
        if (to == from.opposite()) {
            robot.turnRight();
            robot.turnRight();
            return;
        }
        if (from.toTheRightFrom(to)) {
            robot.turnLeft();
            return;
        }
        robot.turnRight();
    }

    private void cleanCell(Robot robot, int row, int col) {
        robot.clean();
        visited.putIfAbsent(row, new HashSet<>());
        visited.get(row).add(col);
    }

    private boolean isDirty(int row, int col) {
        Set<Integer> cleanedCols = visited.get(row);
        return cleanedCols == null || !cleanedCols.contains(col);
    }

    private enum Direction {
        UP(-1, 0),
        DOWN(1, 0),
        LEFT(0, -1),
        RIGHT(0, 1);

        int dRow;
        int dCol;

        Direction(int dRow, int dCol) {
            this.dRow = dRow;
            this.dCol = dCol;
        }

        public boolean toTheRightFrom(Direction other) {
            switch (this) {
                case UP: return other == LEFT;
                case RIGHT: return other == UP;
                case DOWN: return other == RIGHT;
                case LEFT: return other == DOWN;
            }
            throw new IllegalStateException();
        }

        public Direction opposite() {
            switch (this) {
                case UP: return DOWN;
                case DOWN: return UP;
                case LEFT: return RIGHT;
                case RIGHT: return LEFT;
            }
            throw new IllegalStateException();
        }
    }
}