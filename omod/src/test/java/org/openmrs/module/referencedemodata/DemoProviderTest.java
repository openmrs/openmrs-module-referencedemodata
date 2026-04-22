package org.openmrs.module.referencedemodata;

import org.junit.Test;
import org.openmrs.Provider;
import org.openmrs.api.context.Context;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import static org.junit.Assert.*;

public class DemoProviderTest extends BaseModuleContextSensitiveTest {
    @Test
    public void ensureIsIdempotent() {
        Provider p1 = DemoProvider.ensure();
        Provider p2 = DemoProvider.ensure();
        assertEquals(p1.getUuid(), p2.getUuid());
        assertEquals(ReferenceDemoDataConstants.DEMO_PROVIDER_UUID, p1.getUuid());
    }
}
