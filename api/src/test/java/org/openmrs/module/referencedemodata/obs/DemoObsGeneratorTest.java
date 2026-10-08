/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.obs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ObsService;
import org.openmrs.api.ValidationException;
import org.openmrs.module.referencedemodata.DemoDataConceptCache;
import org.springframework.test.util.ReflectionTestUtils;

public class DemoObsGeneratorTest {

	private ObsService obsService;

	private DemoObsGenerator generator;

	@Before
	public void setup() {
		obsService = mock(ObsService.class);
		generator = new DemoObsGenerator(mock(DemoDataConceptCache.class));

		// inject the mocked ObsService so createObs() does not reach into the OpenMRS Context
		ReflectionTestUtils.setField(generator, "os", obsService);
	}

	/**
	 * A randomly generated numeric value can fall outside the concept's reference range, which the
	 * core ObsValidator (>= 2.7) rejects with a ValidationException. That must not abort the whole
	 * demo data generation - the single offending obs is skipped instead.
	 */
	@Test
	public void createObs_shouldSkipObsThatFailsValidationRatherThanPropagate() {
		when(obsService.saveObs(any(Obs.class), any()))
				.thenThrow(new ValidationException("valueNumeric: error.value.outOfRange.low"));

		Encounter encounter = new Encounter();
		Obs partialObs = new Obs();
		partialObs.setConcept(new Concept(210));

		Obs result = generator.createObs(partialObs, new Patient(), encounter, new Date(), null);

		assertNull("an obs that fails validation should be skipped, not returned", result);
		assertFalse("the skipped obs must be removed from the encounter so it is not re-saved on flush",
				encounter.getAllObs(true).contains(partialObs));
	}

	@Test
	public void createObs_shouldReturnTheSavedObsWhenValidationPasses() {
		Obs saved = new Obs();
		when(obsService.saveObs(any(Obs.class), any())).thenReturn(saved);

		Obs result = generator.createObs(new Obs(), new Patient(), new Encounter(), new Date(), null);

		assertSame(saved, result);
	}
}
