import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;

class Point {
    int x, y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Point point = (Point) obj;
        return x == point.x && y == point.y;
    }
}

class Line implements Serializable {
    Point p1, p2;

    public Line(Point p1, Point p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    // 선이 같은지 비교
    public boolean equals(Line other) {
        return (p1.equals(other.p1) && p2.equals(other.p2)) || (p1.equals(other.p2) && p2.equals(other.p1));
    }

    // 선이 두 점을 포함하는지 확인
    public boolean contains(Point point1, Point point2) {
        return (p1.equals(point1) && p2.equals(point2)) || (p1.equals(point2) && p2.equals(point1));
    }
}