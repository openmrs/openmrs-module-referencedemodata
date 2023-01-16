/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.referencedemodata;

import org.hibernate.cfg.Environment;
import org.hl7.fhir.r4.model.Task;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.openmrs.Cohort;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Visit;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ConditionService;
import org.openmrs.api.DiagnosisService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.UserService;
import org.openmrs.api.VisitService;
import org.openmrs.module.appointments.service.AppointmentsService;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.idgen.SequentialIdentifierGenerator;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.idgen.validator.LuhnMod30IdentifierValidator;
import org.openmrs.parameter.EncounterSearchCriteria;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.test.SkipBaseSetup;
import org.springframework.beans.factory.annotation.Autowired;

import ca.uhn.fhir.rest.param.TokenAndListParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.OPENMRS_ID_NAME;

@SkipBaseSetup
public class ReferenceDemoDataActivatorTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	UserService userService;
	
	@Autowired
	ProviderService providerService;
	
	@Autowired
	AdministrationService adminService;
	
	@Autowired
	PatientService patientService;
	
	@Autowired
	EncounterService encounterService;
	
	@Autowired
	VisitService visitService;
	
	@Autowired
	ConditionService conditionService;
	
	@Autowired
	DiagnosisService diagnosisService;
	
	@Autowired
	ObsService obsService;
	
	@Autowired
	ConceptService conceptService;
	
	@Autowired
	OrderService orderService;
	
	@Autowired
	AppointmentsService appointmentsService;
	
	@Autowired
	ProgramWorkflowService programWorkflowService;
	
	@Autowired
	FhirTaskService fhirTaskService;
	
	int demoPatientCount;
	
	// See https://groups.google.com/a/openmrs.org/forum/#!searchin/dev/Timeout$20trying$20to$20lock$20table/dev/rcQJCWvN8DQ/mp2_i7YGm8cJ
	@Override
	public Properties getRuntimeProperties() {
		Properties props = super.getRuntimeProperties();
		String url = props.getProperty(Environment.URL);
		if (url.contains("jdbc:h2:") && !url.contains(";MVCC=TRUE")) {
			props.setProperty(Environment.URL, url + ";MVCC=true");
		}
		return props;
	}
	
	@Before
	public void setupDb() throws Exception {
		executeDataSet(INITIAL_XML_DATASET_PACKAGE_PATH);
		executeDataSet("requiredDataTestDataset.xml");
		getConnection().commit();
		updateSearchIndex();
		authenticate();
	}
	
	@Before
	public void setupPatientCount() {
		String demoPatientCountProperty = System.getProperty("demoPatientCount");
		
		if (demoPatientCountProperty != null) {
			try {
				demoPatientCount = Integer.parseInt(demoPatientCountProperty);
				return;
			}
			catch (NumberFormatException ignored) {
			}
		}
		
		demoPatientCount = 10;
	}
	
	/**
	 * @see ReferenceDemoDataActivator#started()
	 */
	@Test
	public void started_shouldCreateDemoData() {
		GlobalProperty createDemoPatients = new GlobalProperty(
				ReferenceDemoDataConstants.CREATE_DEMO_PATIENTS_ON_NEXT_STARTUP, "" + demoPatientCount);
		adminService.saveGlobalProperty(createDemoPatients);
		
		ReferenceDemoDataActivator referenceDemoDataActivator = new ReferenceDemoDataActivator();
		initMockGenerator();
		referenceDemoDataActivator.started();
		
		List<Patient> allPatients = patientService.getAllPatients();
		
		assertThat(allPatients, hasSize(demoPatientCount));
		
		List<Visit> allVisits = visitService.getAllVisits();
		
		// data generatied by this module is semi-random (i.e., random with a fixed seed) so we cannot really know
		// the exact answers for all counts. These allow us to ensure that the results are within a tolerance.
		int patientErrorFactor = (int) (allPatients.size() * 0.15);
		int visitErrorFactor = (int) (allVisits.size() * 0.15);
		
		assertThat("Expected at least three visits per patient on average", allVisits.size(),
				greaterThan(3 * demoPatientCount));
		assertThat("Expected about appointment for every 2 visits", appointmentsService.getAllAppointments(null).size(),
				greaterThanOrEqualTo((allVisits.size() / 2) - visitErrorFactor));
		assertThat("Expected approximately one condition per visit",
				allPatients.stream().map(patient -> conditionService.getActiveConditions(patient).size())
						.reduce(0, Integer::sum), greaterThan(allVisits.size() - visitErrorFactor));
		assertThat("Expected approximately one diagnosis per visit",
				allPatients.stream().map(patient -> diagnosisService.getDiagnoses(patient, null).size())
						.reduce(0, Integer::sum), greaterThan(allVisits.size() - visitErrorFactor));
		assertThat("Expected more than 3 encounters per visit on average",
				allVisits.stream().map(visit -> visit.getEncounters().size()).reduce(0, Integer::sum),
				greaterThan(3 * allVisits.size()));
		
		List<Encounter> labEncounters = allVisits.stream().flatMap(v -> v.getNonVoidedEncounters().stream())
				.filter(e -> "Lab Results".equals(e.getEncounterType().getName())).collect(Collectors.toList());
		assertThat("Expected at least 2/3rds of visits to include lab results",
				labEncounters, hasSize(greaterThanOrEqualTo(Math.floorDiv(2 * allVisits.size(), 3))));
		
		List<Concept> labConcepts = Arrays.stream(new String[] {
						"21AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "1015AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
						"1133AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "856AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","1019AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" })
				.map(conceptService::getConceptByUuid).collect(Collectors.toList());
		
		assertThat("Expected each lab encounter to have one or more lab orders",
				obsService.getObservations(null, null, labConcepts, null, null, null, null, null, null, null, null, false),
				hasSize(greaterThanOrEqualTo(labEncounters.size())));
		
		List<Encounter> vitalsEncounters = allVisits.stream().flatMap(v -> v.getNonVoidedEncounters().stream())
				.filter(e -> "Vitals".equals(e.getEncounterType().getName())).collect(Collectors.toList());
		assertThat("Expected at least one vitals encounter per visit",
				vitalsEncounters, hasSize(greaterThanOrEqualTo(vitalsEncounters.size())));
		
		List<Concept> vitalsConcepts = Arrays.stream(new String[] {
						"5085AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "5086AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
						"5090AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "5089AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
						"5092AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "5087AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
						"5242AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "5088AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" })
				.map(conceptService::getConceptByUuid).collect(Collectors.toList());
		
		assertThat("Expected each vitals concept to have a result per vitals encounter",
				obsService.getObservations(null, null, vitalsConcepts, null, null, null, null, null, null, null, null,
						false),
				hasSize(vitalsConcepts.size() * vitalsEncounters.size()));
		
		List<Encounter> covidEncounters = allVisits.stream().flatMap(v -> v.getNonVoidedEncounters().stream())
				.filter(e -> "Consultation".equals(e.getEncounterType().getName()) && e.getForm() != null && "Covid 19".equals(
						e.getForm().getName())).collect(Collectors.toList());
		assertThat("Expected at least 1/3rd of visits to include covid results",
				covidEncounters, hasSize(greaterThanOrEqualTo(Math.floorDiv(allVisits.size(), 3))));
		
		Concept covidSymptomsConcept = conceptService.getConceptByUuid("0eaa21e0-5f69-41a9-9dea-942796590bbb");
		List<Obs> covidSymptoms = covidEncounters.stream().flatMap(e -> e.getAllObs().stream())
				.filter(o -> covidSymptomsConcept.equals(o.getConcept())).collect(Collectors.toList());
		assertThat("Expect every covid encounter to have recorded symptoms",
				covidSymptoms, hasSize(greaterThanOrEqualTo(covidEncounters.size())));
		assertThat("Every covid symptom list should have at least one symptom",
				covidSymptoms.stream().map(Obs::getValueCoded).filter(Objects::nonNull).collect(Collectors.toList()),
				hasSize(covidSymptoms.size()));
		
		Concept covidTestConcept = conceptService.getConceptByUuid("89c5bc03-8ce2-40d8-a77d-20b5a62a1ca1");
		List<Obs> covidTests = covidEncounters.stream().flatMap(e -> e.getAllObs().stream())
				.filter(o -> covidTestConcept.equals(o.getConcept())).collect(Collectors.toList());
		assertThat("Expect every covid encounter to record whether or not a test was done",
				covidTests, hasSize(covidEncounters.size()));
		
		List<Encounter> orderEncounters = encounterService.getEncounters(
				new EncounterSearchCriteria(null, null, null, null, null, null,
						Collections.singleton(encounterService.getEncounterType("Order")), null, null, null, false));
		assertThat("Expected at least one order per order encounter",
				allPatients.stream().map(patient -> orderService.getAllOrdersByPatient(patient).size())
						.reduce(0, Integer::sum), greaterThan(orderEncounters.size()));
		
		assertThat("Expected at least 1/3 of patients to be registered in a program",
				programWorkflowService.getPatientPrograms(new Cohort(), programWorkflowService.getPrograms("test")).size(),
				greaterThanOrEqualTo((allPatients.size() / 3) - patientErrorFactor));
		
		assertThat("Expected a COMPLETED FHIR Task per order",
				allPatients.stream().map(patient -> orderService.getAllOrdersByPatient(patient).size())
						.reduce(0, Integer::sum), equalTo(fhirTaskService.searchForTasks(null,null,new TokenAndListParam().addAnd(new TokenOrListParam().addOr(new TokenParam().setValue(Task.TaskStatus.COMPLETED.toString()))),null,null,null,null).size()));
		
	}
	
	long seed = 0;
	
	SequentialIdentifierGenerator mockIdGenerator;
	
	private void initMockGenerator() {
		PatientIdentifierType openmrsIdType = patientService.getPatientIdentifierTypeByName(OPENMRS_ID_NAME);
		mockIdGenerator = new SequentialIdentifierGenerator();
		mockIdGenerator.setIdentifierType(openmrsIdType);
		mockIdGenerator.setName(OPENMRS_ID_NAME + " Generator");
		mockIdGenerator.setUuid(UUID.randomUUID().toString());
		mockIdGenerator.setBaseCharacterSet(new LuhnMod30IdentifierValidator().getBaseCharacters());
		mockIdGenerator.setMinLength(6);
		mockIdGenerator.setFirstIdentifierBase("100000");
		
		IdentifierSourceService mockIss = mock(IdentifierSourceService.class);
		when(mockIss.generateIdentifier(eq(openmrsIdType), eq("DemoData"))).thenAnswer(
				(Answer<String>) invocation -> generateIdentifier());
		
		ReferenceDemoDataActivator.setIdentifierSourceService(mockIss);
	}
	
	private String generateIdentifier() {
		seed++;
		return mockIdGenerator.getIdentifierForSeed(seed);
	}
	
}
