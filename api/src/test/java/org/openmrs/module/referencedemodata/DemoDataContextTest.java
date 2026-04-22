package org.openmrs.module.referencedemodata;

import org.junit.Test;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

public class DemoDataContextTest {

    @Test
    public void sameSeedProducesSameRandomSequence() {
        DemoDataContext a = DemoDataContext.of(42L, Instant.parse("2025-01-01T00:00:00Z"));
        DemoDataContext b = DemoDataContext.of(42L, Instant.parse("2025-01-01T00:00:00Z"));
        for (int i = 0; i < 100; i++) {
            assertEquals(a.random().nextLong(), b.random().nextLong());
        }
    }

    @Test
    public void pickIsDeterministicAcrossIterationOrder() {
        DemoDataContext ctx = DemoDataContext.of(7L, Instant.parse("2025-01-01T00:00:00Z"));
        List<String> forwards = Arrays.asList("a", "b", "c", "d", "e");
        List<String> reversed = Arrays.asList("e", "d", "c", "b", "a");
        String pickA = ctx.pick(forwards, s -> s);
        DemoDataContext ctx2 = DemoDataContext.of(7L, Instant.parse("2025-01-01T00:00:00Z"));
        String pickB = ctx2.pick(reversed, s -> s);
        assertEquals(pickA, pickB);
    }

    @Test
    public void newUuidIsDeterministic() {
        DemoDataContext a = DemoDataContext.of(1L, Instant.parse("2025-01-01T00:00:00Z"));
        DemoDataContext b = DemoDataContext.of(1L, Instant.parse("2025-01-01T00:00:00Z"));
        UUID ua = a.newUuid();
        UUID ub = b.newUuid();
        assertEquals(ua, ub);
    }
}
