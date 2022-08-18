/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.condition;

import static org.openmrs.module.referencedemodata.Randomizer.randomArrayEntry;
import static org.openmrs.module.referencedemodata.Randomizer.randomDoubleBetween;

import java.util.List;

import org.openmrs.CodedOrFreeText;
import org.openmrs.Concept;
import org.openmrs.Condition;
import org.openmrs.ConditionClinicalStatus;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.annotation.OpenmrsProfile;
import org.openmrs.api.ConditionService;
import org.springframework.beans.factory.annotation.Autowired;

@OpenmrsProfile(openmrsPlatformVersion = "2.2.* - 2.3.*")
public class DemoConditionGeneratorImpl2_2 implements DemoConditionGenerator {
	
	@Autowired
	private ConditionService conditionService;
	
	@Override
	public Condition createCondition(Patient patient, Encounter encounter, List<Concept> allConditions) {
		Concept codedConcept = randomArrayEntry(allConditions);
		Condition condition = null;
		if (randomDoubleBetween(0.0, 1.0) < .50) {
			condition = new Condition();
			condition.setCondition(new CodedOrFreeText(codedConcept, codedConcept.getName(), "Some non-coded condition"));
			condition.setClinicalStatus(ConditionClinicalStatus.ACTIVE);
			condition.setPatient(patient);
			conditionService.saveCondition(condition);
		}
		return condition;
	}
}
