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
import java.util.List;

final class ResolvedVisit {

	final Date startDate;

	final Date stopDate;

	final String typeName;

	final String locationName;

	final List<ResolvedEncounter> encounters;

	ResolvedVisit(Date startDate, Date stopDate, String typeName, String locationName,
			List<ResolvedEncounter> encounters) {
		this.startDate = startDate;
		this.stopDate = stopDate;
		this.typeName = typeName;
		this.locationName = locationName;
		this.encounters = encounters;
	}
}
