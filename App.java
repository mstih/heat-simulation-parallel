import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.stream.IntStream;
import javax.swing.*;

public class App extends JFrame {

    private static int width;
    private static int height;
    private static int numPoints;
    private static int[][] grid;
    private BufferedImage image;
    private ForkJoinPool pool = new ForkJoinPool();
    private static boolean isBenchmark;
    private static final long SEED = 5318008;
    private static final int BRUSH_SIZE = 20;
    private static final int MAX_TEMP = 255;

    public static void main(String[] args) {
            // Default values
            width = 800;
            height = 600;
            numPoints = 3;
            isBenchmark = false;

            // Parse arguments if given
            for (int i = 0; i < args.length; i++) {
                if ("benchmark".equals(args[i])) {
                    isBenchmark = true;
                } else if ("width".equals(args[i]) && i + 1 < args.length) {
                    width = Integer.parseInt(args[i + 1]);
                    i++;
                } else if ("height".equals(args[i]) && i + 1 < args.length) {
                    height = Integer.parseInt(args[i + 1]);
                    i++;
                } else if ("points".equals(args[i]) && i + 1 < args.length) {
                    numPoints = Integer.parseInt(args[i + 1]);
                    i++;
                }
            }

            SwingUtilities.invokeLater(() -> {
                App simulation = new App(width, height);

                if (isBenchmark) {
                    simulation.setVisible(false);

                    Random random = new Random(SEED);
                    for (int i = 0; i < numPoints; i++) {
                        int x = random.nextInt(width);
                        int y = random.nextInt(height);
                        simulation.addHeat(x, y);
                    }
                } else {
                    simulation.setVisible(true);
                }

                if (isBenchmark) {
                    System.out.print("Benchmarking simulation in parallel mode...\n");

                    long startTime = System.currentTimeMillis();
                    while (!done()) {
                        simulation.simulateParallel();
                    }
                    long endTime = System.currentTimeMillis();
                    System.out.println(
                            "Time taken: " + (endTime - startTime) + "ms"
                    );

                    System.exit(0);
                } else {
                    new Timer(1000 / 30, e -> simulation.simulateParallel()).start();
                }

                final int FPS = 30;
                new Timer(1000 / FPS, e -> {
                    simulation.drawGrid(0, 0, width - 1, height - 1);
                    simulation.repaint();
                }).start();
            });
    }

    public App(int initialWidth, int initialHeight) {
        width = initialWidth;
        height = initialHeight;

        // Ensure width and height are greater than 0
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Width and height must be greater than 0"
            );
        }

        grid = new int[width][height];
        this.image = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_RGB
        );

        setTitle("Heat Simulation");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        addHeat(e.getX(), e.getY());
                    }
                }
        );

        addMouseMotionListener(
                new MouseMotionAdapter() {
                    @Override
                    public void mouseDragged(MouseEvent e) {
                        addHeat(e.getX(), e.getY());
                    }
                }
        );

        addComponentListener(
                new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        width = getWidth();
                        height = getHeight();

                        // Ensure width and height are greater than 0
                        if (width > 0 && height > 0) {
                            grid = new int[width][height];
                            image = new BufferedImage(
                                    width,
                                    height,
                                    BufferedImage.TYPE_INT_RGB
                            );
                            drawGrid(0, 0, width - 1, height - 1);
                        }
                    }
                }
        );
    }

    private static boolean done() {
        int temperature = grid[0][0];
        for (int[] row : grid) {
            for (int cell : row) {
                if (cell != temperature) {
                    return false;
                }
            }
        }
        return true;
    }

    private synchronized void addHeat(int x, int y) {
        System.out.println("Adding heat at (" + x + ", " + y + ")");

        int minX = Math.max(0, x - BRUSH_SIZE);
        int minY = Math.max(0, y - BRUSH_SIZE);
        int maxX = Math.min(width - 1, x + BRUSH_SIZE);
        int maxY = Math.min(height - 1, y + BRUSH_SIZE);

        for (int i = minX; i <= maxX; i++) {
            for (int j = minY; j <= maxY; j++) {
                int dx = i - x;
                int dy = j - y;
                if (dx * dx + dy * dy <= BRUSH_SIZE * BRUSH_SIZE) {
                    grid[i][j] = MAX_TEMP; // maximum temperature
                }
            }
        }

        if(!isBenchmark){
            drawGrid(minX, minY, maxX, maxY);
        }
    }

    @Override
    public void paint(Graphics g) {
        g.drawImage(image, 0, 0, this);
    }

    private void drawGrid(int minX, int minY, int maxX, int maxY) {
        for (int i = minX; i <= maxX; i++) {
            for (int j = minY; j <= maxY; j++) {
                int temperature = grid[i][j];
                image.setRGB(i, j, getColor(temperature).getRGB());
            }
        }
    }

    private Color getColor(int temperature) {
        float normalizedTemperature = Math.min(temperature / 255.0f, 1.0f); // normalize to 0-1
        float hue = (2.0f / 3.0f) * (1 - normalizedTemperature); // 1/3 blue, 2/3 green, 3/3 red
        return Color.getHSBColor(hue, 1.0f, 1.0f); // saturation and brightness always 1
    }

    public void updateGrid(int[][] newGrid) {
        grid = newGrid;
        if(!isBenchmark){
            drawGrid(0, 0, width - 1, height - 1);
        }
    }

    private int calculateNewTemperature(int x, int y) {
        if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
            return 0;
        }

        int totalTemp = grid[x][y]; // Include the center cell's temperature
        int totalCells = 1; // Count the center cell

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = x + dx;
                int ny = y + dy;

                // Skip the center cell
                if (dx == 0 && dy == 0) {
                    continue;
                }

                // Check if the neighbor cell is within the grid
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    totalTemp += grid[nx][ny];
                    totalCells++;
                }
            }
        }

        return totalTemp / totalCells; // average temperature
    }

    public void simulateParallel() {
        int[][] newGrid = new int[width][height];

        // Parallelize over the X-dimension (columns)
        IntStream.range(0, width).parallel().forEach(x -> {
            for (int y = 0; y < height; y++) {
                newGrid[x][y] = calculateNewTemperature(x, y);
            }
        });

        updateGrid(newGrid);

    }
}

