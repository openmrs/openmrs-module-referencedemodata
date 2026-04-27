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

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.ConditionClinicalStatus;
import org.openmrs.Drug;
import org.openmrs.Encounter;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.OrderFrequency;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.referencedemodata.DemoDataConceptCache;
import org.openmrs.module.referencedemodata.Randomizer;
import org.openmrs.module.referencedemodata.condition.DemoConditionGenerator;
import org.openmrs.module.referencedemodata.diagnosis.DemoDiagnosisGenerator;
import org.openmrs.module.referencedemodata.obs.DemoObsGenerator;
import org.openmrs.module.referencedemodata.orders.DemoOrderGenerator;
import org.openmrs.module.referencedemodata.orders.DemoOrderGenerator.DrugOrderSpec;
import org.openmrs.module.referencedemodata.providers.DemoProviderGenerator;

import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.DEMO_PATIENT_ATTR;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.OPENMRS_ID_NAME;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;
import static org.openmrs.module.referencedemodata.patient.DemoPersonGenerator.populatePerson;

/**
 * Loads a deterministic demo patient (e.g. Devan Modi) from a JSON fixture on the classpath.
 * <p/>
 * The fixture describes the patient's demographics and full clinical history using relative date
 * offsets that are resolved against {@link LocalDate#now()} at load time. All concept references
 * (UUIDs or {@code CIEL:<code>} mappings) are resolved through {@link DemoDataConceptCache}; any
 * unresolved identifier fails loudly with an {@link APIException} and no partial patient is saved.
 */
@Slf4j
public class FixturePatientLoader {

	private static final String VISIT_NOTE_CONCEPT = "CIEL:162169";

	private static final Map<String, String> VITALS_CONCEPT_UUIDS;

	private static final Map<String, String> BMI_CONCEPT_UUIDS;

	static {
		Map<String, String> vitals = new LinkedHashMap<>();
		vitals.put("systolicBp", "5085AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		vitals.put("diastolicBp", "5086AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		vitals.put("heartRate", "5087AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		vitals.put("temperatureC", "5088AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		vitals.put("respiratoryRate", "5242AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		vitals.put("oxygenSaturation", "5092AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		VITALS_CONCEPT_UUIDS = Collections.unmodifiableMap(vitals);

		Map<String, String> bmi = new LinkedHashMap<>();
		bmi.put("weightKg", "5089AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		bmi.put("heightCm", "5090AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		BMI_CONCEPT_UUIDS = Collections.unmodifiableMap(bmi);
	}

	private final DemoDataConceptCache conceptCache;

	private final IdentifierSourceService iss;

	private final ObjectMapper objectMapper;

	private final DemoConditionGenerator conditionGenerator;

	private final DemoObsGenerator obsGenerator;

	private final DemoProviderGenerator providerGenerator;

	private final DemoOrderGenerator orderGenerator;

	private final DemoDiagnosisGenerator diagnosisGenerator;

	private EncounterRole clinicianRole;

	public FixturePatientLoader(DemoDataConceptCache conceptCache, IdentifierSourceService iss) {
		this(conceptCache, iss, new DemoConditionGenerator(), new DemoObsGenerator(conceptCache),
				new DemoProviderGenerator(), new DemoOrderGenerator(), null);
	}

	FixturePatientLoader(DemoDataConceptCache conceptCache, IdentifierSourceService iss,
			DemoConditionGenerator conditionGenerator, DemoObsGenerator obsGenerator,
			DemoProviderGenerator providerGenerator, DemoOrderGenerator orderGenerator,
			DemoDiagnosisGenerator diagnosisGenerator) {
		this.conceptCache = conceptCache;
		this.iss = iss;
		this.conditionGenerator = conditionGenerator;
		this.obsGenerator = obsGenerator;
		this.providerGenerator = providerGenerator;
		this.orderGenerator = orderGenerator;
		this.diagnosisGenerator = diagnosisGenerator != null ? diagnosisGenerator
				: new DemoDiagnosisGenerator(conceptCache, conditionGenerator);
		this.objectMapper = new ObjectMapper();
	}

	/**
	 * Loads the fixture at the given classpath location and creates the described patient if they do
	 * not already exist. If a patient with the fixture's UUID is already present the existing row is
	 * returned unchanged — clinical data (encounters, obs, orders, etc.) is only written on first
	 * load, so the caller must delete the patient to re-seed.
	 *
	 * @throws APIException if the resource is missing, unparseable, or references an unresolvable
	 *             concept
	 */
	public Patient loadFixture(String classpathResourcePath) {
		JsonNode root = readFixture(classpathResourcePath);

		String patientUuid = root.path("patientUuid").asText(null);
		if (StringUtils.isBlank(patientUuid)) {
			throw new APIException("Fixture [" + classpathResourcePath + "] is missing required field 'patientUuid'");
		}

		JsonNode demographics = root.path("demographics");
		if (demographics.isMissingNode() || !demographics.isObject()) {
			throw new APIException("Fixture [" + classpathResourcePath + "] is missing required object 'demographics'");
		}

		PatientService ps = Context.getPatientService();
		Patient existing = ps.getPatientByUuid(patientUuid);
		if (existing != null) {
			// WARN because a prior run that saved the patient row then failed mid-apply will leave
			// partial clinical data behind; operator must delete the patient UUID to reseed.
			log.warn("Fixture patient {} already exists (uuid={}, resource={}); skipping reload — clinical data is not re-applied",
					new Object[] { existing.getPersonName(), patientUuid, classpathResourcePath });
			return existing;
		}

		// Resolve concepts per clinical section before touching the patient row so missing concept
		// mappings fail loudly without creating a partial patient.
		List<ResolvedCondition> resolvedConditions = resolveConditions(root.path("conditions"));
		List<ResolvedObsGroup> resolvedVitals = resolveNumericObsGroups(root.path("vitals"), VITALS_CONCEPT_UUIDS);
		List<ResolvedObsGroup> resolvedBmi = resolveNumericObsGroups(root.path("bmi"), BMI_CONCEPT_UUIDS);
		List<ResolvedObsGroup> resolvedLabs = resolveLabs(root.path("labs"), root.path("labConcepts"));
		List<DrugOrderSpec> resolvedDrugOrders = resolveDrugOrders(root.path("drugOrders"));
		List<ResolvedVisitNote> resolvedVisitNotes = resolveVisitNotes(root.path("visits"));
		ResolvedNarrativeNote resolvedProcedureNote = resolveNarrativeNote(root.path("procedureNote"), true);
		ResolvedDischargeSummary resolvedDischargeSummary = resolveDischargeSummary(root.path("dischargeSummary"));

		PersonService personService = Context.getPersonService();

		PatientIdentifierType identifierType = ps.getPatientIdentifierTypeByName(OPENMRS_ID_NAME);
		if (identifierType == null) {
			throw new APIException("Could not find identifier type " + OPENMRS_ID_NAME);
		}

		PersonAttributeType demoAttrType = personService.getPersonAttributeTypeByName(DEMO_PATIENT_ATTR);
		if (demoAttrType == null) {
			demoAttrType = new PersonAttributeType();
			demoAttrType.setName(DEMO_PATIENT_ATTR);
			demoAttrType.setFormat("java.lang.Boolean");
			demoAttrType.setSearchable(true);
			demoAttrType = personService.savePersonAttributeType(demoAttrType);
		}

		Patient patient = new Patient();
		patient.setUuid(patientUuid);
		populatePerson(patient);
		applyDemographics(patient, demographics);

		Location rootLocation = Randomizer.randomListEntry(Context.getLocationService().getRootLocations(false));
		PatientIdentifier patientIdentifier = new PatientIdentifier();
		patientIdentifier.setIdentifier(iss.generateIdentifier(identifierType, "DemoData"));
		patientIdentifier.setIdentifierType(identifierType);
		patientIdentifier.setDateCreated(new Date());
		patientIdentifier.setLocation(rootLocation);
		patient.addIdentifier(patientIdentifier);

		PersonAttribute demoAttr = new PersonAttribute();
		demoAttr.setAttributeType(demoAttrType);
		demoAttr.setValue("true");
		patient.addAttribute(demoAttr);

		Patient saved = ps.savePatient(patient);

		applyConditions(saved, resolvedConditions);
		applyNumericObsEncounters(saved, resolvedVitals, "Vitals");
		applyNumericObsEncounters(saved, resolvedBmi, "Vitals");
		applyNumericObsEncounters(saved, resolvedLabs, "Lab Results");
		applyDrugOrders(saved, resolvedDrugOrders);
		applyVisitNotes(saved, resolvedVisitNotes);
		applyProcedureNote(saved, resolvedProcedureNote);
		applyDischargeSummary(saved, resolvedDischargeSummary);

		log.info("Loaded fixture patient {} {} (uuid={})",
				new Object[] { saved.getGivenName(), saved.getFamilyName(), saved.getUuid() });
		return saved;
	}

	private List<ResolvedCondition> resolveConditions(JsonNode conditionsNode) {
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

	private void applyConditions(Patient patient, List<ResolvedCondition> resolved) {
		for (ResolvedCondition rc : resolved) {
			conditionGenerator.createDatedCondition(patient, rc.concept, rc.onsetDate, rc.status);
		}
	}

	private ConditionClinicalStatus parseClinicalStatus(String raw) {
		if (StringUtils.isBlank(raw)) {
			return ConditionClinicalStatus.ACTIVE;
		}
		try {
			return ConditionClinicalStatus.valueOf(raw.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			throw new APIException("Fixture condition has unknown clinicalStatus: " + raw, e);
		}
	}

	private static final class ResolvedCondition {

		final Concept concept;

		final Date onsetDate;

		final ConditionClinicalStatus status;

		ResolvedCondition(Concept concept, Date onsetDate, ConditionClinicalStatus status) {
			this.concept = concept;
			this.onsetDate = onsetDate;
			this.status = status;
		}
	}

	private static final class ResolvedObsGroup {

		final Date date;

		final List<ResolvedNumericObs> values;

		ResolvedObsGroup(Date date, List<ResolvedNumericObs> values) {
			this.date = date;
			this.values = values;
		}
	}

	private static final class ResolvedNumericObs {

		final String conceptUuid;

		final Number value;

		ResolvedNumericObs(String conceptUuid, Number value) {
			this.conceptUuid = conceptUuid;
			this.value = value;
		}
	}

	private List<ResolvedObsGroup> resolveNumericObsGroups(JsonNode arrayNode, Map<String, String> fieldToConceptUuid) {
		List<ResolvedObsGroup> resolved = new ArrayList<>();
		if (arrayNode == null || arrayNode.isMissingNode() || arrayNode.isNull()) {
			return resolved;
		}
		if (!arrayNode.isArray()) {
			throw new APIException("Fixture expected array but found: " + arrayNode);
		}
		for (JsonNode entry : arrayNode) {
			Date date = resolveDateOffset(entry.path("date"));
			List<ResolvedNumericObs> values = new ArrayList<>();
			for (Map.Entry<String, String> field : fieldToConceptUuid.entrySet()) {
				JsonNode valueNode = entry.path(field.getKey());
				if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
					continue;
				}
				if (!valueNode.isNumber()) {
					throw new APIException("Fixture field '" + field.getKey() + "' must be numeric, got: " + valueNode);
				}
				resolveConcept(field.getValue());
				values.add(new ResolvedNumericObs(field.getValue(), valueNode.numberValue()));
			}
			resolved.add(new ResolvedObsGroup(date, values));
		}
		return resolved;
	}

	private List<ResolvedObsGroup> resolveLabs(JsonNode labsNode, JsonNode labConceptsNode) {
		List<ResolvedObsGroup> resolved = new ArrayList<>();
		if (labsNode == null || labsNode.isMissingNode() || labsNode.isNull()) {
			return resolved;
		}
		if (!labsNode.isArray()) {
			throw new APIException("Fixture 'labs' must be an array");
		}
		if (labConceptsNode == null || labConceptsNode.isMissingNode() || !labConceptsNode.isObject()) {
			throw new APIException("Fixture 'labConcepts' must be an object mapping lab field names to concept identifiers");
		}

		Map<String, String> fieldToConcept = new LinkedHashMap<>();
		Iterator<Map.Entry<String, JsonNode>> mappingFields = labConceptsNode.fields();
		while (mappingFields.hasNext()) {
			Map.Entry<String, JsonNode> f = mappingFields.next();
			fieldToConcept.put(f.getKey(), f.getValue().asText());
		}

		for (JsonNode entry : labsNode) {
			Date date = resolveDateOffset(entry.path("date"));
			JsonNode valuesNode = entry.path("values");
			if (!valuesNode.isObject()) {
				throw new APIException("Fixture lab entry missing 'values' object: " + entry);
			}
			Iterator<String> valueKeys = valuesNode.fieldNames();
			while (valueKeys.hasNext()) {
				String key = valueKeys.next();
				if (!fieldToConcept.containsKey(key)) {
					throw new APIException("Fixture lab value '" + key + "' has no mapping in labConcepts");
				}
			}
			List<ResolvedNumericObs> values = new ArrayList<>();
			for (Map.Entry<String, String> field : fieldToConcept.entrySet()) {
				JsonNode valueNode = valuesNode.path(field.getKey());
				if (valueNode.isMissingNode() || valueNode.isNull()) {
					continue;
				}
				if (!valueNode.isNumber()) {
					throw new APIException("Fixture lab value '" + field.getKey() + "' must be numeric, got: " + valueNode);
				}
				Concept concept = resolveConcept(field.getValue());
				values.add(new ResolvedNumericObs(concept.getUuid(), valueNode.numberValue()));
			}
			resolved.add(new ResolvedObsGroup(date, values));
		}
		return resolved;
	}

	private void applyNumericObsEncounters(Patient patient, List<ResolvedObsGroup> groups, String encounterTypeName) {
		if (groups.isEmpty()) {
			return;
		}
		EncounterType encounterType = ensureEncounterType(encounterTypeName);
		EncounterRole clinicianRole = getClinicianRole();
		Location location = resolveDefaultLocation();
		Provider provider = providerGenerator.getRandomClinician();

		for (ResolvedObsGroup group : groups) {
			Encounter encounter = createDatedEncounter(patient, group.date, encounterType, location, provider, clinicianRole);
			for (ResolvedNumericObs obs : group.values) {
				obsGenerator.createNumericObs(obs.conceptUuid, obs.value, patient, encounter, group.date, location);
			}
		}
	}

	private EncounterType ensureEncounterType(String name) {
		EncounterService es = Context.getEncounterService();
		EncounterType encounterType = es.getEncounterType(name);
		if (encounterType == null) {
			encounterType = new EncounterType(name, "");
			encounterType.setDateCreated(new Date());
			encounterType = es.saveEncounterType(encounterType);
		}
		return encounterType;
	}

	private Location resolveDefaultLocation() {
		Location location = Context.getLocationService().getLocation("Outpatient Clinic");
		if (location != null) {
			return location;
		}
		List<Location> roots = Context.getLocationService().getRootLocations(false);
		return roots.isEmpty() ? null : Randomizer.randomListEntry(roots);
	}

	private Encounter createDatedEncounter(Patient patient, Date encounterTime, EncounterType encounterType,
			Location location, Provider provider, EncounterRole clinicianRole) {
		Encounter encounter = new Encounter();
		encounter.setEncounterDatetime(encounterTime);
		encounter.setEncounterType(encounterType);
		encounter.setPatient(patient);
		encounter.setLocation(location);
		if (provider != null && clinicianRole != null) {
			encounter.addProvider(clinicianRole, provider);
		}
		return Context.getEncounterService().saveEncounter(encounter);
	}

	private EncounterRole getClinicianRole() {
		if (clinicianRole == null) {
			clinicianRole = Context.getEncounterService().getEncounterRoleByName("Clinician");
		}
		return clinicianRole;
	}

	protected Concept resolveConcept(String conceptIdentifier) {
		if (StringUtils.isBlank(conceptIdentifier)) {
			throw new APIException("Fixture references blank concept identifier");
		}
		return conceptCache.findConcept(conceptIdentifier);
	}

	/**
	 * Resolves a date-offset node (keys: {@code yearsAgo}, {@code monthsAgo}, {@code weeksAgo},
	 * {@code weeksAgoOffset}, {@code daysAgo}; each defaults to 0) to a {@link Date} anchored at
	 * {@link LocalDate#now()}. Negative values move the date forward.
	 */
	protected Date resolveDateOffset(JsonNode offsetNode) {
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

	private JsonNode readFixture(String classpathResourcePath) {
		String normalized = classpathResourcePath.startsWith("/") ? classpathResourcePath : "/" + classpathResourcePath;
		try (InputStream in = getClass().getResourceAsStream(normalized)) {
			if (in == null) {
				throw new APIException("Fixture resource not found on classpath: " + classpathResourcePath);
			}
			return objectMapper.readTree(in);
		}
		catch (IOException e) {
			throw new APIException("Failed to read fixture [" + classpathResourcePath + "]", e);
		}
	}

	private List<DrugOrderSpec> resolveDrugOrders(JsonNode drugOrdersNode) {
		List<DrugOrderSpec> resolved = new ArrayList<>();
		if (drugOrdersNode == null || drugOrdersNode.isMissingNode() || drugOrdersNode.isNull()) {
			return resolved;
		}
		if (!drugOrdersNode.isObject()) {
			throw new APIException("Fixture 'drugOrders' must be an object with 'active' and/or 'inactive' arrays");
		}
		resolveDrugOrderArray(drugOrdersNode.path("active"), false, resolved);
		resolveDrugOrderArray(drugOrdersNode.path("inactive"), true, resolved);
		return resolved;
	}

	private void resolveDrugOrderArray(JsonNode arrayNode, boolean inactive, List<DrugOrderSpec> target) {
		if (arrayNode == null || arrayNode.isMissingNode() || arrayNode.isNull()) {
			return;
		}
		if (!arrayNode.isArray()) {
			throw new APIException("Fixture 'drugOrders' section must be an array");
		}
		ConceptService conceptService = Context.getConceptService();
		for (JsonNode entry : arrayNode) {
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
			Date start = resolveDateOffset(entry.path("start"));
			Date autoExpire = null;
			if (inactive) {
				JsonNode autoExpireNode = entry.path("autoExpire");
				if (autoExpireNode.isMissingNode() || autoExpireNode.isNull()) {
					throw new APIException("Fixture inactive drug order missing 'autoExpire' offset: " + entry);
				}
				autoExpire = resolveDateOffset(autoExpireNode);
			}

			target.add(new DrugOrderSpec()
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

	private List<ResolvedVisitNote> resolveVisitNotes(JsonNode visitsNode) {
		List<ResolvedVisitNote> resolved = new ArrayList<>();
		if (visitsNode == null || visitsNode.isMissingNode() || visitsNode.isNull()) {
			return resolved;
		}
		if (!visitsNode.isArray()) {
			throw new APIException("Fixture 'visits' must be an array");
		}
		resolveConcept(VISIT_NOTE_CONCEPT);
		for (JsonNode entry : visitsNode) {
			Date date = resolveDateOffset(entry.path("date"));
			String note = entry.path("note").asText(null);
			if (StringUtils.isBlank(note)) {
				throw new APIException("Fixture visit entry missing 'note' text: " + entry);
			}
			String providerRole = entry.path("providerRole").asText("doctor");
			Concept diagnosisConcept = null;
			String diagnosisId = entry.path("diagnosisConcept").asText(null);
			if (StringUtils.isNotBlank(diagnosisId)) {
				diagnosisConcept = resolveConcept(diagnosisId);
			}
			resolved.add(new ResolvedVisitNote(date, note, providerRole, diagnosisConcept));
		}
		return resolved;
	}

	private void applyVisitNotes(Patient patient, List<ResolvedVisitNote> visitNotes) {
		if (visitNotes.isEmpty()) {
			return;
		}
		EncounterType encounterType = ensureEncounterType("Visit Note");
		EncounterRole clinicianRole = getClinicianRole();
		Location location = resolveDefaultLocation();

		for (ResolvedVisitNote vn : visitNotes) {
			Provider provider = resolveProviderForRole(vn.providerRole);
			Encounter encounter = createDatedEncounter(patient, vn.date, encounterType, location, provider, clinicianRole);
			obsGenerator.createTextObs(VISIT_NOTE_CONCEPT, vn.note, patient, encounter, vn.date, location);
			if (vn.diagnosisConcept != null) {
				diagnosisGenerator.createDiagnosis(true, patient, encounter, vn.diagnosisConcept);
			}
		}
	}

	private Provider resolveProviderForRole(String providerRole) {
		if ("nurse".equalsIgnoreCase(providerRole)) {
			return providerGenerator.getNurse();
		}
		return providerGenerator.getDoctor();
	}

	private static final class ResolvedVisitNote {

		final Date date;

		final String note;

		final String providerRole;

		final Concept diagnosisConcept;

		ResolvedVisitNote(Date date, String note, String providerRole, Concept diagnosisConcept) {
			this.date = date;
			this.note = note;
			this.providerRole = providerRole;
			this.diagnosisConcept = diagnosisConcept;
		}
	}

	private void applyDrugOrders(Patient patient, List<DrugOrderSpec> specs) {
		if (specs.isEmpty()) {
			return;
		}
		EncounterType encounterType = ensureEncounterType("Order");
		EncounterRole clinicianRole = getClinicianRole();
		Location location = resolveDefaultLocation();
		Provider provider = providerGenerator.getRandomClinician();

		for (DrugOrderSpec spec : specs) {
			Encounter encounter = createDatedEncounter(patient, spec.getStartDate(), encounterType, location,
					provider, clinicianRole);
			orderGenerator.createDatedDrugOrder(encounter, spec);
		}
	}

	private ResolvedNarrativeNote resolveNarrativeNote(JsonNode node, boolean requireDiagnosis) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		if (!node.isObject()) {
			throw new APIException("Fixture narrative note must be an object: " + node);
		}
		Date date = resolveDateOffset(node.path("date"));
		String note = node.path("note").asText(null);
		if (StringUtils.isBlank(note)) {
			throw new APIException("Fixture narrative note missing 'note' text: " + node);
		}
		String providerRole = node.path("providerRole").asText("doctor");
		resolveConcept(VISIT_NOTE_CONCEPT);

		Concept diagnosisConcept = null;
		String diagnosisId = node.path("diagnosisConcept").asText(null);
		if (StringUtils.isNotBlank(diagnosisId)) {
			diagnosisConcept = resolveConcept(diagnosisId);
		} else if (requireDiagnosis) {
			throw new APIException("Fixture narrative note missing required 'diagnosisConcept': " + node);
		}
		return new ResolvedNarrativeNote(date, note, providerRole, diagnosisConcept);
	}

	private ResolvedDischargeSummary resolveDischargeSummary(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		if (!node.isObject()) {
			throw new APIException("Fixture 'dischargeSummary' must be an object: " + node);
		}
		Date date = resolveDateOffset(node.path("encounterDate"));
		String note = node.path("note").asText(null);
		if (StringUtils.isBlank(note)) {
			throw new APIException("Fixture 'dischargeSummary' missing 'note' text");
		}
		String providerRole = node.path("providerRole").asText("doctor");
		resolveConcept(VISIT_NOTE_CONCEPT);

		String primaryId = node.path("primaryDiagnosisConcept").asText(null);
		if (StringUtils.isBlank(primaryId)) {
			throw new APIException("Fixture 'dischargeSummary' missing 'primaryDiagnosisConcept'");
		}
		Concept primaryDiagnosis = resolveConcept(primaryId);

		Concept secondaryDiagnosis = null;
		String secondaryId = node.path("secondaryDiagnosisConcept").asText(null);
		if (StringUtils.isNotBlank(secondaryId)) {
			secondaryDiagnosis = resolveConcept(secondaryId);
		}
		return new ResolvedDischargeSummary(date, note, providerRole, primaryDiagnosis, secondaryDiagnosis);
	}

	private void applyProcedureNote(Patient patient, ResolvedNarrativeNote resolved) {
		if (resolved == null) {
			return;
		}
		EncounterType encounterType = ensureEncounterType("Procedure Note");
		EncounterRole clinicianRole = getClinicianRole();
		Location location = resolveDefaultLocation();
		Provider provider = resolveProviderForRole(resolved.providerRole);

		Encounter encounter = createDatedEncounter(patient, resolved.date, encounterType, location, provider, clinicianRole);
		obsGenerator.createTextObs(VISIT_NOTE_CONCEPT, resolved.note, patient, encounter, resolved.date, location);
		if (resolved.diagnosisConcept != null) {
			diagnosisGenerator.createDiagnosis(true, patient, encounter, resolved.diagnosisConcept);
		}
	}

	private void applyDischargeSummary(Patient patient, ResolvedDischargeSummary resolved) {
		if (resolved == null) {
			return;
		}
		EncounterType encounterType = resolveDischargeEncounterType();
		EncounterRole clinicianRole = getClinicianRole();
		Location location = resolveDefaultLocation();
		Provider provider = resolveProviderForRole(resolved.providerRole);

		Encounter encounter = createDatedEncounter(patient, resolved.date, encounterType, location, provider, clinicianRole);
		obsGenerator.createTextObs(VISIT_NOTE_CONCEPT, resolved.note, patient, encounter, resolved.date, location);
		diagnosisGenerator.createDiagnosis(true, patient, encounter, resolved.primaryDiagnosis);
		if (resolved.secondaryDiagnosis != null) {
			diagnosisGenerator.createDiagnosis(false, patient, encounter, resolved.secondaryDiagnosis);
		}
	}

	private EncounterType resolveDischargeEncounterType() {
		EncounterType existing = Context.getEncounterService().getEncounterType("Discharge Summary");
		return existing != null ? existing : ensureEncounterType("Visit Note");
	}

	private static final class ResolvedNarrativeNote {

		final Date date;

		final String note;

		final String providerRole;

		final Concept diagnosisConcept;

		ResolvedNarrativeNote(Date date, String note, String providerRole, Concept diagnosisConcept) {
			this.date = date;
			this.note = note;
			this.providerRole = providerRole;
			this.diagnosisConcept = diagnosisConcept;
		}
	}

	private static final class ResolvedDischargeSummary {

		final Date date;

		final String note;

		final String providerRole;

		final Concept primaryDiagnosis;

		final Concept secondaryDiagnosis;

		ResolvedDischargeSummary(Date date, String note, String providerRole, Concept primaryDiagnosis,
				Concept secondaryDiagnosis) {
			this.date = date;
			this.note = note;
			this.providerRole = providerRole;
			this.primaryDiagnosis = primaryDiagnosis;
			this.secondaryDiagnosis = secondaryDiagnosis;
		}
	}

	private void applyDemographics(Patient patient, JsonNode demographics) {
		String givenName = demographics.path("givenName").asText(null);
		String familyName = demographics.path("familyName").asText(null);
		String gender = demographics.path("gender").asText(null);

		if (StringUtils.isBlank(givenName) || StringUtils.isBlank(familyName) || StringUtils.isBlank(gender)) {
			throw new APIException("Fixture demographics must provide givenName, familyName, and gender");
		}

		JsonNode ageNode = demographics.path("ageYearsAgo");
		if (!ageNode.canConvertToInt()) {
			throw new APIException("Fixture demographics.ageYearsAgo must be an integer");
		}
		int ageYearsAgo = ageNode.asInt();

		PersonName personName = patient.getPersonName();
		if (personName == null) {
			personName = new PersonName();
			patient.addName(personName);
		}
		personName.setGivenName(givenName);
		personName.setFamilyName(familyName);

		patient.setGender(gender);
		patient.setBirthdate(toDate(LocalDate.now().minusYears(ageYearsAgo)));
		patient.setBirthdateEstimated(false);
	}
}
