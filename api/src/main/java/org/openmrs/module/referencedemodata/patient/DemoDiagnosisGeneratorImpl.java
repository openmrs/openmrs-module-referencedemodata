/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.patient;

import static org.openmrs.module.referencedemodata.Randomizer.flipACoin;
import static org.openmrs.module.referencedemodata.Randomizer.randomArrayEntry;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_CODE_DIAGNOSIS_CERTAINTY;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_CODE_DIAGNOSIS_CONFIRMED;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_CODE_DIAGNOSIS_ORDER;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_CODE_DIAGNOSIS_ORDER_PRIMARY;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_CODE_DIAGNOSIS_ORDER_SECONDARY;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_CODE_DIAGNOSIS_PRESUMED;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_DIAGNOSIS_CONCEPT_SET;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_DIAGNOSIS_LIST;

import java.util.List;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.annotation.OpenmrsProfile;
import org.openmrs.api.ObsService;
import org.openmrs.api.context.Context;
import org.openmrs.module.referencedemodata.diagnosis.DemoDiagnosisGenerator;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@OpenmrsProfile(openmrsPlatformVersion = "2.1.*")
public class DemoDiagnosisGeneratorImpl extends DemoPatientGenerator implements DemoDiagnosisGenerator {
	
	public DemoDiagnosisGeneratorImpl() throws Exception {
		super(new PathMatchingResourcePatternResolver(new DefaultResourceLoader(Thread.currentThread().getContextClassLoader())));
	}

	@Autowired
	private ObsService obsService;
	
	@Override
	public void createDiagnosis(boolean primary, Patient patient, Encounter encounter, Location location, List<Concept> allDiagnoses) {
		Obs obsGroup = createBasicObs(CONCEPT_DIAGNOSIS_CONCEPT_SET, patient, encounter.getEncounterDatetime(), location);
		encounter.addObs(obsGroup);
		
		Context.getAdministrationService().setGlobalProperty(OpenmrsConstants.GP_CASE_SENSITIVE_DATABASE_STRING_COMPARISON,
				"true");
		
		String certainty = flipACoin() ? CONCEPT_CODE_DIAGNOSIS_CONFIRMED : CONCEPT_CODE_DIAGNOSIS_PRESUMED;
		Obs obs1 = createCodedObs(CONCEPT_CODE_DIAGNOSIS_CERTAINTY, certainty, patient, encounter, encounter.getEncounterDatetime(), location);
		
		Obs obs2 = createCodedObs(CONCEPT_DIAGNOSIS_LIST, randomArrayEntry(allDiagnoses), patient, encounter, encounter.getEncounterDatetime(),
				location);
		
		String order = primary ? CONCEPT_CODE_DIAGNOSIS_ORDER_PRIMARY : CONCEPT_CODE_DIAGNOSIS_ORDER_SECONDARY;
		Obs obs3 = createCodedObs(CONCEPT_CODE_DIAGNOSIS_ORDER, order, patient, encounter, encounter.getEncounterDatetime(), location);
		
		obsGroup.addGroupMember(obs1);
		obsGroup.addGroupMember(obs2);
		obsGroup.addGroupMember(obs3);
		obsService.saveObs(obsGroup, "Creating Obs Group");
	}
}
