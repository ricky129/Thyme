package com.ricky.thyme.feature.containers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the path data of a Simple Icons SVG into a tiny 6x3 character sprite.
 *
 * <p>The SVG is flattened and sampled on a 12x12 grid with the even-odd rule,
 * then encoded both as Braille (2x4 dots per cell) and as half blocks for
 * terminals without Braille.</p>
 */
public final class SvgIconRasterizer {
    private static final int GRID = 12;
    private static final int SUPERSAMPLE = 2;
    private static final Pattern PATH = Pattern.compile(
            "<path\\b[^>]*?\\bd\\s*=\\s*([\"'])(.*?)\\1", Pattern.DOTALL);
    private static final Pattern VIEW_BOX = Pattern.compile(
            "viewBox\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final int[] LEFT_DOTS = {0x01, 0x02, 0x04, 0x40};
    private static final int[] RIGHT_DOTS = {0x08, 0x10, 0x20, 0x80};

    /** A 6x3 sprite: three rows of six characters, in both encodings. */
    public record Sprite(String[] brailleRows, String[] blockRows) {
    }

    private SvgIconRasterizer() {
        // Utility class.
    }

    /** Rasterizes the SVG, or returns {@code null} when it has no usable path data. */
    public static Sprite rasterize(String svg) {
        if (svg == null || svg.isBlank()) {
            return null;
        }
        double[] viewBox = viewBox(svg);
        List<List<double[]>> subpaths = new ArrayList<>();
        Matcher matcher = PATH.matcher(svg);
        while (matcher.find()) {
            if (!parsePath(matcher.group(2), subpaths)) {
                return null;
            }
        }
        if (subpaths.isEmpty()) {
            return null;
        }
        boolean[][] grid = new boolean[GRID][GRID];
        boolean any = false;
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                int hits = 0;
                for (int sy = 0; sy < SUPERSAMPLE; sy++) {
                    for (int sx = 0; sx < SUPERSAMPLE; sx++) {
                        double px = viewBox[0] + (col + (sx + 0.5) / SUPERSAMPLE) / GRID * viewBox[2];
                        double py = viewBox[1] + (row + (sy + 0.5) / SUPERSAMPLE) / GRID * viewBox[3];
                        if (inside(subpaths, px, py)) {
                            hits++;
                        }
                    }
                }
                grid[row][col] = hits * 2 >= SUPERSAMPLE * SUPERSAMPLE;
                any |= grid[row][col];
            }
        }
        return any ? encode(grid) : null;
    }

    /** Builds a sprite from twelve rows of twelve characters, {@code '#'} meaning filled. */
    public static Sprite fromBitmap(String[] rows) {
        if (rows == null || rows.length != GRID) {
            throw new IllegalArgumentException("bitmap must have 12 rows");
        }
        boolean[][] grid = new boolean[GRID][GRID];
        for (int row = 0; row < GRID; row++) {
            if (rows[row].length() != GRID) {
                throw new IllegalArgumentException("bitmap rows must have 12 columns");
            }
            for (int col = 0; col < GRID; col++) {
                grid[row][col] = rows[row].charAt(col) == '#';
            }
        }
        return encode(grid);
    }

    private static double[] viewBox(String svg) {
        Matcher matcher = VIEW_BOX.matcher(svg);
        if (matcher.find()) {
            String[] parts = matcher.group(1).trim().split("[\\s,]+");
            if (parts.length == 4) {
                try {
                    double[] box = new double[4];
                    for (int index = 0; index < 4; index++) {
                        box[index] = Double.parseDouble(parts[index]);
                    }
                    if (box[2] > 0 && box[3] > 0) {
                        return box;
                    }
                } catch (NumberFormatException ignored) {
                    // Fall back to the Simple Icons default below.
                }
            }
        }
        return new double[]{0, 0, 24, 24};
    }

    private static boolean inside(List<List<double[]>> subpaths, double px, double py) {
        boolean inside = false;
        for (List<double[]> subpath : subpaths) {
            int count = subpath.size();
            for (int index = 0; index < count; index++) {
                double[] a = subpath.get(index);
                double[] b = subpath.get((index + 1) % count);
                if (a[1] > py != b[1] > py
                        && px < (b[0] - a[0]) * (py - a[1]) / (b[1] - a[1]) + a[0]) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private static Sprite encode(boolean[][] grid) {
        String[] braille = new String[3];
        for (int cellRow = 0; cellRow < 3; cellRow++) {
            StringBuilder line = new StringBuilder();
            for (int cellCol = 0; cellCol < 6; cellCol++) {
                int bits = 0;
                for (int dy = 0; dy < 4; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        if (grid[cellRow * 4 + dy][cellCol * 2 + dx]) {
                            bits |= dx == 0 ? LEFT_DOTS[dy] : RIGHT_DOTS[dy];
                        }
                    }
                }
                line.append(bits == 0 ? ' ' : (char) (0x2800 + bits));
            }
            braille[cellRow] = line.toString();
        }
        boolean[][] blocks = new boolean[6][6];
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 6; col++) {
                int filled = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        if (grid[row * 2 + dy][col * 2 + dx]) {
                            filled++;
                        }
                    }
                }
                blocks[row][col] = filled >= 2;
            }
        }
        String[] block = new String[3];
        for (int row = 0; row < 3; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < 6; col++) {
                boolean top = blocks[row * 2][col];
                boolean bottom = blocks[row * 2 + 1][col];
                line.append(top && bottom ? '█' : top ? '▀' : bottom ? '▄' : ' ');
            }
            block[row] = line.toString();
        }
        return new Sprite(braille, block);
    }

    // ------------------------------------------------------------------
    // Path parsing
    // ------------------------------------------------------------------

    /** Scanner over SVG path data, tolerant of minified syntax. */
    private static final class Scanner {
        private final String text;
        private int pos;

        Scanner(String text) {
            this.text = text;
        }

        void skipSeparators() {
            while (pos < text.length()
                    && (Character.isWhitespace(text.charAt(pos)) || text.charAt(pos) == ',')) {
                pos++;
            }
        }

        boolean eof() {
            skipSeparators();
            return pos >= text.length();
        }

        char peek() {
            return text.charAt(pos);
        }

        void advance() {
            pos++;
        }

        double number() {
            skipSeparators();
            int start = pos;
            if (pos < text.length() && (text.charAt(pos) == '-' || text.charAt(pos) == '+')) {
                pos++;
            }
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            if (pos < text.length() && text.charAt(pos) == '.') {
                pos++;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                int save = pos;
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '-' || text.charAt(pos) == '+')) {
                    pos++;
                }
                int digits = pos;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
                if (digits == pos) {
                    pos = save;
                }
            }
            if (start == pos) {
                throw new NumberFormatException("number expected at " + pos);
            }
            return Double.parseDouble(text.substring(start, pos));
        }

        boolean flag() {
            skipSeparators();
            if (pos >= text.length() || text.charAt(pos) != '0' && text.charAt(pos) != '1') {
                throw new NumberFormatException("flag expected at " + pos);
            }
            boolean value = text.charAt(pos) == '1';
            pos++;
            return value;
        }
    }

    private static boolean parsePath(String data, List<List<double[]>> subpaths) {
        try {
            parse(data, subpaths);
            return true;
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return false;
        }
    }

    private static void parse(String data, List<List<double[]>> subpaths) {
        Scanner scanner = new Scanner(data);
        char command = 0;
        char previous = 0;
        double cx = 0;
        double cy = 0;
        double startX = 0;
        double startY = 0;
        double lastCtrlX = 0;
        double lastCtrlY = 0;
        List<double[]> current = null;
        while (!scanner.eof()) {
            char next = scanner.peek();
            if (Character.isLetter(next)) {
                command = next;
                scanner.advance();
                if (command == 'z' || command == 'Z') {
                    if (current != null) {
                        subpaths.add(current);
                        current = null;
                    }
                    cx = startX;
                    cy = startY;
                    previous = 'Z';
                    continue;
                }
            } else if (command == 0 || command == 'z' || command == 'Z') {
                throw new NumberFormatException("unexpected number");
            } else if (command == 'M') {
                command = 'L';
            } else if (command == 'm') {
                command = 'l';
            }
            boolean relative = Character.isLowerCase(command);
            char upper = Character.toUpperCase(command);
            double ox = relative ? cx : 0;
            double oy = relative ? cy : 0;
            if (upper != 'M' && current == null) {
                current = new ArrayList<>();
                current.add(new double[]{cx, cy});
            }
            switch (upper) {
                case 'M' -> {
                    double x = scanner.number() + ox;
                    double y = scanner.number() + oy;
                    if (current != null) {
                        subpaths.add(current);
                    }
                    current = new ArrayList<>();
                    current.add(new double[]{x, y});
                    cx = x;
                    cy = y;
                    startX = x;
                    startY = y;
                }
                case 'L' -> {
                    cx = scanner.number() + ox;
                    cy = scanner.number() + oy;
                    current.add(new double[]{cx, cy});
                }
                case 'H' -> {
                    cx = scanner.number() + ox;
                    current.add(new double[]{cx, cy});
                }
                case 'V' -> {
                    cy = scanner.number() + oy;
                    current.add(new double[]{cx, cy});
                }
                case 'C' -> {
                    double x1 = scanner.number() + ox;
                    double y1 = scanner.number() + oy;
                    double x2 = scanner.number() + ox;
                    double y2 = scanner.number() + oy;
                    double x = scanner.number() + ox;
                    double y = scanner.number() + oy;
                    cubic(current, cx, cy, x1, y1, x2, y2, x, y);
                    lastCtrlX = x2;
                    lastCtrlY = y2;
                    cx = x;
                    cy = y;
                }
                case 'S' -> {
                    boolean smooth = previous == 'C' || previous == 'S';
                    double x1 = smooth ? 2 * cx - lastCtrlX : cx;
                    double y1 = smooth ? 2 * cy - lastCtrlY : cy;
                    double x2 = scanner.number() + ox;
                    double y2 = scanner.number() + oy;
                    double x = scanner.number() + ox;
                    double y = scanner.number() + oy;
                    cubic(current, cx, cy, x1, y1, x2, y2, x, y);
                    lastCtrlX = x2;
                    lastCtrlY = y2;
                    cx = x;
                    cy = y;
                }
                case 'Q' -> {
                    double x1 = scanner.number() + ox;
                    double y1 = scanner.number() + oy;
                    double x = scanner.number() + ox;
                    double y = scanner.number() + oy;
                    quadratic(current, cx, cy, x1, y1, x, y);
                    lastCtrlX = x1;
                    lastCtrlY = y1;
                    cx = x;
                    cy = y;
                }
                case 'T' -> {
                    boolean smooth = previous == 'Q' || previous == 'T';
                    double x1 = smooth ? 2 * cx - lastCtrlX : cx;
                    double y1 = smooth ? 2 * cy - lastCtrlY : cy;
                    double x = scanner.number() + ox;
                    double y = scanner.number() + oy;
                    quadratic(current, cx, cy, x1, y1, x, y);
                    lastCtrlX = x1;
                    lastCtrlY = y1;
                    cx = x;
                    cy = y;
                }
                case 'A' -> {
                    double rx = scanner.number();
                    double ry = scanner.number();
                    double rotation = scanner.number();
                    boolean large = scanner.flag();
                    boolean sweep = scanner.flag();
                    double x = scanner.number() + ox;
                    double y = scanner.number() + oy;
                    arc(current, cx, cy, rx, ry, rotation, large, sweep, x, y);
                    cx = x;
                    cy = y;
                }
                default -> throw new NumberFormatException("unknown command " + command);
            }
            previous = upper;
        }
        if (current != null) {
            subpaths.add(current);
        }
    }

    private static void cubic(List<double[]> out, double x0, double y0, double x1, double y1,
            double x2, double y2, double x3, double y3) {
        int steps = 8;
        for (int step = 1; step <= steps; step++) {
            double t = (double) step / steps;
            double u = 1 - t;
            out.add(new double[]{
                u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3,
                u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3});
        }
    }

    private static void quadratic(List<double[]> out, double x0, double y0, double x1, double y1,
            double x2, double y2) {
        int steps = 6;
        for (int step = 1; step <= steps; step++) {
            double t = (double) step / steps;
            double u = 1 - t;
            out.add(new double[]{
                u * u * x0 + 2 * u * t * x1 + t * t * x2,
                u * u * y0 + 2 * u * t * y1 + t * t * y2});
        }
    }

    private static void arc(List<double[]> out, double x0, double y0, double rxIn, double ryIn,
            double rotationDegrees, boolean large, boolean sweep, double x, double y) {
        double rx = Math.abs(rxIn);
        double ry = Math.abs(ryIn);
        if (rx == 0 || ry == 0 || x0 == x && y0 == y) {
            out.add(new double[]{x, y});
            return;
        }
        double phi = Math.toRadians(rotationDegrees);
        double cos = Math.cos(phi);
        double sin = Math.sin(phi);
        double dx = (x0 - x) / 2;
        double dy = (y0 - y) / 2;
        double x1p = cos * dx + sin * dy;
        double y1p = -sin * dx + cos * dy;
        double lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry);
        if (lambda > 1) {
            double scale = Math.sqrt(lambda);
            rx *= scale;
            ry *= scale;
        }
        double numerator = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
        double denominator = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
        double coefficient = Math.sqrt(Math.max(0, numerator / denominator));
        if (large == sweep) {
            coefficient = -coefficient;
        }
        double cxp = coefficient * rx * y1p / ry;
        double cyp = -coefficient * ry * x1p / rx;
        double centerX = cos * cxp - sin * cyp + (x0 + x) / 2;
        double centerY = sin * cxp + cos * cyp + (y0 + y) / 2;
        double ux = (x1p - cxp) / rx;
        double uy = (y1p - cyp) / ry;
        double vx = (-x1p - cxp) / rx;
        double vy = (-y1p - cyp) / ry;
        double theta = Math.atan2(uy, ux);
        double delta = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
        if (!sweep && delta > 0) {
            delta -= 2 * Math.PI;
        } else if (sweep && delta < 0) {
            delta += 2 * Math.PI;
        }
        int steps = 12;
        for (int step = 1; step <= steps; step++) {
            double t = theta + delta * step / steps;
            double ex = rx * Math.cos(t);
            double ey = ry * Math.sin(t);
            out.add(new double[]{
                centerX + ex * cos - ey * sin,
                centerY + ex * sin + ey * cos});
        }
    }
}
