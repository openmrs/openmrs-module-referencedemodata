/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.visit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;

import org.junit.Test;

public class DemoVisitGeneratorTest {
	
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 11, 12, 0);
	
	@Test
	public void notBeforePreviousStop_shouldMoveStartThatBeginsBeforeThePreviousVisitEnded() {
		LocalDateTime previousStop = NOW.minusHours(2);
		LocalDateTime start = NOW.minusHours(5);
		
		LocalDateTime result = DemoVisitGenerator.notBeforePreviousStop(start, previousStop);
		
		assertEquals(previousStop.plusMinutes(1), result);
		assertFalse(result.isAfter(NOW));
	}
	
	@Test
	public void notBeforePreviousStop_shouldMoveStartThatEqualsThePreviousStop() {
		LocalDateTime previousStop = NOW.minusDays(3);
		
		assertEquals(previousStop.plusMinutes(1), DemoVisitGenerator.notBeforePreviousStop(previousStop, previousStop));
	}
	
	@Test
	public void notBeforePreviousStop_shouldKeepStartThatIsAfterThePreviousStop() {
		LocalDateTime previousStop = NOW.minusDays(10);
		LocalDateTime start = previousStop.plusDays(2);
		
		assertEquals(start, DemoVisitGenerator.notBeforePreviousStop(start, previousStop));
	}
	
	@Test
	public void admissionFitsBeforeNow_shouldRejectStartInsideTheAdmissionWindow() {
		assertFalse(DemoVisitGenerator.admissionFitsBeforeNow(NOW.minusDays(2), NOW));
		assertFalse(DemoVisitGenerator.admissionFitsBeforeNow(NOW.minusHours(5), NOW));
	}
	
	@Test
	public void admissionFitsBeforeNow_shouldAcceptStartThatLeavesRoomForTheLongestAdmission() {
		assertTrue(DemoVisitGenerator.admissionFitsBeforeNow(NOW.minusDays(6), NOW));
		assertTrue(DemoVisitGenerator.admissionFitsBeforeNow(NOW.minusDays(30), NOW));
	}
}