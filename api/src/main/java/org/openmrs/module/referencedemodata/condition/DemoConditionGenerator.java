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

import java.util.List;

import org.openmrs.CodedOrFreeText;
import org.openmrs.Concept;
import org.openmrs.Condition;
import org.openmrs.ConditionClinicalStatus;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.api.ConditionService;
import org.openmrs.api.context.Context;

import static org.openmrs.module.referencedemodata.Randomizer.randomListEntry;

public class DemoConditionGenerator {
	
	private ConditionService conditionService = null;
	
	public Condition createCondition(Patient patient, Encounter encounter, List<Concept> allConditions) {
		Concept codedConcept = randomListEntry(allConditions);
		
		if (codedConcept == null) {
			return null;
		}
		
		Condition condition = new Condition();
		condition.setCondition(new CodedOrFreeText(codedConcept, codedConcept.getName(Context.getLocale()), "Some non-coded condition"));
		condition.setClinicalStatus(ConditionClinicalStatus.ACTIVE);
		condition.setPatient(patient);
		condition.setDateCreated(encounter.getEncounterDatetime());
		
		return getConditionService().saveCondition(condition);
	}
	
	protected ConditionService getConditionService() {
		if (conditionService == null) {
			conditionService = Context.getConditionService();
		}
		
		return conditionService;
	}
}
