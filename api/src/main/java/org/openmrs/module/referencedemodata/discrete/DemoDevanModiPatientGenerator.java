/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.discrete;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.openmrs.Condition;
import org.openmrs.Diagnosis;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.api.EncounterService;
import org.openmrs.api.FormService;
import org.openmrs.api.LocationService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.referencedemodata.condition.DemoConditionGenerator;
import org.openmrs.module.referencedemodata.diagnosis.DemoDiagnosisGenerator;
import org.openmrs.module.referencedemodata.obs.DemoObsGenerator;
import org.openmrs.module.referencedemodata.obs.NumericObsValueDescriptor;
import org.openmrs.module.referencedemodata.orders.DemoOrderGenerator;
import org.openmrs.module.referencedemodata.orders.DrugOrderDescriptor;
import org.openmrs.module.referencedemodata.orders.DrugOrderGenerator;
import org.openmrs.module.referencedemodata.patient.DemoPatientGenerator;
import org.openmrs.module.referencedemodata.providers.DemoProviderGenerator;
import org.openmrs.module.referencedemodata.visit.DemoVisitGenerator;

import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toLocalDate;

@Slf4j
public class DemoDevanModiPatientGenerator {
	
	private final DemoPatientGenerator demoPatientGenerator;
	
	private final DemoProviderGenerator providerGenerator;
	
	private final DemoObsGenerator obsGenerator;
	
	private final DemoOrderGenerator orderGenerator;
	
	private final DemoDiagnosisGenerator diagnosisGenerator;
	
	private final DemoVisitGenerator visitGenerator;
	
	private final Date today;
	
	private FormService fs = null;
	
	private EncounterService es = null;
	
	private VisitService vs = null;
	
	private LocationService ls = null;

	private DemoConditionGenerator conditionGenerator;
	
	public DemoDevanModiPatientGenerator(DemoPatientGenerator demoPatientGenerator, DemoProviderGenerator providerGenerator, DemoObsGenerator obsGenerator, DemoOrderGenerator orderGenerator, DemoDiagnosisGenerator diagnosisGenerator, DemoVisitGenerator visitGenerator, DemoConditionGenerator conditionGenerator) {
		this.visitGenerator = visitGenerator;
		this.providerGenerator = providerGenerator;
		this.obsGenerator = obsGenerator;
		this.orderGenerator = orderGenerator;
		this.diagnosisGenerator = diagnosisGenerator;
		this.demoPatientGenerator = demoPatientGenerator;
		this.conditionGenerator = conditionGenerator;
		this.today = new Date();
	}
	
	/**
	 *
	 */
	public void create() {
		List<Integer> id = demoPatientGenerator.createDemoPatients(1);
		Patient patient = Context.getPatientService().getPatient(id.get(0));
		patient.setBirthdateFromAge(65, toDate(LocalDateTime.now().minusDays(90)));
		PersonName name = patient.getPersonName();
		name.setFamilyName("Devan");
		name.setGivenName("Modi");
		
	}
	
	private void createVisits(Patient patient) {
		// Visit 5 years ago
		
		// Visit 3 years ago
		
		// Visit 2 months and 11 weeks ago
		
		// Visit 1 month ago

	}
	
	private void createDemoVisit5YearsAgo(Patient patient) throws Exception {
		Visit visit5YearsAgo = new Visit(patient, vs.getVisitTypeByUuid(""), toDate(LocalDateTime.now().minusYears(5)));
		visit5YearsAgo.setLocation(ls.getLocationByUuid(""));
		
		Provider visitProvider = providerGenerator.getRandomClinician();
		Location location = Context.getLocationService().getLocation("Outpatient Clinic");
		Encounter vitalsEncounter = visitGenerator.createDemoVitalsEncounter(patient, toDate(toLocalDate(visit5YearsAgo.getStartDatetime())), location, visitProvider);
		createDemoVitalsObs(patient, vitalsEncounter, location, "");
		// save encounter before adding it to visit
		es.saveEncounter(vitalsEncounter);
		visit5YearsAgo.addEncounter(vitalsEncounter);
		// add lab orders' encounter
		Encounter labOrderEncounter = visitGenerator.createEncounter("Lab Order", patient, toDate(toLocalDate(visit5YearsAgo.getStartDatetime())), location, visitProvider);
		// add lab orders to encounter
		orderGenerator.createDemoTestOrder(labOrderEncounter, null);
		
		// add consultation encounter
		Encounter consultationEncounter = visitGenerator.createEncounter("Consultation", patient, toDate(toLocalDate(visit5YearsAgo.getStartDatetime())), location, visitProvider);
		// add consutlation details i.e. drugs prescription
		
		es.saveEncounter(consultationEncounter);
		visit5YearsAgo.addEncounter(consultationEncounter);
		
		// add visit note
		Encounter visitNoteEncounter = visitGenerator.createEncounter("Visit Note", patient, toDate(toLocalDate(visit5YearsAgo.getStartDatetime())), location, visitProvider);
		visitNoteEncounter.setForm(visitGenerator.getVisitNoteForm());
		es.saveEncounter(visitNoteEncounter);
		
		obsGenerator.createTextObs("CIEL:162169", "actual visit notes, advice", patient, visitNoteEncounter, toDate(toLocalDate(visit5YearsAgo.getStartDatetime())),
				location);
		
		// add diagnoses to visit notes encounter
		Condition condition = conditionGenerator.createCondition(patient, visitNoteEncounter, diagnosisGenerator.getConceptCache().findConcept(""));
		Diagnosis diagnosis1 = diagnosisGenerator.createDiagnosis(true, patient, visitNoteEncounter, condition);
		
		// add appointment
		
		// add patient to program
		
		// add drug dispensing
		
		// save visit
	}
	
	private void createDemoVisit3YearsAgo(Patient patient) throws Exception {
		Visit visit3YearsAgo = new Visit(patient, vs.getVisitTypeByUuid(""), toDate(LocalDateTime.now().minusYears(3)));
		visit3YearsAgo.setLocation(ls.getLocationByUuid(""));
		
		Provider visitProvider = providerGenerator.getRandomClinician();
		Location location = Context.getLocationService().getLocation("Outpatient Clinic");
		Encounter vitalsEncounter = visitGenerator.createDemoVitalsEncounter(patient, toDate(toLocalDate(visit3YearsAgo.getStartDatetime())), location, visitProvider);
		createDemoVitalsObs(patient, vitalsEncounter, location, "");
		// save encounter before adding it to visit
		es.saveEncounter(vitalsEncounter);
		visit3YearsAgo.addEncounter(vitalsEncounter);
		// add lab orders' encounter
		Encounter labOrderEncounter = visitGenerator.createEncounter("Lab Order", patient, toDate(toLocalDate(visit3YearsAgo.getStartDatetime())), location, visitProvider);
		// add lab orders to encounter
		orderGenerator.createDemoTestOrder(labOrderEncounter, null);
		
		// add consultation encounter
		Encounter consultationEncounter = visitGenerator.createEncounter("Consultation", patient, toDate(toLocalDate(visit3YearsAgo.getStartDatetime())), location, visitProvider);
		// add consutlation details i.e. drugs prescription
		
		es.saveEncounter(consultationEncounter);
		visit3YearsAgo.addEncounter(consultationEncounter);
		
		// add visit note
		Encounter visitNoteEncounter = visitGenerator.createEncounter("Visit Note", patient, toDate(toLocalDate(visit3YearsAgo.getStartDatetime())), location, visitProvider);
		visitNoteEncounter.setForm(visitGenerator.getVisitNoteForm());
		es.saveEncounter(visitNoteEncounter);
		
		obsGenerator.createTextObs("CIEL:162169", "actual visit notes, advice", patient, visitNoteEncounter, toDate(toLocalDate(visit3YearsAgo.getStartDatetime())),
				location);
		
		// add diagnoses to visit notes encounter
		Condition condition = conditionGenerator.createCondition(patient, visitNoteEncounter, diagnosisGenerator.getConceptCache().findConcept(""));
		Diagnosis diagnosis1 = diagnosisGenerator.createDiagnosis(true, patient, visitNoteEncounter, condition);
		
		
		// save visit
	}
	
	private void createDemoVisit2YearsAnd11monthsAgo(Patient patient) throws Exception {
		Visit visit2YearsAnd11monthsAgo = new Visit(patient, vs.getVisitTypeByUuid(""), toDate(LocalDateTime.now().minusMonths(1)));
		visit2YearsAnd11monthsAgo.setLocation(ls.getLocationByUuid(""));
		
		Provider visitProvider = providerGenerator.getRandomClinician();
		Location location = Context.getLocationService().getLocation("Outpatient Clinic");
		Encounter vitalsEncounter = visitGenerator.createDemoVitalsEncounter(patient, toDate(toLocalDate(visit2YearsAnd11monthsAgo.getStartDatetime())), location, visitProvider);
		createDemoVitalsObs(patient, vitalsEncounter, location, "");
		// save encounter before adding it to visit
		es.saveEncounter(vitalsEncounter);
		visit2YearsAnd11monthsAgo.addEncounter(vitalsEncounter);
		// add lab orders' encounter
		Encounter labOrderEncounter = visitGenerator.createEncounter("Lab Order", patient, toDate(toLocalDate(visit2YearsAnd11monthsAgo.getStartDatetime())), location, visitProvider);
		// add lab orders to encounter
		orderGenerator.createDemoTestOrder(labOrderEncounter, null);
		
		// add consultation encounter
		Encounter consultationEncounter = visitGenerator.createEncounter("Consultation", patient, toDate(toLocalDate(visit2YearsAnd11monthsAgo.getStartDatetime())), location, visitProvider);
		// add consutlation details i.e. drugs prescription
		
		es.saveEncounter(consultationEncounter);
		visit2YearsAnd11monthsAgo.addEncounter(consultationEncounter);
		
		// add visit note
		Encounter visitNoteEncounter = visitGenerator.createEncounter("Visit Note", patient, toDate(toLocalDate(visit2YearsAnd11monthsAgo.getStartDatetime())), location, visitProvider);
		visitNoteEncounter.setForm(visitGenerator.getVisitNoteForm());
		es.saveEncounter(visitNoteEncounter);
		
		obsGenerator.createTextObs("CIEL:162169", "actual visit notes, advice", patient, visitNoteEncounter, toDate(toLocalDate(visit2YearsAnd11monthsAgo.getStartDatetime())),
				location);
		
		// add diagnoses to visit notes encounter
		Condition condition = conditionGenerator.createCondition(patient, visitNoteEncounter, diagnosisGenerator.getConceptCache().findConcept(""));
		Diagnosis diagnosis1 = diagnosisGenerator.createDiagnosis(true, patient, visitNoteEncounter, condition);
		
		// save visit
	}
	
	private void createDemoVisit1MonthAgo(Patient patient) throws Exception {
		Visit visit1MonthAgo = new Visit(patient, vs.getVisitTypeByUuid(""), toDate(LocalDateTime.now().minusMonths(1)));
		visit1MonthAgo.setLocation(ls.getLocationByUuid(""));
		
		Provider visitProvider = providerGenerator.getRandomClinician();
		Location location = Context.getLocationService().getLocation("Outpatient Clinic");
		Encounter vitalsEncounter = visitGenerator.createDemoVitalsEncounter(patient, toDate(toLocalDate(visit1MonthAgo.getStartDatetime())), location, visitProvider);
		createDemoVitalsObs(patient, vitalsEncounter, location, "");
		// save encounter before adding it to visit
		es.saveEncounter(vitalsEncounter);
		visit1MonthAgo.addEncounter(vitalsEncounter);
		// add lab orders' encounter
		Encounter labOrderEncounter = visitGenerator.createEncounter("Lab Order", patient, toDate(toLocalDate(visit1MonthAgo.getStartDatetime())), location, visitProvider);
		// add lab orders to encounter
		orderGenerator.createDemoTestOrder(labOrderEncounter, null);
		
		// add consultation encounter
		Encounter consultationEncounter = visitGenerator.createEncounter("Consultation", patient, toDate(toLocalDate(visit1MonthAgo.getStartDatetime())), location, visitProvider);
		// add consutlation details i.e. drugs prescription
		es.saveEncounter(consultationEncounter);
		DrugOrderDescriptor dod = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "parten resolver path");
		
		orderGenerator.createDemoDrugOrder(consultationEncounter, dod);
		
		visit1MonthAgo.addEncounter(consultationEncounter);
		
		// add visit note
		Encounter visitNoteEncounter = visitGenerator.createEncounter("Visit Note", patient, toDate(toLocalDate(visit1MonthAgo.getStartDatetime())), location, visitProvider);
		visitNoteEncounter.setForm(visitGenerator.getVisitNoteForm());
		es.saveEncounter(visitNoteEncounter);
		
		obsGenerator.createTextObs("CIEL:162169", "actual visit notes, advice", patient, visitNoteEncounter, toDate(toLocalDate(visit1MonthAgo.getStartDatetime())),
				location);
		
		// add diagnoses to visit notes encounter
		Condition condition = conditionGenerator.createCondition(patient, visitNoteEncounter, diagnosisGenerator.getConceptCache().findConcept(""));
		Diagnosis diagnosis1 = diagnosisGenerator.createDiagnosis(true, patient, visitNoteEncounter, condition);
		
		
		// save visit
	}
	
	private void createDemoVitalsObs(Patient patient, Encounter encounter, Location location, String descriptorPath) throws Exception {
		for (NumericObsValueDescriptor vitalsDescriptor : obsGenerator.loadObsValueDescriptorsFor(obsGenerator.getPatternResolver(), descriptorPath)) {
			obsGenerator.createNumericObsFromDescriptor(vitalsDescriptor, patient, encounter, encounter.getEncounterDatetime(), location);
		}
	}
}
