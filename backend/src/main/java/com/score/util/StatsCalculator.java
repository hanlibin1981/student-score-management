package com.score.util;

import java.math.BigDecimal;
import java.util.List;

public final class StatsCalculator {

    private StatsCalculator() {
    }

    /**
     * 同分同名次：排名 = 严格大于自己的总分个数 + 1。
     */
    public static long rank(BigDecimal myTotal, List<BigDecimal> allTotals) {
        if (myTotal == null) {
            return 0;
        }
        return allTotals.stream()
                .filter(total -> total != null && total.compareTo(myTotal) > 0)
                .count() + 1;
    }
}
