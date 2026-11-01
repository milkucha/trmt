package milkucha.trmt;

import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Test-only logging for automated in-game tests; see {@link #ENABLED}. */
public final class TestLog {

    /**
     * Enabled with the JVM property {@code -Dtrmt.testlog=true} (read once). Off by default: nothing is logged.
     * Emits "[TRMT-TEST] ..." INFO lines about erosion sync so a script can check the logs.
     */
    private static final boolean ENABLED = Boolean.getBoolean("trmt.testlog");

    /** Max {@code *-entry} lines logged per chunk sync (the first N by block position); the rest are only in the checksum. */
    private static final int CHUNK_ENTRY_LINES = 5;

    private TestLog() {}

    public static boolean enabled() {
        return ENABLED;
    }

    public static void line(Supplier<String> message) {
        if (ENABLED) TRMT.LOGGER.info("[TRMT-TEST] {}", message.get());
    }

    /** The per-block value fields shared by every entry line, so server and client text compares equal. */
    public static String entry(BlockPos pos, int stage, float walked, float threshold, long last) {
        return "pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " stage=" + stage
                + " walked=" + walked + " threshold=" + threshold + " last=" + last;
    }

    /**
     * Collects the entries of one chunk sync and logs a bounded summary: one {@code <side>-chunk} line with the
     * count, the stage sum and an order-independent checksum over all values, then the first
     * {@link #CHUNK_ENTRY_LINES} entries (by block position) as {@code <side>-entry} lines.
     * Only create one when {@link #enabled()}.
     */
    public static final class ChunkSummary {
        private final TreeMap<Long, String> first = new TreeMap<>();
        private int count;
        private long stageSum;
        private long checksum;

        public void add(BlockPos pos, int stage, float walked, float threshold, long last) {
            count++;
            stageSum += stage;
            long h = pos.asLong();
            h = h * 31 + stage;
            h = h * 31 + Float.floatToIntBits(walked);
            h = h * 31 + Float.floatToIntBits(threshold);
            h = h * 31 + last;
            checksum += h * 0x9E3779B97F4A7C15L;
            first.put(pos.asLong(), entry(pos, stage, walked, threshold, last));
            if (first.size() > CHUNK_ENTRY_LINES) first.pollLastEntry();
        }

        /** @param side "server" or "client"; @param head fields written after the tag, e.g. {@code player=Alice dim=... chunk=x,z} */
        public void log(String side, String head) {
            line(() -> side + "-chunk " + head + " entries=" + count + " stage-sum=" + stageSum + " checksum=" + checksum);
            for (Map.Entry<Long, String> e : first.entrySet()) {
                line(() -> side + "-entry " + head + " " + e.getValue());
            }
        }
    }
}
