package org.openmrs.module.referencedemodata.fixture;

import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class RelativeTimeTest {
    private final Instant anchor = Instant.parse("2025-01-01T00:00:00Z");

    @Test public void parsesYears()  { assertEquals(Instant.parse("2000-01-01T00:00:00Z"), RelativeTime.resolve("-P25Y", anchor)); }
    @Test public void parsesMonths() { assertEquals(Instant.parse("2024-12-01T00:00:00Z"), RelativeTime.resolve("-P1M", anchor)); }
    @Test public void parsesDays()   { assertEquals(Instant.parse("2024-12-03T00:00:00Z"), RelativeTime.resolve("-P29D", anchor)); }
    @Test public void parsesMixedYearsMonths() { assertEquals(Instant.parse("2022-02-01T00:00:00Z"), RelativeTime.resolve("-P2Y11M", anchor)); }
    @Test(expected = IllegalArgumentException.class) public void rejectsPositive() { RelativeTime.resolve("P1Y", anchor); }
    @Test(expected = IllegalArgumentException.class) public void rejectsGarbage()  { RelativeTime.resolve("yesterday", anchor); }
}
