/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.patient;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.map.DefaultedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.PatientService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.referencedemodata.obs.NumericObsValueDescriptor;
import org.openmrs.module.referencedemodata.obs.ObsValueGenerator;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.openmrs.module.referencedemodata.Randomizer.flipACoin;
import static org.openmrs.module.referencedemodata.Randomizer.randomArrayEntry;
import static org.openmrs.module.referencedemodata.Randomizer.randomBetween;
import static org.openmrs.module.referencedemodata.Randomizer.randomDoubleBetween;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_CODE_DIAGNOSIS_CERTAINTY;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_CODE_DIAGNOSIS_CONFIRMED;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_CODE_DIAGNOSIS_ORDER;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_CODE_DIAGNOSIS_ORDER_PRIMARY;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_CODE_DIAGNOSIS_ORDER_SECONDARY;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_CODE_DIAGNOSIS_PRESUMED;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_DIAGNOSIS_CONCEPT_SET;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.CONCEPT_DIAGNOSIS_LIST;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.OPENMRS_ID_NAME;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;
import static org.openmrs.module.referencedemodata.patient.DemoPersonGenerator.populatePerson;

@Getter
@Setter
public class DemoPatientGenerator {
	
	private static final Logger log = LoggerFactory.getLogger(DemoPatientGenerator.class);
	private static final int ADMISSION_DAYS_MIN = 1;
	private static final int ADMISSION_DAYS_MAX = 3;
	
	private static final String[] NOTE_TEXT = {
			"Lorem ipsum dolor sit amet",
			"consectetur adipisicing elit",
			"sed do eiusmod tempor incididunt",
			"ut labore et dolore magna aliqua",
			"Ut enim ad minim veniam",
			"quis nostrud exercitation ullamco laboris",
			"nisi ut aliquip ex ea commodo consequat.",
			"Duis aute irure dolor in reprehenderit in voluptat",
			"velit esse cillum dolore eu fugiat nulla pariatur.",
			"Excepteur sint occaecat cupidatat non proident",
			"sunt in culpa qui officia deserunt",
			"mollit anim id est laborum."
	};
	
	private static IdentifierSourceService iss;
	
	private static ConceptService cs;
	
	private static ObsService os;
	
	private List<Concept> allDiagnoses;
	
	private final ResourcePatternResolver patternResolver;
	
	private List<NumericObsValueDescriptor> vitalsDescriptors;
	
	private List<NumericObsValueDescriptor> labDescriptors;
	
	private Map<String, Concept> conceptCache = new DefaultedMap<>((key) -> {
		ConceptService cs = getConceptService();
		Concept concept = null;
		
		if (key.contains(":")) {
			String[] map = key.split(":", 2);
			if (map.length == 2 && StringUtils.isNotBlank(map[0]) && StringUtils.isNotBlank(map[1])) {
				try {
					concept = cs.getConceptByMapping(map[1], map[0]);
				}
				catch (Exception ignored) {
				
				}
			}
		}
		
		if (concept == null && key.length() >= 36) {
			try {
				concept = cs.getConceptByUuid(key);
			}
			catch (Exception ignored) {
			
			}
		}
		
		if (concept == null) {
			int conceptId = NumberUtils.toInt(key, -1);
			if (conceptId > 0) {
				try {
					concept = cs.getConcept(conceptId);
				}
				catch (Exception ignored) {
				
				}
			}
		}
		
		if (concept == null) {
			try {
				concept = cs.getConceptByName(key);
			}
			catch (Exception ignored) {
			
			}
		}
		
		if (concept == null) {
			throw new APIException("Could not find concept [" + key + "]");
		}
		
		return concept;
	});
	
	public DemoPatientGenerator(ResourcePatternResolver patternResolver)
			throws IOException {
		this.patternResolver = patternResolver;
		
		vitalsDescriptors = loadVitalsDescriptors(patternResolver);
		labDescriptors = loadLabDescriptors(patternResolver);
	}
	
	public void createDemoPatients(int patientCount) {
		// force the "created demo patient" below to show up
		OpenmrsUtil.applyLogLevel(DemoPatientGenerator.class.toString(), OpenmrsConstants.LOG_LEVEL_INFO);
		
		allDiagnoses = getConceptService().getConceptsByClass(getConceptService().getConceptClassByName("Diagnosis"));
		
		
		PatientService ps = Context.getPatientService();
		Location rootLocation = randomArrayEntry(Context.getLocationService().getRootLocations(false));
		PatientIdentifierType patientIdentifierType = ps.getPatientIdentifierTypeByName(OPENMRS_ID_NAME);
		
		for (int i = 0; i < patientCount; i++) {
			Patient patient = createDemoPatient(ps, patientIdentifierType, rootLocation);
			log.info("created demo patient: {} {} {}", patient.getPatientIdentifier(), patient.getGivenName(), patient.getFamilyName());
			Context.flushSession();
			Context.clearSession();
		}
	}
	
	private Patient createDemoPatient(PatientService ps, PatientIdentifierType patientIdentifierType, Location location) {
		Patient patient = createBasicDemoPatient(patientIdentifierType, location);
		patient = ps.savePatient(patient);
		
		VisitService vs = Context.getVisitService();
		int visitCount = randomBetween(0, 20);
		for (int i = 0; i < visitCount; i++) {
			boolean shortVisit = i < (visitCount * 0.8);
			Visit visit = createDemoVisit(patient, vs.getAllVisitTypes(), location, shortVisit);
			vs.saveVisit(visit);
		}
		
		return patient;
	}
	
	private Patient createBasicDemoPatient(PatientIdentifierType patientIdentifierType, Location location) {
		Patient patient = new Patient();
		
		populatePerson(patient);
		
		PatientIdentifier patientIdentifier = new PatientIdentifier();
		patientIdentifier.setIdentifier(getIdentifierSourceService().generateIdentifier(patientIdentifierType, "DemoData"));
		patientIdentifier.setIdentifierType(patientIdentifierType);
		patientIdentifier.setDateCreated(new Date());
		patientIdentifier.setLocation(location);
		patient.addIdentifier(patientIdentifier);
		
		return patient;
	}
	
	private Visit createDemoVisit(Patient patient, List<VisitType> visitTypes, Location location, boolean shortVisit) {
		LocalDateTime visitStart = LocalDateTime.now().minusDays(randomBetween(0, (365 * 5) - 1)).minusMinutes(randomBetween(0, 24 * 60));
		
		// just in case the start is today, back it up a few days.
		if (!shortVisit && LocalDate.now().minusDays(ADMISSION_DAYS_MAX + 1).isBefore(visitStart.toLocalDate())) {
			visitStart = visitStart.minusDays(ADMISSION_DAYS_MAX + 1);
		}
		
		Visit visit = new Visit(patient, randomArrayEntry(visitTypes), toDate(visitStart));
		visit.setLocation(location);
		
		LocalDateTime lastEncounterTime;
		
		int vitalsStartMinutes = randomBetween(0, 60);
		lastEncounterTime = visitStart.plusMinutes(vitalsStartMinutes);
		visit.addEncounter(createDemoVitalsEncounter(patient, toDate(lastEncounterTime)));
		
		lastEncounterTime = lastEncounterTime.plusMinutes(randomBetween(0, 120));
		visit.addEncounter(createVisitNote(patient, toDate(lastEncounterTime), location));
		
		if (shortVisit) {
			// roughly 2/3rds of "short" visits will have lab results
			if (randomDoubleBetween(0.0, 1.0) < .67) {
				int labStartMinutes = randomBetween(0, 120);
				lastEncounterTime = lastEncounterTime.plusMinutes(labStartMinutes);
			}
			
			LocalDateTime visitEndTime = lastEncounterTime.plusMinutes(randomBetween(20, 40));
			visit.setStopDatetime(toDate(visitEndTime));
		} else {
			// admit now and discharge a few days later
			Location admitLocation = Context.getLocationService().getLocation("Inpatient Ward");
			LocalDateTime admitTime = lastEncounterTime;
			visit.addEncounter(createEncounter("Admission", patient, toDate(admitTime), admitLocation));
			
			LocalDateTime dischargeTime = admitTime
					.plusDays(randomBetween(ADMISSION_DAYS_MIN, ADMISSION_DAYS_MAX))
					.plusMinutes(randomBetween(-12 * 30, 12 * 60));
			
			for (int i = 0; i < Duration.between(admitTime, dischargeTime).toDays(); i++) {
				lastEncounterTime = lastEncounterTime.plusDays(1)
						.plusMinutes(randomBetween(-12 * 60, 12 * 60));
				
				if (lastEncounterTime.isAfter(dischargeTime.minusHours(4))) {
					lastEncounterTime = dischargeTime.minusHours(4).minusMinutes(randomBetween(5, 60 * 12));
				}
				
				visit.addEncounter(createDemoVitalsEncounter(patient, toDate(lastEncounterTime), admitLocation));
				
				lastEncounterTime = lastEncounterTime.plusMinutes(randomBetween(0, 120));
				visit.addEncounter(createVisitNote(patient, toDate(lastEncounterTime), admitLocation));
				
				int labStartMinutes = randomBetween(0, 120);
				lastEncounterTime = lastEncounterTime.plusMinutes(labStartMinutes);
				visit.addEncounter(createDemoLabsEncounter(patient, toDate(lastEncounterTime), admitLocation));
			}
			
			visit.addEncounter(createEncounter("Discharge", patient, toDate(dischargeTime), admitLocation));
			visit.setStopDatetime(toDate(dischargeTime));
		}
		return visit;
	}
	
	private Encounter createVisitNote(Patient patient, Date encounterTime, Location location) {
		Encounter visitNote = createEncounter("Visit Note", patient, encounterTime, location);
		visitNote.setForm(Context.getFormService().getForm("Visit Note"));
		Context.getEncounterService().saveEncounter(visitNote);
		
		createTextObs("CIEL:162169", randomArrayEntry(NOTE_TEXT), patient, visitNote, encounterTime, location);
		
		// TODO 5% of diagnoses should be non-coded.
		if (allDiagnoses.size() > 0) {
			createDiagnosisObsGroup(true, patient, visitNote, encounterTime, location);
			
			if (flipACoin()) {
				// add a second diagnosis
				createDiagnosisObsGroup(false, patient, visitNote, encounterTime, location);
			}
		}
		
		return visitNote;
	}
	
	private void createDiagnosisObsGroup(boolean primary, Patient patient, Encounter visitNote, Date encounterTime,
			Location location) {
		Obs obsGroup = createBasicObs(CONCEPT_DIAGNOSIS_CONCEPT_SET, patient, encounterTime, location);
		visitNote.addObs(obsGroup);
		
		// Here we are going to make global property set to true. Reason, is while fetching concepts the case for concept name is different
		// from what we are sending to service call. This causes problem in PostgreSQL. This is not a problem in MySQL because For MySQL,
		// the collation is utf8_general_ci that is case insensitive so case does not matter but in other dbs like PostgreSQL it does.
		boolean valueBefore = Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive();
		Context.getAdministrationService().setGlobalProperty(OpenmrsConstants.GP_CASE_SENSITIVE_DATABASE_STRING_COMPARISON,
				"true");
		
		String certainty = flipACoin() ? CONCEPT_CODE_DIAGNOSIS_CONFIRMED : CONCEPT_CODE_DIAGNOSIS_PRESUMED;
		Obs obs1 = createCodedObs(CONCEPT_CODE_DIAGNOSIS_CERTAINTY, certainty, patient, visitNote, encounterTime, location);
		
		Obs obs2 = createCodedObs(CONCEPT_DIAGNOSIS_LIST, randomArrayEntry(allDiagnoses), patient, visitNote, encounterTime, location);
		
		String order = primary ? CONCEPT_CODE_DIAGNOSIS_ORDER_PRIMARY : CONCEPT_CODE_DIAGNOSIS_ORDER_SECONDARY;
		Obs obs3 = createCodedObs(CONCEPT_CODE_DIAGNOSIS_ORDER, order, patient, visitNote, encounterTime, location);
		
		obsGroup.addGroupMember(obs1);
		obsGroup.addGroupMember(obs2);
		obsGroup.addGroupMember(obs3);
		os.saveObs(obsGroup, null);
		
		// Restore the value of Global Property
		Context.getAdministrationService().setGlobalProperty(OpenmrsConstants.GP_CASE_SENSITIVE_DATABASE_STRING_COMPARISON,
				String.valueOf(valueBefore));
	}
	
	private Encounter createDemoVitalsEncounter(Patient patient, Date encounterTime) {
		Location location = Context.getLocationService().getLocation("Outpatient Clinic");
		return createDemoVitalsEncounter(patient, encounterTime, location);
	}
	
	private Encounter createDemoVitalsEncounter(Patient patient, Date encounterTime, Location location) {
		Encounter encounter = createEncounter("Vitals", patient, encounterTime, location);
		createDemoVitalsObs(patient, encounter, location);
		return encounter;
	}
	
	private Encounter createDemoLabsEncounter(Patient patient, Date encounterTime, Location location) {
		Encounter encounter = createEncounter("Lab Results", patient, encounterTime, location);
		createDemoLabObs(patient, encounter, location);
		return encounter;
	}
	
	private Encounter createEncounter(String encounterType, Patient patient, Date encounterTime, Location location) {
		EncounterService es = Context.getEncounterService();
		
		Encounter encounter = new Encounter();
		encounter.setEncounterDatetime(encounterTime);
		encounter.setEncounterType(es.getEncounterType(encounterType));
		encounter.setPatient(patient);
		encounter.setLocation(location);
		
		return es.saveEncounter(encounter);
	}
	
	private void createDemoVitalsObs(Patient patient, Encounter encounter, Location location) {
		for (NumericObsValueDescriptor vitalsDescriptor : vitalsDescriptors) {
			createNumericObsFromDescriptor(vitalsDescriptor, patient, encounter, location);
		}
	}
	
	private void createDemoLabObs(Patient patient, Encounter encounter, Location location) {
		for (NumericObsValueDescriptor labDescriptor : labDescriptors) {
			createNumericObsFromDescriptor(labDescriptor, patient, encounter, location);
		}
	}
	
	private void createNumericObsFromDescriptor(NumericObsValueDescriptor descriptor, Patient patient, Encounter encounter, Location location) {
		Obs previousObs = null;
		try {
			List<Obs> potentialObs = os.getObservations(
					Collections.singletonList(patient),
					null,
					Collections.singletonList(descriptor.getConcept()),
					null,
					null,
					null,
					null,
					1,
					null,
					null,
					null,
					false
			);
			
			if (potentialObs.size() > 0) {
				previousObs = potentialObs.get(0);
			}
		} catch (Exception ignored) {
		
		}
		
		Obs partialObs = ObsValueGenerator.createObsWithNumericValue(descriptor, previousObs != null ? previousObs.getValueNumeric() : null);
		createObs(partialObs, patient, encounter, encounter.getEncounterDatetime(), location);
	}
	
	private void createObs(Obs partialObs, Patient patient, Encounter encounter, Date encounterTime, Location location) {
		partialObs.setPerson(patient);
		partialObs.setEncounter(encounter);
		partialObs.setObsDatetime(encounterTime);
		partialObs.setLocation(location);
		
		getObsService().saveObs(partialObs, null);
	}
	
	private void createTextObs(String conceptName, String text, Patient patient, Encounter encounter, Date encounterTime,
			Location location) {
		Obs obs = createBasicObs(conceptName, patient, encounterTime, location);
		obs.setValueText(text);
		encounter.addObs(obs);
		getObsService().saveObs(obs, null);
	}
	
	private Obs createCodedObs(String conceptName, String codedConceptName, Patient patient, Encounter encounter, Date encounterTime,
			Location location) {
		return createCodedObs(conceptName, findConcept(codedConceptName), patient, encounter, encounterTime, location);
	}
	
	private Obs createCodedObs(String conceptName, Concept concept, Patient patient, Encounter encounter,
			Date encounterTime, Location location) {
		
		Obs obs = createBasicObs(conceptName, patient, encounterTime, location);
		obs.setValueCoded(concept);
		encounter.addObs(obs);
		
		getObsService().saveObs(obs, null);
		return obs;
	}
	
	private Obs createBasicObs(String conceptName, Patient patient, Date encounterTime, Location location) {
		Concept concept = findConcept(conceptName);
		if (concept == null) {
			log.warn("incorrect concept identifier? [{}]", conceptName);
		}
		
		return new Obs(patient, concept, encounterTime, location);
	}
	
	private Concept findConcept(String conceptDescriptor) {
		return getConceptCache().get(conceptDescriptor);
	}
	
	private List<NumericObsValueDescriptor> loadVitalsDescriptors(ResourcePatternResolver patternResolver)
			throws IOException {
		return loadObsValueDescriptorsFor(patternResolver, "classpath:vitalsValueDescriptors/*.json");
	}
	
	private List<NumericObsValueDescriptor> loadLabDescriptors(ResourcePatternResolver patternResolver)
			throws IOException {
		return loadObsValueDescriptorsFor(patternResolver, "classpath*:labValueDescriptors/*.json");
	}
	
	private List<NumericObsValueDescriptor> loadObsValueDescriptorsFor(ResourcePatternResolver patternResolver,
			String resourcePattern) throws IOException {
		ObjectMapper om = new ObjectMapper();
		ObjectReader reader = om.readerFor(NumericObsValueDescriptor.class);
		return Arrays.stream(patternResolver.getResources(resourcePattern))
				.map(r -> {
					try {
						return Optional.of(r.getInputStream());
					}
					catch (IOException e) {
						log.warn("Exception caught while attempting to read [{}]", r.getFilename(), e);
						return Optional.<InputStream>empty();
					}
				}).filter(Optional::isPresent)
				.map(Optional::get)
				.map(is -> {
					try {
						return Optional.<NumericObsValueDescriptor>of(reader.readValue(is));
					} catch (IOException e) {
						log.warn("Exception caught while attempting to parse file", e);
						return Optional.<NumericObsValueDescriptor>empty();
					}
				})
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());
	}
	
	private IdentifierSourceService getIdentifierSourceService() {
		if (iss == null) {
			iss = Context.getService(IdentifierSourceService.class);
		}
		
		return iss;
	}
	
	// exists for testing...
	public static void setIdentifierSourceService(IdentifierSourceService iss) {
		DemoPatientGenerator.iss = iss;
	}
	
	private ConceptService getConceptService() {
		if (cs == null) {
			cs = Context.getConceptService();
		}
		
		return cs;
	}
	
	public static void setConceptService(ConceptService cs) {
		DemoPatientGenerator.cs = cs;
	}
	
	private ObsService getObsService() {
		if (os == null) {
			os = Context.getObsService();
		}
		
		return os;
	}
	
	public static void setObsService(ObsService os) {
		DemoPatientGenerator.os = os;
	}
	
}
