/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.hibernate.cfg.Environment;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.openmrs.GlobalProperty;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Visit;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.PatientService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.SequentialIdentifierGenerator;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.idgen.validator.LuhnMod30IdentifierValidator;
import org.openmrs.test.SkipBaseSetup;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.OPENMRS_ID_NAME;

/**
 * Performance tests for demo data generation.
 * <p>
 * These tests are skipped by default. To run them, set the system property:
 * <pre>
 *   -DrunPerformanceTests=true
 * </pre>
 * Optionally control patient count with:
 * <pre>
 *   -DperfPatientCount=50
 * </pre>
 * Example Maven invocation:
 * <pre>
 *   mvn test -pl omod -Dtest=ReferenceDemoDataPerformanceTest -DrunPerformanceTests=true -DperfPatientCount=50
 * </pre>
 */
@SkipBaseSetup
public class ReferenceDemoDataPerformanceTest extends BaseModuleWebContextSensitiveTest {
	
	@Autowired
	AdministrationService adminService;
	
	@Autowired
	PatientService patientService;
	
	@Autowired
	VisitService visitService;
	
	@Autowired
	EncounterService encounterService;
	
	@Autowired
	ObsService obsService;
	
	private int patientCount;
	
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
	public void setup() throws Exception {
		Assume.assumeTrue("Performance tests are skipped by default. Set -DrunPerformanceTests=true to run.",
				"true".equalsIgnoreCase(System.getProperty("runPerformanceTests")));
		
		executeDataSet(INITIAL_XML_DATASET_PACKAGE_PATH);
		executeDataSet("requiredDataTestDataset.xml");
		getConnection().commit();
		updateSearchIndex();
		authenticate();
		
		String countProp = System.getProperty("perfPatientCount");
		if (countProp != null) {
			try {
				patientCount = Integer.parseInt(countProp);
			}
			catch (NumberFormatException e) {
				patientCount = 10;
			}
		} else {
			patientCount = 10;
		}
	}
	
	@Test
	public void shouldMeasureDemoDataGenerationTime() {
		GlobalProperty createDemoPatients = new GlobalProperty(
				ReferenceDemoDataConstants.CREATE_DEMO_PATIENTS_ON_NEXT_STARTUP, String.valueOf(patientCount));
		adminService.saveGlobalProperty(createDemoPatients);
		
		initMockGenerator();
		
		ReferenceDemoDataActivator activator = new ReferenceDemoDataActivator();
		
		long startTime = System.currentTimeMillis();
		activator.started();
		long totalTime = System.currentTimeMillis() - startTime;
		
		List<Patient> allPatients = patientService.getAllPatients();
		List<Visit> allVisits = visitService.getAllVisits();
		
		int totalEncounters = allVisits.stream()
				.mapToInt(v -> v.getEncounters().size())
				.sum();
		int totalObs = obsService.getObservations(null, null, null, null, null, null, null, null, null, null, null, false)
				.size();
		
		assertThat("Expected all patients to be created", allPatients.size(), greaterThan(0));
		assertThat("Expected visits to be created", allVisits.size(), greaterThan(0));
		
		System.out.println();
		System.out.println("========================================");
		System.out.println("  Demo Data Generation Performance");
		System.out.println("========================================");
		System.out.println("  Patients:       " + allPatients.size());
		System.out.println("  Visits:         " + allVisits.size());
		System.out.println("  Encounters:     " + totalEncounters);
		System.out.println("  Observations:   " + totalObs);
		System.out.println("----------------------------------------");
		System.out.println("  Total time:     " + totalTime + " ms");
		System.out.println("  Per patient:    " + (totalTime / allPatients.size()) + " ms");
		System.out.println("  Per visit:      " + (totalTime / Math.max(allVisits.size(), 1)) + " ms");
		System.out.println("  Per encounter:  " + (totalTime / Math.max(totalEncounters, 1)) + " ms");
		System.out.println("  Per obs:        " + (totalObs > 0 ? (totalTime / totalObs) : "N/A") + " ms");
		System.out.println("========================================");
		System.out.println();
	}
	
	@Test
	public void shouldMeasurePatientCreationTimeIndependently() {
		initMockGenerator();
		
		org.openmrs.module.referencedemodata.patient.DemoPatientGenerator patientGenerator =
				new org.openmrs.module.referencedemodata.patient.DemoPatientGenerator(getMockIdentifierSourceService());
		
		long startTime = System.currentTimeMillis();
		List<Integer> patientIds = patientGenerator.createDemoPatients(patientCount);
		Context.flushSession();
		long patientTime = System.currentTimeMillis() - startTime;
		
		assertThat("Expected all patients to be created", patientIds.size(), greaterThan(0));
		
		System.out.println();
		System.out.println("========================================");
		System.out.println("  Patient Creation Performance");
		System.out.println("========================================");
		System.out.println("  Patients created: " + patientIds.size());
		System.out.println("  Total time:       " + patientTime + " ms");
		System.out.println("  Per patient:      " + (patientTime / patientIds.size()) + " ms");
		System.out.println("========================================");
		System.out.println();
	}
	
	@Test
	public void shouldMeasurePerPatientScaling() {
		GlobalProperty createDemoPatients = new GlobalProperty(
				ReferenceDemoDataConstants.CREATE_DEMO_PATIENTS_ON_NEXT_STARTUP, String.valueOf(patientCount));
		adminService.saveGlobalProperty(createDemoPatients);
		
		initMockGenerator();
		
		ReferenceDemoDataActivator activator = new ReferenceDemoDataActivator();
		
		long startTime = System.currentTimeMillis();
		activator.started();
		long totalTime = System.currentTimeMillis() - startTime;
		
		List<Patient> allPatients = patientService.getAllPatients();
		List<Visit> allVisits = visitService.getAllVisits();
		int totalEncounters = allVisits.stream()
				.mapToInt(v -> v.getEncounters().size())
				.sum();
		
		// Calculate throughput
		double patientsPerSecond = allPatients.size() / (totalTime / 1000.0);
		double visitsPerSecond = allVisits.size() / (totalTime / 1000.0);
		double encountersPerSecond = totalEncounters / (totalTime / 1000.0);
		
		assertThat("Expected all patients to be created", allPatients.size(), greaterThan(0));
		
		System.out.println();
		System.out.println("========================================");
		System.out.println("  Throughput Metrics (" + allPatients.size() + " patients)");
		System.out.println("========================================");
		System.out.println("  Patients/sec:    " + String.format("%.2f", patientsPerSecond));
		System.out.println("  Visits/sec:      " + String.format("%.2f", visitsPerSecond));
		System.out.println("  Encounters/sec:  " + String.format("%.2f", encountersPerSecond));
		System.out.println("  Total time:      " + totalTime + " ms");
		System.out.println("========================================");
		System.out.println();
	}
	
	// --- Mock identifier generator setup (same as ReferenceDemoDataActivatorTest) ---
	
	private long seed = 0;
	
	private SequentialIdentifierGenerator mockIdGenerator;
	
	private IdentifierSourceService mockIss;
	
	private void initMockGenerator() {
		PatientIdentifierType openmrsIdType = patientService.getPatientIdentifierTypeByName(OPENMRS_ID_NAME);
		mockIdGenerator = new SequentialIdentifierGenerator();
		mockIdGenerator.setIdentifierType(openmrsIdType);
		mockIdGenerator.setName(OPENMRS_ID_NAME + " Generator");
		mockIdGenerator.setUuid(UUID.randomUUID().toString());
		mockIdGenerator.setBaseCharacterSet(new LuhnMod30IdentifierValidator().getBaseCharacters());
		mockIdGenerator.setMinLength(6);
		mockIdGenerator.setFirstIdentifierBase("100000");
		
		mockIss = mock(IdentifierSourceService.class);
		when(mockIss.generateIdentifier(eq(openmrsIdType), eq("DemoData")))
				.thenAnswer((Answer<String>) invocation -> generateIdentifier());
		
		ReferenceDemoDataActivator.setIdentifierSourceService(mockIss);
	}
	
	private IdentifierSourceService getMockIdentifierSourceService() {
		if (mockIss == null) {
			initMockGenerator();
		}
		return mockIss;
	}
	
	private String generateIdentifier() {
		seed++;
		return mockIdGenerator.getIdentifierForSeed(seed);
	}
}
