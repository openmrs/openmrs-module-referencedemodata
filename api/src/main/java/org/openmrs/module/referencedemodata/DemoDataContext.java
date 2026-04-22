package org.openmrs.module.referencedemodata;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.openmrs.api.AdministrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds all sources of non-determinism behind one object. Thread one of these
 * through every generation path so seed + clock anchor fully determine the output.
 *
 * Single-threaded by design. If any caller ever parallelizes generation, it MUST
 * use {@link java.util.SplittableRandom#split()} at a known point — do not share
 * the same instance across threads.
 */
public final class DemoDataContext {

    private static final Logger log = LoggerFactory.getLogger(DemoDataContext.class);

    public static final String GP_SEED = "referencedemodata.seed";
    public static final String GP_CLOCK_ANCHOR = "referencedemodata.clockAnchor";
    public static final Instant DEFAULT_CLOCK_ANCHOR = Instant.parse("2025-01-01T00:00:00Z");
    public static final long DEFAULT_SEED = 0L;

    private final long seed;
    private final Instant clockAnchor;
    private final java.util.SplittableRandom random;
    private long uuidCounter = 0L;
    private long identifierCounter = 0L;

    public static final ZoneId ZONE = ZoneId.of("UTC");
    public static final Locale LOCALE = Locale.ROOT;

    private DemoDataContext(long seed, Instant clockAnchor) {
        this.seed = seed;
        this.clockAnchor = clockAnchor;
        this.random = new java.util.SplittableRandom(seed);
    }

    public static DemoDataContext of(long seed, Instant clockAnchor) {
        return new DemoDataContext(seed, clockAnchor);
    }

    public static DemoDataContext fromGlobalProperties(AdministrationService svc) {
        long seed = parseLong(svc.getGlobalProperty(GP_SEED, Long.toString(DEFAULT_SEED)), DEFAULT_SEED);
        Instant anchor;
        String raw = svc.getGlobalProperty(GP_CLOCK_ANCHOR, DEFAULT_CLOCK_ANCHOR.toString());
        try {
            anchor = Instant.parse(raw);
        } catch (DateTimeParseException e) {
            log.warn("Could not parse GP " + GP_CLOCK_ANCHOR + "=" + raw + " as Instant; falling back to " + DEFAULT_CLOCK_ANCHOR);
            anchor = DEFAULT_CLOCK_ANCHOR;
        }
        return new DemoDataContext(seed, anchor);
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            log.warn("Could not parse '" + s + "' as long; falling back to " + fallback);
            return fallback;
        }
    }

    public long seed() { return seed; }
    public Instant clockAnchor() { return clockAnchor; }
    public java.util.SplittableRandom random() { return random; }

    /**
     * Deterministically picks one element from any collection by sorting it via
     * the supplied key extractor first. Defends against HashMap iteration order.
     */
    public <T, K extends Comparable<K>> T pick(Collection<T> items, Function<T, K> key) {
        if (items.isEmpty()) throw new IllegalArgumentException("empty collection");
        List<T> sorted = items.stream()
                .sorted(Comparator.comparing(key))
                .collect(Collectors.toList());
        return sorted.get(random.nextInt(sorted.size()));
    }

    /** Deterministic UUIDv4 derived from (seed, monotonically-increasing counter). */
    public UUID newUuid() {
        long n = uuidCounter++;
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(seed).putLong(n);
        byte[] bytes = bb.array();
        bytes[6] &= 0x0f; bytes[6] |= 0x40; // version 4
        bytes[8] &= 0x3f; bytes[8] |= 0x80; // variant
        ByteBuffer b2 = ByteBuffer.wrap(bytes);
        return new UUID(b2.getLong(), b2.getLong());
    }

    /** Monotonically-increasing identifier counter for DEMO-NNNNNNNNNN identifiers. */
    public long nextIdentifierIndex() { return identifierCounter++; }
}
