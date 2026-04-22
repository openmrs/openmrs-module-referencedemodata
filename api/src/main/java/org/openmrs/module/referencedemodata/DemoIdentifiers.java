package org.openmrs.module.referencedemodata;

import org.openmrs.PatientIdentifierType;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.referencemetadata.ReferenceMetadataConstants;

/** Thin adapter for generating OpenMRS IDs via idgen's "DemoData" source. */
public final class DemoIdentifiers {
    private DemoIdentifiers() {}

    private static IdentifierSourceService issOverride;

    /** Test hook for injecting a mock IdentifierSourceService. */
    public static void setIdentifierSourceService(IdentifierSourceService iss) {
        issOverride = iss;
    }

    private static IdentifierSourceService iss() {
        if (issOverride != null) return issOverride;
        IdentifierSourceService iss = Context.getService(IdentifierSourceService.class);
        if (iss == null) {
            throw new IllegalStateException(
                    "IdentifierSourceService unavailable — is the idgen module loaded and started?");
        }
        return iss;
    }

    public static PatientIdentifierType type() {
        PatientIdentifierType t = Context.getPatientService()
                .getPatientIdentifierTypeByName(ReferenceMetadataConstants.OPENMRS_ID_NAME);
        if (t == null) {
            throw new IllegalStateException(
                    "OpenMRS ID PatientIdentifierType is missing — has the referencemetadata module started?");
        }
        return t;
    }

    public static String next() {
        return iss().generateIdentifier(type(), ReferenceDemoDataConstants.DEMO_IDGEN_SOURCE_NAME);
    }
}
