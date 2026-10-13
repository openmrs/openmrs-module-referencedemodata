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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.ConditionClinicalStatus;
import org.openmrs.Drug;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.referencedemodata.DemoDataConceptCache;
import org.openmrs.module.referencedemodata.orders.DrugOrderDescriptor;

import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;

@Slf4j
class FixtureResolver {
	
	private final DemoDataConceptCache conceptCache;

	private final Map<String, Optional<Drug>> drugByConceptUuidCache = new HashMap<>();

	FixtureResolver(DemoDataConceptCache conceptCache) {
		this.conceptCache = conceptCache;
	}
	
	List<ResolvedCondition> resolveConditions(JsonNode conditionsNode) {
		return resolveArray(conditionsNode, "conditions", entry -> new ResolvedCondition(
				resolveConcept(entry.path("concept").asText(null)),
				resolveDateOffset(entry.path("onset")),
				parseClinicalStatus(entry.path("status").asText(null))));
	}

	private <T> List<T> resolveArray(JsonNode node, String label, Function<JsonNode, T> mapper) {
		List<T> resolved = new ArrayList<>();
		if (node == null || node.isMissingNode() || node.isNull()) {
			return resolved;
		}
		if (!node.isArray()) {
			throw new APIException("Fixture '" + label + "' must be an array");
		}
		for (JsonNode entry : node) {
			resolved.add(mapper.apply(entry));
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
	 * Resolves a date-offset node (keys: yearsAgo, monthsAgo, weeksAgo, daysAgo; each defaults to 0)
	 * to a Date anchored at LocalDate.now(). Negative values move forward.
	 */
	Date resolveDateOffset(JsonNode offsetNode) {
		if (offsetNode == null || offsetNode.isMissingNode() || offsetNode.isNull() || !offsetNode.isObject()) {
			throw new APIException("Fixture date offset is missing or not an object: " + offsetNode);
		}
		int yearsAgo = requireIntField(offsetNode, "yearsAgo");
		int monthsAgo = requireIntField(offsetNode, "monthsAgo");
		int weeksAgo = requireIntField(offsetNode, "weeksAgo");
		int daysAgo = requireIntField(offsetNode, "daysAgo");

		LocalDate resolved = LocalDate.now()
				.minusYears(yearsAgo)
				.minusMonths(monthsAgo)
				.minusWeeks(weeksAgo)
				.minusDays(daysAgo);
		return toDate(resolved);
	}

	private int requireIntField(JsonNode parent, String fieldName) {
		if (!parent.has(fieldName)) {
			return 0;
		}
		JsonNode node = parent.get(fieldName);
		if (node == null || node.isNull()) {
			return 0;
		}
		if (!node.canConvertToInt()) {
			throw new APIException("date offset field '" + fieldName + "' must be an integer, got: " + node.toString());
		}
		return node.asInt();
	}
	
	List<ResolvedVisit> resolveVisits(JsonNode visitsNode) {
		List<ResolvedVisit> resolved = new ArrayList<>();
		if (visitsNode == null || visitsNode.isMissingNode() || visitsNode.isNull()) {
			return resolved;
		}
		if (!visitsNode.isArray()) {
			throw new APIException("Fixture 'visits' must be an array");
		}
		for (JsonNode visitNode : visitsNode) {
			String typeName = visitNode.path("type").asText(null);
			if (StringUtils.isBlank(typeName)) {
				throw new APIException("Fixture visit missing 'type'");
			}
			Date start = resolveDateOffset(visitNode.path("start"));
			Date stop = resolveDateOffset(visitNode.path("stop"));
			if (stop.before(start)) {
				throw new APIException("Fixture visit 'stop' is before 'start': " + visitNode);
			}
			String location = visitNode.path("location").asText(null);
			List<ResolvedEncounter> encounters = resolveEncounters(visitNode.path("encounters"), start, stop);
			resolved.add(new ResolvedVisit(start, stop, typeName, location, encounters));
		}
		return resolved;
	}
	
	private List<ResolvedEncounter> resolveEncounters(JsonNode encountersNode, Date visitStart, Date visitStop) {
		List<ResolvedEncounter> resolved = new ArrayList<>();
		if (encountersNode == null || encountersNode.isMissingNode() || encountersNode.isNull()) {
			return resolved;
		}
		if (!encountersNode.isArray()) {
			throw new APIException("Fixture visit 'encounters' must be an array");
		}
		for (JsonNode encounterNode : encountersNode) {
			String typeName = encounterNode.path("type").asText(null);
			if (StringUtils.isBlank(typeName)) {
				throw new APIException("Fixture encounter missing 'type'");
			}
			Date date = resolveDateOffset(encounterNode.path("date"));
			if (date.before(visitStart) || date.after(visitStop)) {
				throw new APIException("Fixture encounter date " + date
						+ " falls outside visit range [" + visitStart + ", " + visitStop + "]");
			}
			String providerRole = encounterNode.path("providerRole").asText("doctor");
			ResolvedVitals vitals = resolveVitals(encounterNode.path("vitals"));
			ResolvedBmi bmi = resolveBmi(encounterNode.path("bmi"));
			List<ResolvedNumericObs> labs = resolveLabs(encounterNode.path("labs"));
			List<DrugOrderDescriptor> drugOrders = resolveDrugOrders(encounterNode.path("drugOrders"), date);
			JsonNode noteNode = encounterNode.path("note");
			String noteText = noteNode.isMissingNode() || noteNode.isNull() ? null : noteNode.asText(null);
			List<ResolvedDiagnosis> diagnoses = resolveDiagnoses(encounterNode.path("diagnoses"));
			resolved.add(new ResolvedEncounter(date, typeName, providerRole, vitals, bmi, labs,
					drugOrders, noteText, diagnoses));
		}
		return resolved;
	}
	
	private ResolvedVitals resolveVitals(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) return null;
		return new ResolvedVitals(
				optionalDouble(node, "systolicBp"),
				optionalDouble(node, "diastolicBp"),
				optionalDouble(node, "heartRate"),
				optionalDouble(node, "respiratoryRate"),
				optionalDouble(node, "oxygenSaturation"),
				optionalDouble(node, "temperatureC"));
	}
	
	private ResolvedBmi resolveBmi(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) return null;
		return new ResolvedBmi(optionalDouble(node, "weightKg"), optionalDouble(node, "heightCm"));
	}
	
	private List<ResolvedNumericObs> resolveLabs(JsonNode node) {
		return resolveArray(node, "labs", entry -> new ResolvedNumericObs(
				resolveConcept(entry.path("concept").asText(null)).getUuid(),
				entry.path("value").asDouble()));
	}

	private List<ResolvedDiagnosis> resolveDiagnoses(JsonNode node) {
		return resolveArray(node, "diagnoses", entry -> new ResolvedDiagnosis(
				resolveConcept(entry.path("concept").asText(null)),
				entry.path("primary").asBoolean(false)));
	}
	
	private Double optionalDouble(JsonNode node, String field) {
		JsonNode v = node.path(field);
		return v.isMissingNode() || v.isNull() ? null : v.asDouble();
	}
	
	private List<DrugOrderDescriptor> resolveDrugOrders(JsonNode node, Date encounterDate) {
		return resolveArray(node, "drugOrders", entry -> resolveDrugOrder(entry, encounterDate));
	}

	private DrugOrderDescriptor resolveDrugOrder(JsonNode entry, Date encounterDate) {
		Concept drugConcept = resolveConcept(entry.path("drug").asText(null));
		Concept doseUnits = resolveConcept(entry.path("doseUnits").asText(null));
		Concept route = resolveConcept(entry.path("route").asText(null));
		Concept frequencyConcept = resolveConcept(entry.path("frequency").asText(null));
		Concept indication = null;
		String indicationId = entry.path("indication").asText(null);
		if (StringUtils.isNotBlank(indicationId)) {
			indication = resolveConcept(indicationId);
		}
		JsonNode doseNode = entry.path("doseValue");
		if (!doseNode.isNumber()) {
			throw new APIException("Fixture drug order 'doseValue' must be numeric, got: " + doseNode);
		}
		JsonNode startNode = entry.path("start");
		Date start = (startNode.isMissingNode() || startNode.isNull())
				? encounterDate : resolveDateOffset(startNode);
		Date autoExpire = null;
		JsonNode autoExpireNode = entry.path("autoExpire");
		if (!autoExpireNode.isMissingNode() && !autoExpireNode.isNull()) {
			autoExpire = resolveDateOffset(autoExpireNode);
		}

		Drug drug = firstDrugForConcept(drugConcept);
		String drugName = entry.path("drugName").asText(null);
		if (drug == null && StringUtils.isBlank(drugName)) {
			throw new APIException("Fixture drug order references concept '" + drugConcept.getUuid()
					+ "' (" + drugConcept.getName() + ") but no Drug row exists; "
					+ "either seed a Drug for this concept or set 'drugName' in the fixture");
		}

		return new DrugOrderDescriptor()
				.setDrugConcept(drugConcept)
				.setDrug(drug)
				.setDrugName(drugName)
				.setDoseValue(doseNode.asDouble())
				.setDoseUnits(doseUnits)
				.setRoute(route)
				.setFrequencyConcept(frequencyConcept)
				.setIndication(indication)
				.setStartDate(start)
				.setAutoExpireDate(autoExpire);
	}

	private Drug firstDrugForConcept(Concept drugConcept) {
		String key = drugConcept.getUuid();
		Optional<Drug> cached = drugByConceptUuidCache.get(key);
		if (cached != null) {
			return cached.orElse(null);
		}
		List<Drug> drugs = Context.getConceptService().getDrugsByConcept(drugConcept);
		Optional<Drug> resolved = (drugs == null || drugs.isEmpty()) ? Optional.empty() : Optional.of(drugs.get(0));
		drugByConceptUuidCache.put(key, resolved);
		return resolved.orElse(null);
	}
}
