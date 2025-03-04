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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Range;
import org.openmrs.Condition;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PersonName;
import org.openmrs.Program;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.referencedemodata.DemoDataConceptCache;
import org.openmrs.module.referencedemodata.condition.DemoConditionGenerator;
import org.openmrs.module.referencedemodata.diagnosis.DemoDiagnosisGenerator;
import org.openmrs.module.referencedemodata.obs.DemoObsGenerator;
import org.openmrs.module.referencedemodata.obs.NumericObsValueDescriptor;
import org.openmrs.module.referencedemodata.obs.NumericObsValueDescriptor.DecayType;
import org.openmrs.module.referencedemodata.obs.NumericObsValueDescriptor.Precision;
import org.openmrs.module.referencedemodata.orders.DemoOrderGenerator;
import org.openmrs.module.referencedemodata.orders.DrugOrderDescriptor;
import org.openmrs.module.referencedemodata.orders.DrugOrderGenerator;
import org.openmrs.module.referencedemodata.patient.DemoPatientGenerator;
import org.openmrs.module.referencedemodata.program.DemoProgramGenerator;
import org.openmrs.module.referencedemodata.providers.DemoProviderGenerator;
import org.openmrs.module.referencedemodata.visit.DemoVisitGenerator;

import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toLocalDate;

public class DemoDevanModiPatientGenerator {
	
	private final DemoDataConceptCache conceptCache;
	
	private final DemoPatientGenerator demoPatientGenerator;
	
	private final DemoProviderGenerator providerGenerator;
	
	private final DemoObsGenerator obsGenerator;
	
	private final DemoOrderGenerator orderGenerator;
	
	private final DemoDiagnosisGenerator diagnosisGenerator;
	
	private final DemoVisitGenerator visitGenerator;
	
	private final DemoProgramGenerator programGenerator;
	
	private final Date today;
	
	// diastolicBP, height, oxygenSaturation, pulse, respiratoryRate, systolicBP , temperature, weight
	
	private final String[] vitalsConcepts = {"5086AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "5090AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "5092AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "5087AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "5242AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "5085AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "5088AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "5089AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"};
	
	// values
	private final Double[] vitalsValues5YearsAgo = {68.0, 177.0,99.0,82.0,14.0,130.0,37.8,81.0};
	
	private final Double[] vitalsValues3YearsAgo = {92.0,177.0,98.0,80.0,16.0,168.0,38.2,81.5};
	
	private final Double[] vitalsValues2YearsAnd11MonthsAgo = {78.0,177.0,98.0,82.0,13.0,138.0,38.2,81.5};
	
	private final Double[] vitalsValues1MonthAgo = {76.0,177.0,99.0,90.0,14.0,140.0,37.2,83.0,83.0};
	
	private EncounterService es = null;
	
	private VisitService vs = null;
	
	private ProgramWorkflowService pws = null;

	private DemoConditionGenerator conditionGenerator;
	
	public DemoDevanModiPatientGenerator(DemoPatientGenerator demoPatientGenerator, DemoProviderGenerator providerGenerator, DemoObsGenerator obsGenerator, DemoOrderGenerator orderGenerator, DemoDiagnosisGenerator diagnosisGenerator, DemoVisitGenerator visitGenerator, DemoConditionGenerator conditionGenerator, DemoProgramGenerator programGenerator, DemoDataConceptCache conceptCache) {
		this.programGenerator = programGenerator;
		this.conceptCache = conceptCache;
		this.visitGenerator = visitGenerator;
		this.providerGenerator = providerGenerator;
		this.obsGenerator = obsGenerator;
		this.orderGenerator = orderGenerator;
		this.diagnosisGenerator = diagnosisGenerator;
		this.demoPatientGenerator = demoPatientGenerator;
		this.conditionGenerator = conditionGenerator;
		this.today = toDate(toLocalDate(new Date()));
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
	
	private void createVisits(Patient patient) throws Exception {
		// Visit 5 years ago
		createDemoVisit5YearsAgo(patient);
		// Visit 3 years ago
		createDemoVisit3YearsAgo(patient);
		// Visit 2 months and 11 weeks ago
		createDemoVisit2YearsAnd11MonthsAgo(patient);
		// Visit 1 month ago
		createDemoVisit1MonthAgo(patient);

	}
	
	private void createDemoVisit5YearsAgo(Patient patient) throws Exception {
		Location location = Context.getLocationService().getLocation("Outpatient Clinic");
		Date visitDate = toDate(toLocalDate(today).minusYears(5));
		Visit visit5YearsAgo = new Visit(patient, vs.getVisitTypeByUuid("7b0f5697-27e3-40c4-8bae-f4049abfb4ed"), visitDate);
		visit5YearsAgo.setLocation(location);
		vs.saveVisit(visit5YearsAgo);
		
		Provider visitProvider = providerGenerator.getRandomClinician();
		Encounter vitalsEncounter = visitGenerator.createDemoVitalsEncounter(patient, visitDate, location, visitProvider);
		createDemoVitalsObs(patient, vitalsEncounter, location, vitalsValues5YearsAgo);
		// save encounter before adding it to visit
		es.saveEncounter(vitalsEncounter);
		// add vitals
		
		visit5YearsAgo.addEncounter(vitalsEncounter);
		// add lab orders' encounter
		Encounter labOrderEncounter = visitGenerator.createEncounter("Lab Order", patient, visitDate, location, visitProvider);
		// add lab orders to encounter
		orderGenerator.createDemoTestOrder(labOrderEncounter, conceptCache.findConcept("159644AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
		orderGenerator.createDemoTestOrder(labOrderEncounter, conceptCache.findConcept("160912AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
		
		// add lab results
		
		// add consultation encounter
		Encounter consultationEncounter = visitGenerator.createEncounter("Consultation", patient, visitDate, location, visitProvider);
		// add consutlation details i.e. drugs prescription
		{
			DrugOrderDescriptor metformin500mg = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "metformin500mg");
			orderGenerator.createDemoDrugOrder(consultationEncounter, metformin500mg);
		}

		{
			DrugOrderDescriptor hydrochlorothiazide = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "hydrochlorothiazide");
			orderGenerator.createDemoDrugOrder(consultationEncounter, hydrochlorothiazide);
		}
		
		es.saveEncounter(consultationEncounter);
		visit5YearsAgo.addEncounter(consultationEncounter);
		
		// add visit note
		Encounter visitNoteEncounter = visitGenerator.createEncounter("Visit Note", patient, visitDate, location, visitProvider);
		visitNoteEncounter.setForm(visitGenerator.getVisitNoteForm());
		es.saveEncounter(visitNoteEncounter);
		
		String visitNotes = "Patient to:\n" + 
				"Participate in the outpatient diabetes education program\n" + 
				"Maintain a healthy diet and engage in regular exercise to improve glycemic control.\n" + 
				"continue on HCTZ for hypertension control.\n" + 
				"Regularly monitor Hgb A1c levels\n" + 
				"Consult a nutritionist or dietitian for dietary counseling";
		obsGenerator.createTextObs("CIEL:162169", visitNotes, patient, visitNoteEncounter, toDate(toLocalDate(visit5YearsAgo.getStartDatetime())),
				location);
		
		// add diagnoses to visit notes encounter
		{
			Condition condition = conditionGenerator.createCondition(patient, visitNoteEncounter, conceptCache.findConcept("117399AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
			diagnosisGenerator.createDiagnosis(true, patient, visitNoteEncounter, condition);
		}
		{
			Condition condition = conditionGenerator.createCondition(patient, visitNoteEncounter, conceptCache.findConcept("119481AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
			diagnosisGenerator.createDiagnosis(false, patient, visitNoteEncounter, condition);
		}
		// add patient to program
		
		Program program = pws.getProgramByUuid("7a72a724-8994-4d33-962e-f65d8ca4ae67");
		programGenerator.createDemoPatientProgram(patient, visitDate, program);
		
		// save visit
		vs.saveVisit(visit5YearsAgo);
	}
	
	private void createDemoVisit3YearsAgo(Patient patient) throws Exception {
		Location location = Context.getLocationService().getLocation("Outpatient Clinic");
		Visit visit3YearsAgo = new Visit(patient, vs.getVisitTypeByUuid("7b0f5697-27e3-40c4-8bae-f4049abfb4ed"), toDate(toLocalDate(today).minusYears(3)));
		visit3YearsAgo.setLocation(location);
		
		Provider visitProvider = providerGenerator.getRandomClinician();
		Encounter vitalsEncounter = visitGenerator.createDemoVitalsEncounter(patient, toDate(toLocalDate(visit3YearsAgo.getStartDatetime())), location, visitProvider);
		createDemoVitalsObs(patient, vitalsEncounter, location, vitalsValues3YearsAgo);
		// save encounter before adding it to visit
		es.saveEncounter(vitalsEncounter);
		visit3YearsAgo.addEncounter(vitalsEncounter);
		// add lab orders' encounter
		Encounter labOrderEncounter = visitGenerator.createEncounter("Lab Order", patient, toDate(toLocalDate(visit3YearsAgo.getStartDatetime())), location, visitProvider);
		// add lab orders to encounter
		orderGenerator.createDemoTestOrder(labOrderEncounter, conceptCache.findConcept("159644AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
		orderGenerator.createDemoTestOrder(labOrderEncounter, conceptCache.findConcept("160912AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
		orderGenerator.createDemoTestOrder(labOrderEncounter, conceptCache.findConcept("162630AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
		
		// add lab results
		
		// add consultation encounter
		Encounter consultationEncounter = visitGenerator.createEncounter("Consultation", patient, toDate(toLocalDate(visit3YearsAgo.getStartDatetime())), location, visitProvider);
		// add consutlation details i.e. drugs prescription
		{
			DrugOrderDescriptor metformin1000mg = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "metformin1000mg");
			orderGenerator.createDemoDrugOrder(consultationEncounter, metformin1000mg);
		}

		{
			DrugOrderDescriptor hydrochlorothiazide = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "hydrochlorothiazide");
			orderGenerator.createDemoDrugOrder(consultationEncounter, hydrochlorothiazide);
		}
		
		{
			DrugOrderDescriptor amlodipine50mg = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "amlodipine50mg");
			orderGenerator.createDemoDrugOrder(consultationEncounter, amlodipine50mg);
		}
		
		es.saveEncounter(consultationEncounter);
		visit3YearsAgo.addEncounter(consultationEncounter);
		
		// add visit note
		Encounter visitNoteEncounter = visitGenerator.createEncounter("Visit Note", patient, toDate(toLocalDate(visit3YearsAgo.getStartDatetime())), location, visitProvider);
		visitNoteEncounter.setForm(visitGenerator.getVisitNoteForm());
		es.saveEncounter(visitNoteEncounter);
		String visitNotes = "Patient advised:\n" + 
				"Regarding potential side effects.\n" + 
				"To continue lifestyle modifications such as maintaining a healthy diet, regular exercise, and limiting sodium intake.\n" + 
				"To contact the office if he experiences any adverse effects or if his blood pressure remains uncontrolled.";
		
		obsGenerator.createTextObs("CIEL:162169", visitNotes, patient, visitNoteEncounter, toDate(toLocalDate(visit3YearsAgo.getStartDatetime())),
				location);
		
		// add diagnoses to visit notes encounter
		Condition condition = conditionGenerator.createCondition(patient, visitNoteEncounter, diagnosisGenerator.getConceptCache().findConcept(""));
		diagnosisGenerator.createDiagnosis(true, patient, visitNoteEncounter, condition);
		
		// save visit
		vs.saveVisit(visit3YearsAgo);
	}
	
	private void createDemoVisit2YearsAnd11MonthsAgo(Patient patient) throws Exception {
		Location location = Context.getLocationService().getLocation("Outpatient Clinic");
		Visit visit2YearsAnd11monthsAgo = new Visit(patient, vs.getVisitTypeByUuid("7b0f5697-27e3-40c4-8bae-f4049abfb4ed"), toDate(toLocalDate(today).minusYears(2).minusMonths(11)));
		visit2YearsAnd11monthsAgo.setLocation(location);
		
		Provider visitProvider = providerGenerator.getRandomClinician();
		Encounter vitalsEncounter = visitGenerator.createDemoVitalsEncounter(patient, toDate(toLocalDate(visit2YearsAnd11monthsAgo.getStartDatetime())), location, visitProvider);
		createDemoVitalsObs(patient, vitalsEncounter, location, vitalsValues2YearsAnd11MonthsAgo);
		// save encounter before adding it to visit
		es.saveEncounter(vitalsEncounter);
		visit2YearsAnd11monthsAgo.addEncounter(vitalsEncounter);
		
		// add consultation encounter
		Encounter consultationEncounter = visitGenerator.createEncounter("Consultation", patient, toDate(toLocalDate(visit2YearsAnd11monthsAgo.getStartDatetime())), location, visitProvider);
		// add consutlation details i.e. drugs prescription
		{
			// Discontinue
			DrugOrderDescriptor metformin1000mg = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "metformin1000mg");
			orderGenerator.createDemoDrugOrder(consultationEncounter, metformin1000mg);
		}

		{
			DrugOrderDescriptor hydrochlorothiazide = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "hydrochlorothiazide");
			orderGenerator.createDemoDrugOrder(consultationEncounter, hydrochlorothiazide);
		}
		
		{
			DrugOrderDescriptor amlodipine50mg = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "amlodipine50mg");
			orderGenerator.createDemoDrugOrder(consultationEncounter, amlodipine50mg);
		}
		
		es.saveEncounter(consultationEncounter);
		visit2YearsAnd11monthsAgo.addEncounter(consultationEncounter);
		
		// add visit note
		Encounter visitNoteEncounter = visitGenerator.createEncounter("Visit Note", patient, toDate(toLocalDate(visit2YearsAnd11monthsAgo.getStartDatetime())), location, visitProvider);
		visitNoteEncounter.setForm(visitGenerator.getVisitNoteForm());
		es.saveEncounter(visitNoteEncounter);
		String visitNotes = "Patient advised to continue to monitoring his BP at home as well and contact the office should his BPs worsen";
		obsGenerator.createTextObs("CIEL:162169", visitNotes, patient, visitNoteEncounter, toDate(toLocalDate(visit2YearsAnd11monthsAgo.getStartDatetime())),
				location);
		
		// save visit
		vs.saveVisit(visit2YearsAnd11monthsAgo);
	}
	
	private void createDemoVisit1MonthAgo(Patient patient) throws Exception {
		Location location = Context.getLocationService().getLocation("Outpatient Clinic");
		Visit visit1MonthAgo = new Visit(patient, vs.getVisitTypeByUuid("7b0f5697-27e3-40c4-8bae-f4049abfb4ed"), toDate(toLocalDate(today).minusDays(30)));
		visit1MonthAgo.setLocation(location);
		
		Provider visitProvider = providerGenerator.getRandomClinician();
		Encounter vitalsEncounter = visitGenerator.createDemoVitalsEncounter(patient, toDate(toLocalDate(visit1MonthAgo.getStartDatetime())), location, visitProvider);
		createDemoVitalsObs(patient, vitalsEncounter, location, vitalsValues1MonthAgo);
		// save encounter before adding it to visit
		es.saveEncounter(vitalsEncounter);
		visit1MonthAgo.addEncounter(vitalsEncounter);
		// add lab orders' encounter
		Encounter labOrderEncounter = visitGenerator.createEncounter("Lab Order", patient, visit1MonthAgo.getStartDatetime(), location, visitProvider);
		// add lab orders to encounter
		orderGenerator.createDemoTestOrder(labOrderEncounter, conceptCache.findConcept("159644AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
		visit1MonthAgo.addEncounter(labOrderEncounter);
		
		// add consultation encounter
		Encounter consultationEncounter = visitGenerator.createEncounter("Consultation", patient, visit1MonthAgo.getStartDatetime(), location, visitProvider);
		// add consultation details i.e. procedures and physical exams (Abdominal exam, Rectal exam)
		
		es.saveEncounter(consultationEncounter);
		
		// add Admission encounter
		Encounter admissionEncounter = visitGenerator.createEncounter("Admission", patient, visit1MonthAgo.getStartDatetime(), location, visitProvider);
		// add Admission details i.e. diagnoses
		{
			Condition condition = conditionGenerator.createCondition(patient, admissionEncounter, conceptCache.findConcept("123569AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
			diagnosisGenerator.createDiagnosis(false, patient, admissionEncounter, condition);
		}
		
		es.saveEncounter(admissionEncounter);
		
		visit1MonthAgo.addEncounter(admissionEncounter);
		
		// create Discharge encounter
		Encounter dischargeEncounter = visitGenerator.createEncounter("Discharge", patient, visit1MonthAgo.getStartDatetime(), location, visitProvider);
		// add discharge diagnosis
		{
			Condition condition = conditionGenerator.createCondition(patient, dischargeEncounter, conceptCache.findConcept("117903AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
			diagnosisGenerator.createDiagnosis(false, patient, dischargeEncounter, condition);
		}
		// add discharge medication
		{
			DrugOrderDescriptor metformin1000mg = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "metformin1000mg");
			orderGenerator.createDemoDrugOrder(dischargeEncounter, metformin1000mg);
		}

		{
			DrugOrderDescriptor hydrochlorothiazide = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "hydrochlorothiazide");
			orderGenerator.createDemoDrugOrder(dischargeEncounter, hydrochlorothiazide);
		}
		
		{
			DrugOrderDescriptor amlodipine50mg = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "amlodipine50mg");
			orderGenerator.createDemoDrugOrder(dischargeEncounter, amlodipine50mg);
		}
		
		{
			DrugOrderDescriptor esomeprazole20mg = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "esomeprazole20mg");
			orderGenerator.createDemoDrugOrder(dischargeEncounter, esomeprazole20mg);
		}
		
		{
			DrugOrderDescriptor bismuthSubsalicylate300mg = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "bismuthSubsalicylate300mg");
			orderGenerator.createDemoDrugOrder(dischargeEncounter, bismuthSubsalicylate300mg);
		}
		
		{
			DrugOrderDescriptor tetracycline500mg = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "tetracycline500mg");
			orderGenerator.createDemoDrugOrder(dischargeEncounter, tetracycline500mg);
		}
		
		{
			DrugOrderDescriptor metronidazole250mg = DrugOrderGenerator.loadDrugOrderDescriptor(obsGenerator.getPatternResolver(), "metronidazole250mg");
			orderGenerator.createDemoDrugOrder(dischargeEncounter, metronidazole250mg);
		}
		
		visit1MonthAgo.addEncounter(dischargeEncounter);
		
		// add visit note
		Encounter visitNoteEncounter = visitGenerator.createEncounter("Visit Note", patient, visit1MonthAgo.getStartDatetime(), location, visitProvider);
		visitNoteEncounter.setForm(visitGenerator.getVisitNoteForm());
		es.saveEncounter(visitNoteEncounter);
		String visitNotes = "Patient advised to:\n" + 
				"Take their medications as directed.\n" + 
				"Avoid alcohol, smoking, and NSAIDS as these can make the ulcer worse.\n" + 
				"Follow up with their primary care physician and GI within 1-2 weeks.\n" + 
				"Return to the emergency department or call 911 if they experience severe abdominal pain, shortness of breath, chest pain, dizziness or vomiting.";
		obsGenerator.createTextObs("CIEL:162169", visitNotes, patient, visitNoteEncounter, visit1MonthAgo.getStartDatetime(),
				location);
		visit1MonthAgo.addEncounter(visitNoteEncounter);
		
		// save visit
		vs.saveVisit(visit1MonthAgo);
	}
	
	private void createDemoVitalsObs(Patient patient, Encounter encounter, Location location, Double[] values) throws Exception {
		for (String uuid : vitalsConcepts) {
			NumericObsValueDescriptor vitalDescriptor = new NumericObsValueDescriptor();
			vitalDescriptor.setConcept(Context.getConceptService().getConceptByUuid(uuid));
			vitalDescriptor.setInitialValue(Range.between(values[ArrayUtils.indexOf(vitalsConcepts, uuid)],values[ArrayUtils.indexOf(vitalsConcepts, uuid)]));
			vitalDescriptor.setDecayType(DecayType.CONSTANT);
			vitalDescriptor.setTrend(0);
			vitalDescriptor.setPrecision(Precision.FLOAT);
			vitalDescriptor.setStandardDeviation(0);
			obsGenerator.createNumericObsFromDescriptor(vitalDescriptor, patient, encounter, encounter.getEncounterDatetime(), location);
		}
	}
}
