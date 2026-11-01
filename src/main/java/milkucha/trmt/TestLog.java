package milkucha.trmt;

import java.util.function.Supplier;

/** Test-only logging for automated in-game tests; see {@link #ENABLED}. */
public final class TestLog {

    /**
     * Enabled with the JVM property {@code -Dtrmt.testlog=true} (read once). Off by default: nothing is logged.
     * Emits "[TRMT-TEST] ..." INFO lines about erosion sync so a script can check the logs.
     */
    private static final boolean ENABLED = Boolean.getBoolean("trmt.testlog");

    private TestLog() {}

    public static void line(Supplier<String> message) {
        if (ENABLED) TRMT.LOGGER.info("[TRMT-TEST] {}", message.get());
    }
}
