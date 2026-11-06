package de.mangalib.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.IntStream;

/** Reine Centarithmetik. Restcents erhalten die ersten fachlich sortierten Zielbände. */
public final class Money {
    private static final BigDecimal LIMIT = new BigDecimal("100000000");
    private Money() { }

    public static BigDecimal amount(BigDecimal value) {
        if (value == null || value.scale() < -8 || value.scale() > 10
                || value.abs().compareTo(LIMIT) >= 0) throw new IllegalArgumentException("Ungültiger Geldbetrag.");
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException("Geldbetrag muss centgenau sein."); }
    }

    public static List<BigDecimal> distribute(BigDecimal total, int count) {
        if (count < 1 || count > 10_000) throw new IllegalArgumentException("Ungültige Bandanzahl.");
        long cents = amount(total).movePointRight(2).longValueExact();
        long base = cents / count, remainder = cents % count;
        return IntStream.range(0, count).mapToObj(i -> BigDecimal.valueOf(
                base + (i < Math.abs(remainder) ? Long.signum(remainder) : 0), 2)).toList();
    }

    public static BigDecimal mean(BigDecimal total, int count) {
        if (count < 1 || count > 10_000) throw new IllegalArgumentException("Ungültige Bandanzahl.");
        return amount(total).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
