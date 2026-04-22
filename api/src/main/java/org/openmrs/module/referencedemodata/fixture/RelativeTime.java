package org.openmrs.module.referencedemodata.fixture;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;

public final class RelativeTime {
    private RelativeTime() {}

    /** Resolves "-P25Y", "-P2Y11M", "-P29D" against anchor. Positive offsets rejected: demo data must be in the past. */
    public static Instant resolve(String s, Instant anchor) {
        if (s == null || !s.startsWith("-P")) {
            throw new IllegalArgumentException("expected ISO-8601 negative period like '-P25Y'; got: " + s);
        }
        Period p;
        try {
            p = Period.parse(s.substring(1)); // strip leading minus
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid period: " + s, e);
        }
        return LocalDateTime.ofInstant(anchor, ZoneOffset.UTC).minus(p).toInstant(ZoneOffset.UTC);
    }
}
