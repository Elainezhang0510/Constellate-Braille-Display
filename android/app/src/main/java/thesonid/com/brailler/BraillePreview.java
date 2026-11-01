package thesonid.com.brailler;

/**
 * Maps a–z to 6-dot braille cells for in-app preview.
 * Dot order: 1 4 / 2 5 / 3 6 (standard cell layout).
 * At most {@link #CELLS_PER_ROW} cells per line; letters 5–8 wrap to a second band
 * aligned under letters 1–4. A second band is omitted when the word is 4 letters or fewer.
 */
final class BraillePreview {

    private static final int CELLS_PER_ROW = 4;
    private static final String CELL_GAP = "  ";
    private static final int CELL_WIDTH = 2;
    private static final int ROW_WIDTH =
            CELLS_PER_ROW * CELL_WIDTH + (CELLS_PER_ROW - 1) * CELL_GAP.length();

    private static final int[][] DOTS = {
            {1, 0, 0, 0, 0, 0}, // a
            {1, 1, 0, 0, 0, 0}, // b
            {1, 0, 0, 1, 0, 0}, // c
            {1, 0, 0, 1, 1, 0}, // d
            {1, 0, 0, 0, 1, 0}, // e
            {1, 1, 0, 1, 0, 0}, // f
            {1, 1, 0, 1, 1, 0}, // g
            {1, 1, 0, 0, 1, 0}, // h
            {0, 1, 0, 1, 0, 0}, // i
            {0, 1, 0, 1, 1, 0}, // j
            {1, 0, 1, 0, 0, 0}, // k
            {1, 1, 1, 0, 0, 0}, // l
            {1, 0, 1, 1, 0, 0}, // m
            {1, 0, 1, 1, 1, 0}, // n
            {1, 0, 1, 0, 1, 0}, // o
            {1, 1, 1, 1, 0, 0}, // p
            {1, 1, 1, 1, 1, 0}, // q
            {1, 1, 1, 0, 1, 0}, // r
            {0, 1, 1, 1, 0, 0}, // s
            {0, 1, 1, 1, 1, 0}, // t
            {1, 0, 1, 0, 0, 1}, // u
            {1, 1, 1, 0, 0, 1}, // v
            {0, 1, 0, 1, 1, 1}, // w
            {1, 0, 1, 1, 0, 1}, // x
            {1, 0, 1, 1, 1, 1}, // y
            {1, 0, 1, 0, 1, 1}, // z
    };

    private BraillePreview() {
    }

    /**
     * Returns a preview with one 2×3 cell per letter, 4 cells per line:
     * <pre>
     * ●○  ●○  ●○  ●○
     * ●○  ○●  ●○  ●○
     * ●○  ●○  ●○  ●○
     *
     * ●○  ●○
     * ○●  ●○
     * ●○  ●○
     * </pre>
     */
    static String formatWord(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }
        int[][] cells = new int[word.length()][];
        int count = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = Character.toLowerCase(word.charAt(i));
            if (c < 'a' || c > 'z') {
                continue;
            }
            cells[count++] = DOTS[c - 'a'];
        }
        if (count == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int start = 0; start < count; start += CELLS_PER_ROW) {
            if (start > 0) {
                out.append("\n\n");
            }
            int n = Math.min(CELLS_PER_ROW, count - start);
            appendCellBand(out, cells, start, n, start > 0);
        }
        return out.toString();
    }

    private static void appendCellBand(
            StringBuilder out, int[][] cells, int start, int n, boolean padToFullRow) {
        for (int row = 0; row < 3; row++) {
            if (row > 0) {
                out.append('\n');
            }
            int lineStart = out.length();
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    out.append(CELL_GAP);
                }
                int[] d = cells[start + i];
                out.append(cell(d[row], d[row + 3]));
            }
            if (padToFullRow) {
                int pad = ROW_WIDTH - (out.length() - lineStart);
                for (int s = 0; s < pad; s++) {
                    out.append(' ');
                }
            }
        }
    }

    static String formatLetter(char c) {
        return formatWord(String.valueOf(c));
    }

    static boolean isRaised(char letter, int dot) {
        char c = Character.toLowerCase(letter);
        return c >= 'a' && c <= 'z' && dot >= 1 && dot <= 6 && DOTS[c - 'a'][dot - 1] == 1;
    }

    private static String cell(int left, int right) {
        return (left == 1 ? "●" : "○") + (right == 1 ? "●" : "○");
    }
}
