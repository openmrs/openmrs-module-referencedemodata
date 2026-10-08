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

import org.openmrs.Condition;
import org.openmrs.ConditionVerificationStatus;
import org.openmrs.Diagnosis;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.api.DiagnosisService;
import org.openmrs.api.context.Context;
import org.openmrs.module.referencedemodata.DemoDataConceptCache;
import org.openmrs.module.referencedemodata.condition.DemoConditionGenerator;

import static org.openmrs.module.referencedemodata.Randomizer.randomBetween;


public class DemoDiagnosisGenerator {
	
	private DiagnosisService diagnosisService;
	
	private final DemoConditionGenerator conditionGenerator;
	
	private final DemoDataConceptCache conceptCache;
	
	public DemoDiagnosisGenerator(DemoDataConceptCache conceptCache, DemoConditionGenerator conditionGenerator) {
		this.conceptCache = conceptCache;
		this.conditionGenerator = conditionGenerator;
	}
	
	public void createDiagnosis(boolean primary, Patient patient, Encounter encounter) {
		Condition condition = conditionGenerator.createCondition(patient, encounter,
				conceptCache.getConceptsByClass("Diagnosis"));
		
		Diagnosis diagnosis = new Diagnosis();
		diagnosis.setCondition(condition);
		diagnosis.setDiagnosis(condition.getCondition());
		diagnosis.setEncounter(encounter);
		diagnosis.setCertainty(primary ? ConditionVerificationStatus.PROVISIONAL : ConditionVerificationStatus.CONFIRMED);
		diagnosis.setPatient(patient);
		diagnosis.setRank(randomBetween(1, 2));
		diagnosis.setDateCreated(encounter.getEncounterDatetime());
		
		getDiagnosisService().save(diagnosis);
	}
	
	protected DiagnosisService getDiagnosisService() {
		if (diagnosisService == null) {
			diagnosisService = Context.getDiagnosisService();
		}
		
		return diagnosisService;
	}
}
