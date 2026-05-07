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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.ConditionClinicalStatus;
import org.openmrs.api.APIException;
import org.openmrs.module.referencedemodata.DemoDataConceptCache;

import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;

class FixtureResolver {

	private final DemoDataConceptCache conceptCache;

	FixtureResolver(DemoDataConceptCache conceptCache) {
		this.conceptCache = conceptCache;
	}

	List<ResolvedCondition> resolveConditions(JsonNode conditionsNode) {
		List<ResolvedCondition> resolved = new ArrayList<>();
		if (conditionsNode == null || conditionsNode.isMissingNode() || conditionsNode.isNull()) {
			return resolved;
		}
		if (!conditionsNode.isArray()) {
			throw new APIException("Fixture 'conditions' must be an array");
		}
		for (JsonNode entry : conditionsNode) {
			String conceptId = entry.path("concept").asText(null);
			Concept concept = resolveConcept(conceptId);
			Date onset = resolveDateOffset(entry.path("onset"));
			ConditionClinicalStatus status = parseClinicalStatus(entry.path("status").asText(null));
			resolved.add(new ResolvedCondition(concept, onset, status));
		}
		return resolved;
	}

	private ConditionClinicalStatus parseClinicalStatus(String raw) {
		if (StringUtils.isBlank(raw)) {
			return ConditionClinicalStatus.ACTIVE;
		}
		try {
			return ConditionClinicalStatus.valueOf(raw.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new APIException("Fixture condition has unknown clinicalStatus: " + raw, e);
		}
	}

	Concept resolveConcept(String conceptIdentifier) {
		if (StringUtils.isBlank(conceptIdentifier)) {
			throw new APIException("Fixture references blank concept identifier");
		}
		return conceptCache.findConcept(conceptIdentifier);
	}

	/**
	 * Resolves a date-offset node (keys: yearsAgo, monthsAgo, weeksAgo, weeksAgoOffset, daysAgo;
	 * each defaults to 0) to a Date anchored at LocalDate.now(). Negative values move forward.
	 */
	Date resolveDateOffset(JsonNode offsetNode) {
		if (offsetNode == null || offsetNode.isMissingNode() || offsetNode.isNull() || !offsetNode.isObject()) {
			throw new APIException("Fixture date offset is missing or not an object: " + offsetNode);
		}
		int yearsAgo = offsetNode.path("yearsAgo").asInt(0);
		int monthsAgo = offsetNode.path("monthsAgo").asInt(0);
		int weeksAgo = offsetNode.path("weeksAgo").asInt(0) + offsetNode.path("weeksAgoOffset").asInt(0);
		int daysAgo = offsetNode.path("daysAgo").asInt(0);

		LocalDate resolved = LocalDate.now()
				.minusYears(yearsAgo)
				.minusMonths(monthsAgo)
				.minusWeeks(weeksAgo)
				.minusDays(daysAgo);
		return toDate(resolved);
	}
}
