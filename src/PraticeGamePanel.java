import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

class PraticeGamePanel extends JPanel {
    private int gridSize;
    private boolean[][] horizontalLines;
    private boolean[][] verticalLines;
    private ArrayList<Line> lines;
    private boolean[][] boxes;
    private int dotSpacing = 100; // 점 사이 간격
    private Point firstSelectedPoint;

    public PraticeGamePanel(int gridSize) {
        this.gridSize = gridSize;
        this.horizontalLines = new boolean[gridSize][gridSize - 1];
        this.verticalLines = new boolean[gridSize - 1][gridSize];
        this.boxes = new boolean[gridSize - 1][gridSize - 1];
        this.lines = new ArrayList<>();
        setPreferredSize(new Dimension(400, 400));
        setBackground(Color.WHITE);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Point clickedPoint = new Point(e.getX(), e.getY());
                handleMouseClick(clickedPoint);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(Color.BLACK);

        // 점 그리기
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                int x = 50 + j * dotSpacing;
                int y = 50 + i * dotSpacing;
                g.fillOval(x - 5, y - 5, 10, 10);
            }
        }

        // 선 그리기
        g.setColor(Color.BLUE);
        for (Line line : lines) {
            int x1 = 50 + line.p1.x * dotSpacing;
            int y1 = 50 + line.p1.y * dotSpacing;
            int x2 = 50 + line.p2.x * dotSpacing;
            int y2 = 50 + line.p2.y * dotSpacing;
            g.drawLine(x1, y1, x2, y2);
        }

        // 사각형 색칠하기
        g.setColor(new Color(200, 200, 255));
        for (int i = 0; i < boxes.length; i++) {
            for (int j = 0; j < boxes[i].length; j++) {
                if (boxes[i][j]) {
                    int x = 50 + j * dotSpacing;
                    int y = 50 + i * dotSpacing;
                    g.fillRect(x+1, y+1, dotSpacing-1, dotSpacing-1);
                }
            }
        }
    }

    private void handleMouseClick(Point clickPoint) {
        Point gridPoint = getNearestGridPoint(clickPoint);
        if (gridPoint == null) return;

        if (firstSelectedPoint == null) {
            firstSelectedPoint = gridPoint;
        } else {
            Line newLine = new Line(firstSelectedPoint, gridPoint);
            if (isValidLine(newLine)) {
                lines.add(newLine);
                updateLineArrays(newLine);
                checkAndFillBoxes();
                repaint();
            }
            firstSelectedPoint = null;
        }
    }

    private Point getNearestGridPoint(Point clickPoint) {
        int x = (clickPoint.x - 50 + dotSpacing / 2) / dotSpacing;
        int y = (clickPoint.y - 50 + dotSpacing / 2) / dotSpacing;

        if (x >= 0 && x < gridSize && y >= 0 && y < gridSize) {
            return new Point(x, y);
        }
        return null;
    }

    private boolean isValidLine(Line line) {
        if (line.p1.equals(line.p2)) return false; // 점이 같은 경우

        int dx = Math.abs(line.p1.x - line.p2.x);
        int dy = Math.abs(line.p1.y - line.p2.y);
        if ((dx == 1 && dy == 0) || (dx == 0 && dy == 1)) {
            for (Line existingLine : lines) {
                if (existingLine.equals(line)) return false; // 이미 존재하는 선
            }
            return true;
        }
        return false;
    }

    private void updateLineArrays(Line line) {
        if (line.p1.x == line.p2.x) { // 수직 선
            int x = line.p1.x;
            int y = Math.min(line.p1.y, line.p2.y);
            verticalLines[y][x] = true;
        } else if (line.p1.y == line.p2.y) { // 수평 선
            int x = Math.min(line.p1.x, line.p2.x);
            int y = line.p1.y;
            horizontalLines[y][x] = true;
        }
    }

    private void checkAndFillBoxes() {
        for (int i = 0; i < gridSize - 1; i++) {
            for (int j = 0; j < gridSize - 1; j++) {
                if (!boxes[i][j] && horizontalLines[i][j] && horizontalLines[i + 1][j] && verticalLines[i][j] && verticalLines[i][j + 1]) {
                    boxes[i][j] = true;
                }
            }
        }
    }
}