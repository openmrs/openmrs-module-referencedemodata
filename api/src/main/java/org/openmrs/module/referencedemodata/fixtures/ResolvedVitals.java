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

final class ResolvedVitals {

	final Double systolicBp;

	final Double diastolicBp;

	final Double heartRate;

	final Double respiratoryRate;

	final Double oxygenSaturation;

	final Double temperatureC;

	ResolvedVitals(Double systolicBp, Double diastolicBp, Double heartRate,
			Double respiratoryRate, Double oxygenSaturation, Double temperatureC) {
		this.systolicBp = systolicBp;
		this.diastolicBp = diastolicBp;
		this.heartRate = heartRate;
		this.respiratoryRate = respiratoryRate;
		this.oxygenSaturation = oxygenSaturation;
		this.temperatureC = temperatureC;
	}
}
