/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.fixtures;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.openmrs.module.referencedemodata.orders.DrugOrderDescriptor;

final class ResolvedEncounter {
	
	final Date date;
	
	final String typeName;
	
	final String providerRole;
	
	final ResolvedVitals vitals;
	
	final ResolvedBmi bmi;
	
	final List<ResolvedNumericObs> labs;
	
	final List<DrugOrderDescriptor> drugOrders;
	
	final String noteText;
	
	final List<ResolvedDiagnosis> diagnoses;
	
	ResolvedEncounter(Date date, String typeName, String providerRole,
			ResolvedVitals vitals, ResolvedBmi bmi, List<ResolvedNumericObs> labs,
			List<DrugOrderDescriptor> drugOrders, String noteText,
			List<ResolvedDiagnosis> diagnoses) {
		this.date = date;
		this.typeName = typeName;
		this.providerRole = providerRole;
		this.vitals = vitals;
		this.bmi = bmi;
		this.labs = Collections.unmodifiableList(labs);
		this.drugOrders = Collections.unmodifiableList(drugOrders);
		this.noteText = noteText;
		this.diagnoses = Collections.unmodifiableList(diagnoses);
	}
}
