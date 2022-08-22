/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.diagnosis;

import static org.openmrs.module.referencedemodata.Randomizer.randomBetween;
import static org.openmrs.module.referencedemodata.Randomizer.randomDoubleBetween;

import java.util.List;

import org.openmrs.Concept;
import org.openmrs.Condition;
import org.openmrs.ConditionVerificationStatus;
import org.openmrs.Diagnosis;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.annotation.OpenmrsProfile;
import org.openmrs.api.DiagnosisService;
import org.openmrs.module.referencedemodata.condition.DemoConditionGenerator;
import org.openmrs.module.referencedemodata.diagnosis.DemoDiagnosisGenerator;
import org.springframework.beans.factory.annotation.Autowired;

@OpenmrsProfile(openmrsPlatformVersion = "2.2.* - 2.6.*")
public class DemoDiagnosisGeneratorImpl2_2 implements DemoDiagnosisGenerator {

	@Autowired
	private DiagnosisService diagnosisService;
	
	@Autowired
	private DemoConditionGenerator conditionGenerator;
	
	@Override
	public void createDiagnosis(boolean primary, Patient patient, Encounter encounter, Location location, List<Concept> allDiagnoses) {
		Condition condition = conditionGenerator.createCondition(patient, encounter, allDiagnoses);
		if (condition != null && randomDoubleBetween(0.0, 1.0) < .50) {
			Diagnosis diagnosis = new Diagnosis();
			diagnosis.setCondition(condition);
			diagnosis.setDiagnosis(condition.getCondition());
			diagnosis.setEncounter(encounter);
			diagnosis.setCertainty(primary ? ConditionVerificationStatus.PROVISIONAL : ConditionVerificationStatus.CONFIRMED);
			diagnosis.setPatient(patient);
			diagnosis.setRank(randomBetween(0, 3));
			diagnosis.setDateCreated(encounter.getEncounterDatetime());
			
			diagnosisService.save(diagnosis);	
		}
	}
}
