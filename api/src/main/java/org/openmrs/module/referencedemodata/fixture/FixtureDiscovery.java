package org.openmrs.module.referencedemodata.fixture;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Tiny discovery over classpath:fixtures/index.txt. Avoids classpath-scanning magic. */
public final class FixtureDiscovery {
    private FixtureDiscovery() {}

    public static List<String> listFixtureResources() {
        try (InputStream in = FixtureDiscovery.class.getClassLoader()
                .getResourceAsStream("fixtures/index.txt")) {
            if (in == null) return Collections.emptyList();
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .map(s -> "fixtures/" + s)
                    .collect(Collectors.toList());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to read fixtures/index.txt", e);
        }
    }
}
