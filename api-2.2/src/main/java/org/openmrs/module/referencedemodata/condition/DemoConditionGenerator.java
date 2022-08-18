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

import org.openmrs.Concept;
import org.openmrs.Condition;
import org.openmrs.Encounter;
import org.openmrs.Patient;

public interface DemoConditionGenerator {
	
	/**
	 * 
	 */
	public Condition createCondition(Patient patient, Encounter encounter, 
			List<Concept> allConditions);
	
}
