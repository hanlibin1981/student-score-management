package com.score.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatsCalculatorTest {

    @Test
    void highestTotalRanksFirst() {
        List<BigDecimal> totals = List.of(bd("90"), bd("72"), bd("55"));
        assertThat(StatsCalculator.rank(bd("90"), totals)).isEqualTo(1);
        assertThat(StatsCalculator.rank(bd("72"), totals)).isEqualTo(2);
        assertThat(StatsCalculator.rank(bd("55"), totals)).isEqualTo(3);
    }

    @Test
    void tiedTotalsShareRankAndSkipFollowing() {
        List<BigDecimal> totals = List.of(bd("90"), bd("90"), bd("80"));
        assertThat(StatsCalculator.rank(bd("90"), totals)).isEqualTo(1);
        assertThat(StatsCalculator.rank(bd("80"), totals)).isEqualTo(3);
    }

    @Test
    void nullTotalRanksZero() {
        assertThat(StatsCalculator.rank(null, List.of(bd("90")))).isZero();
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
