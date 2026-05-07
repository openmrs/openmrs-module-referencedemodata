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

import java.util.Date;

import org.openmrs.Concept;
import org.openmrs.ConditionClinicalStatus;

final class ResolvedCondition {

	final Concept concept;

	final Date onsetDate;

	final ConditionClinicalStatus status;

	ResolvedCondition(Concept concept, Date onsetDate, ConditionClinicalStatus status) {
		this.concept = concept;
		this.onsetDate = onsetDate;
		this.status = status;
	}
}
