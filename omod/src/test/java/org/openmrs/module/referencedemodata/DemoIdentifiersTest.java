package org.openmrs.module.referencedemodata;

import org.junit.Test;
import org.openmrs.PatientIdentifierType;
import org.openmrs.module.referencemetadata.ReferenceMetadataActivator;
import org.openmrs.module.referencemetadata.ReferenceMetadataConstants;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.test.SkipBaseSetup;
import static org.junit.Assert.*;

@SkipBaseSetup
public class DemoIdentifiersTest extends BaseModuleContextSensitiveTest {

    @Test
    public void typeResolvesToOpenMRSId() throws Exception {
        initializeInMemoryDatabase();
        executeDataSet("requiredDataTestDataset.xml");
        authenticate();
        new ReferenceMetadataActivator().started();

        PatientIdentifierType t = DemoIdentifiers.type();
        assertNotNull("OpenMRS ID type must exist after referencemetadata startup", t);
        assertEquals(ReferenceMetadataConstants.OPENMRS_ID_NAME, t.getName());
    }
}
