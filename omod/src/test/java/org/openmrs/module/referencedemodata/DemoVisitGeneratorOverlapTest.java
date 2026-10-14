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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.OPENMRS_ID_NAME;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.cfg.Environment;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.openmrs.GlobalProperty;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.VisitService;
import org.openmrs.module.idgen.SequentialIdentifierGenerator;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.idgen.validator.LuhnMod30IdentifierValidator;
import org.openmrs.module.referencedemodata.condition.DemoConditionGenerator;
import org.openmrs.module.referencedemodata.diagnosis.DemoDiagnosisGenerator;
import org.openmrs.module.referencedemodata.obs.DemoObsGenerator;
import org.openmrs.module.referencedemodata.orders.DemoOrderGenerator;
import org.openmrs.module.referencedemodata.providers.DemoProviderGenerator;
import org.openmrs.module.referencedemodata.visit.DemoVisitGenerator;
import org.openmrs.test.SkipBaseSetup;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Drives {@link DemoVisitGenerator#createDemoVisit} itself, so that removing or reordering the logic that keeps
 * a generated visit from starting before the patient's previous visit ended is caught. The ordering is asserted
 * directly: core's VisitValidator in the version this module builds against also rejects a visit that overlaps
 * itself, so visits.allowOverlappingVisits is deliberately left at its default here.
 */
@SkipBaseSetup
public class DemoVisitGeneratorOverlapTest extends BaseModuleWebContextSensitiveTest {
	
	@Autowired
	AdministrationService adminService;
	
	@Autowired
	PatientService patientService;
	
	@Autowired
	VisitService visitService;
	
	private long seed = 0;
	
	private SequentialIdentifierGenerator mockIdGenerator;
	
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
	
	@Test
	public void createDemoVisit_shouldNotStartBeforeThePreviousVisitHasEnded() {
		adminService.saveGlobalProperty(
		    new GlobalProperty(ReferenceDemoDataConstants.CREATE_DEMO_PATIENTS_ON_NEXT_STARTUP, "5"));
		initMockGenerator();
		new ReferenceDemoDataActivator().started();
		
		// patients aged 5 or younger only ever get short visits, so pick one that can be admitted
		Patient patient = patientService.getAllPatients().stream()
		        .filter(p -> p.getAge() != null && p.getAge() > 5).findFirst()
		        .orElseThrow(() -> new AssertionError("Expected a generated patient older than 5"));
		
		for (Visit visit : visitService.getVisitsByPatient(patient)) {
			visitService.voidVisit(visit, "test setup");
		}
		
		List<VisitType> visitTypes = visitService.getAllVisitTypes().stream()
		        .filter(vt -> !"Offline Visit".equals(vt.getName())).collect(Collectors.toList());
		Location location = patient.getPatientIdentifier().getLocation();
		
		// a previous visit that ended two hours ago
		LocalDateTime now = LocalDateTime.now();
		Visit previous = new Visit(patient, visitTypes.get(0), toDate(now.minusHours(4)));
		previous.setStopDatetime(toDate(now.minusHours(2)));
		previous.setLocation(location);
		previous = visitService.saveVisit(previous);
		
		DemoVisitGenerator generator = newVisitGenerator();
		
		for (boolean shortVisit : new boolean[] { true, false }) {
			Visit visit = generator.createDemoVisit(patient, visitTypes, location, shortVisit, previous, 0);
			
			assertTrue("A " + (shortVisit ? "short" : "long") + " visit must not start before the previous visit ended",
			    visit.getStartDatetime().after(previous.getStopDatetime()));
			assertFalse("A " + (shortVisit ? "short" : "long") + " visit must not end in the future",
			    visit.getStopDatetime().after(new Date()));
		}
	}
	
	private DemoVisitGenerator newVisitGenerator() {
		DemoDataConceptCache conceptCache = new DemoDataConceptCache();
		DemoProviderGenerator providerGenerator = new DemoProviderGenerator();
		DemoObsGenerator obsGenerator = new DemoObsGenerator(conceptCache);
		DemoOrderGenerator orderGenerator = new DemoOrderGenerator();
		DemoConditionGenerator conditionGenerator = new DemoConditionGenerator();
		DemoDiagnosisGenerator diagnosisGenerator = new DemoDiagnosisGenerator(conceptCache, conditionGenerator);
		return new DemoVisitGenerator(providerGenerator, obsGenerator, orderGenerator, diagnosisGenerator);
	}
	
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
		when(mockIss.generateIdentifier(eq(openmrsIdType), eq("DemoData")))
		        .thenAnswer((Answer<String>) invocation -> mockIdGenerator.getIdentifierForSeed(++seed));
		
		ReferenceDemoDataActivator.setIdentifierSourceService(mockIss);
	}
}