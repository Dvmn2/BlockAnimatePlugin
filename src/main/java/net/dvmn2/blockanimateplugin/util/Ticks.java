package net.dvmn2.blockanimateplugin.util;

import java.math.BigDecimal;

/** Conversions between seconds and server ticks. */
public final class Ticks {

    /** Server ticks per second. */
    public static final int PER_SECOND = 20;

    private static final int MIN_TICKS = 1;
    private static final int HUNDREDTHS_PER_TICK = 5;
    private static final int HUNDREDTHS_SCALE = 2;

    private Ticks() {
    }

    /** Converts seconds to ticks, rounding to the nearest tick, never less than one tick. */
    public static int fromSeconds(double seconds) {
        return (int) Math.max(MIN_TICKS, Math.round(seconds * PER_SECOND));
    }

    /** Converts whole seconds to ticks. */
    public static long fromWholeSeconds(int seconds) {
        return (long) seconds * PER_SECOND;
    }

    /** Formats a tick count as seconds without trailing zeros, for example "0.05" or "2". */
    public static String toSecondsString(int ticks) {
        return BigDecimal.valueOf((long) ticks * HUNDREDTHS_PER_TICK, HUNDREDTHS_SCALE)
                .stripTrailingZeros()
                .toPlainString();
    }
}
