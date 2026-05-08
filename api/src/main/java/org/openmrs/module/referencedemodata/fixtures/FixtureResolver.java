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
import org.openmrs.Drug;
import org.openmrs.OrderFrequency;
import org.openmrs.VisitType;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.referencedemodata.DemoDataConceptCache;
import org.openmrs.module.referencedemodata.orders.DrugOrderDescriptor;

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
			validateVisitType(typeName);
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
	
	private void validateVisitType(String name) {
		for (VisitType vt : Context.getVisitService().getAllVisitTypes()) {
			if (vt.getName().equalsIgnoreCase(name)) return;
		}
		throw new APIException("Fixture references unknown visit type: " + name
				+ ". Visit types are platform-managed; ensure standard types exist.");
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
		if (node == null || node.isMissingNode() || node.isNull()) return null;
		if (!node.isArray()) throw new APIException("Fixture encounter 'labs' must be an array");
		List<ResolvedNumericObs> resolved = new ArrayList<>();
		for (JsonNode entry : node) {
			String conceptId = entry.path("concept").asText(null);
			Concept concept = resolveConcept(conceptId);
			double value = entry.path("value").asDouble();
			resolved.add(new ResolvedNumericObs(concept.getUuid(), value));
		}
		return resolved;
	}
	
	private List<ResolvedDiagnosis> resolveDiagnoses(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) return null;
		if (!node.isArray()) throw new APIException("Fixture encounter 'diagnoses' must be an array");
		List<ResolvedDiagnosis> resolved = new ArrayList<>();
		for (JsonNode entry : node) {
			String conceptId = entry.path("concept").asText(null);
			Concept concept = resolveConcept(conceptId);
			boolean primary = entry.path("primary").asBoolean(false);
			resolved.add(new ResolvedDiagnosis(concept, primary));
		}
		return resolved;
	}
	
	private Double optionalDouble(JsonNode node, String field) {
		JsonNode v = node.path(field);
		return v.isMissingNode() || v.isNull() ? null : v.asDouble();
	}
	
	private List<DrugOrderDescriptor> resolveDrugOrders(JsonNode node, Date encounterDate) {
		if (node == null || node.isMissingNode() || node.isNull()) return null;
		if (!node.isArray()) throw new APIException("Fixture encounter 'drugOrders' must be an array");
		List<DrugOrderDescriptor> resolved = new ArrayList<>();
		ConceptService conceptService = Context.getConceptService();
		for (JsonNode entry : node) {
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
			OrderFrequency frequency = resolveOrderFrequency(frequencyConcept);
			Drug drug = firstDrugForConcept(conceptService, drugConcept);
			
			JsonNode startNode = entry.path("start");
			Date start = (startNode.isMissingNode() || startNode.isNull())
					? encounterDate : resolveDateOffset(startNode);
			Date autoExpire = null;
			JsonNode autoExpireNode = entry.path("autoExpire");
			if (!autoExpireNode.isMissingNode() && !autoExpireNode.isNull()) {
				autoExpire = resolveDateOffset(autoExpireNode);
			}
			
			resolved.add(new DrugOrderDescriptor()
					.setDrugConcept(drugConcept)
					.setDrug(drug)
					.setDrugName(entry.path("drugName").asText(null))
					.setDoseValue(doseNode.asDouble())
					.setDoseUnits(doseUnits)
					.setRoute(route)
					.setFrequency(frequency)
					.setIndication(indication)
					.setStartDate(start)
					.setAutoExpireDate(autoExpire));
		}
		return resolved;
	}
	
	private OrderFrequency resolveOrderFrequency(Concept frequencyConcept) {
		OrderService orderService = Context.getOrderService();
		OrderFrequency frequency = orderService.getOrderFrequencyByConcept(frequencyConcept);
		if (frequency == null) {
			frequency = new OrderFrequency();
			frequency.setConcept(frequencyConcept);
			frequency = orderService.saveOrderFrequency(frequency);
		}
		return frequency;
	}
	
	private Drug firstDrugForConcept(ConceptService conceptService, Concept drugConcept) {
		List<Drug> drugs = conceptService.getDrugsByConcept(drugConcept);
		return (drugs == null || drugs.isEmpty()) ? null : drugs.get(0);
	}
}
