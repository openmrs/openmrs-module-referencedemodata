package org.openmrs.module.referencedemodata.fixture;

import org.junit.Test;
import org.openmrs.test.BaseModuleContextSensitiveTest;

public class ConceptAliasesTest extends BaseModuleContextSensitiveTest {
    @Test(expected = IllegalStateException.class)
    public void failsLoudlyForUnknownAlias() {
        ConceptAliases aliases = ConceptAliases.fromClasspath("fixtures/concepts.yaml");
        aliases.resolve("DOES_NOT_EXIST");
    }
}
