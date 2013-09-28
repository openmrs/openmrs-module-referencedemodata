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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Properties;

import org.hibernate.cfg.Environment;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.openmrs.GlobalProperty;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.UserService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.SequentialIdentifierGenerator;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.idgen.validator.LuhnMod30IdentifierValidator;
import org.openmrs.module.referencemetadata.ReferenceMetadataActivator;
import org.openmrs.module.referencemetadata.ReferenceMetadataConstants;
import org.openmrs.test.SkipBaseSetup;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

@SkipBaseSetup
public class ReferenceDemoDataActivatorTest extends BaseModuleWebContextSensitiveTest {

	@Autowired
	UserService userService;

	@Autowired
	ProviderService providerService;

    @Autowired
    AdministrationService adminService;

    @Autowired
    PatientService patientService;
    
    @Autowired
    VisitService visitService;
    
	/**
	 * @see ReferenceDemoDataActivator#started()
	 * @verifies install the metadata package on startup
	 */
	@Test
	public void started_shouldInstallTheMetadataPackageOnStartup() throws Exception {
		initializeInMemoryDatabase();
		executeDataSet("requiredDataTestDataset.xml");
		authenticate();

		new ReferenceMetadataActivator().started();

		LocationService ls = Context.getLocationService();
		final int initialLocationCount = ls.getAllLocations().size();
		
		new ReferenceDemoDataActivator().started();
		
		assertEquals(initialLocationCount + 7, ls.getAllLocations().size());

		assertThat(userService.getUserByUsername("clerk"), is(notNullValue()));
		assertThat(userService.getUserByUsername("nurse"), is(notNullValue()));
		assertThat(userService.getUserByUsername("doctor"), is(notNullValue()));

		assertThat(providerService.getProviderByIdentifier("clerk"), is(notNullValue()));
		assertThat(providerService.getProviderByIdentifier("nurse"), is(notNullValue()));
		assertThat(providerService.getProviderByIdentifier("doctor"), is(notNullValue()));
	}

    /**
     * @verifies create a scheduler user and set the related global properties
     * @see ReferenceDemoDataActivator#started()
     */
    @Test
    public void started_shouldCreateASchedulerUserAndSetTheRelatedGlobalProperties() throws Exception {
        initializeInMemoryDatabase();
        executeDataSet("requiredDataTestDataset.xml");
        authenticate();

        new ReferenceMetadataActivator().started();

        User user = userService.getUserByUsername(ReferenceDemoDataConstants.SCHEDULER_USERNAME);
        assertNull(user);

        new ReferenceDemoDataActivator().started();

        user = userService.getUserByUsername(ReferenceDemoDataConstants.SCHEDULER_USERNAME);
        assertNotNull(user);
        assertTrue(user.isSuperUser());
        assertEquals(ReferenceDemoDataConstants.SCHEDULER_USERNAME, adminService.getGlobalProperty("scheduler.username"));
        assertEquals("Scheduler123", adminService.getGlobalProperty("scheduler.password"));
    }
    
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
    
    /**
     * @verifies that some demo patients are created on startup
     * @see ReferenceDemoDataActivator#started()
     */
    @Test
    public void started_shouldCreateDemoPatients() throws Exception {
        initializeInMemoryDatabase();
        executeDataSet("requiredDataTestDataset.xml");
        authenticate();

        GlobalProperty createDemoPatients = new GlobalProperty(ReferenceDemoDataActivator.CREATE_DEMO_PATIENTS_ON_NEXT_STARTUP, "10");
        adminService.saveGlobalProperty(createDemoPatients);
        
        new ReferenceMetadataActivator().started();
        
        ReferenceDemoDataActivator referenceDemoDataActivator = new ReferenceDemoDataActivator();
        initMockGenerator(referenceDemoDataActivator);
		referenceDemoDataActivator.started();

        List<Patient> allPatients = patientService.getAllPatients();
        for (Patient patient : allPatients) {
	        System.out.println(patient + " " + patient.getPatientIdentifier() + " " + patient.getPersonName() + " " + patient.getBirthdate());
        }
		assertTrue(allPatients.size() > 0);
        List<Visit> allVisits = visitService.getAllVisits();
        for (Visit visit : allVisits) {
	        System.out.println(visit);
        }
//		assertTrue(allVisits.size() > 0);
    }
    
    long seed = 0;
    SequentialIdentifierGenerator mockIdGenerator;

    private void initMockGenerator(ReferenceDemoDataActivator referenceDemoDataActivator) {
		PatientIdentifierType openmrsIdType = patientService.getPatientIdentifierTypeByName(ReferenceMetadataConstants.OPENMRS_ID_NAME);
    	mockIdGenerator = new SequentialIdentifierGenerator();
    	mockIdGenerator.setIdentifierType(openmrsIdType);
    	mockIdGenerator.setName(ReferenceMetadataConstants.OPENMRS_ID_GENERATOR_NAME);
    	mockIdGenerator.setUuid(ReferenceMetadataConstants.OPENMRS_ID_GENERATOR_UUID);
    	mockIdGenerator.setBaseCharacterSet(new LuhnMod30IdentifierValidator().getBaseCharacters());
    	mockIdGenerator.setLength(6);
    	mockIdGenerator.setFirstIdentifierBase("10000");

        IdentifierSourceService mockIss = Mockito.mock(IdentifierSourceService.class);
        Mockito.when(mockIss.generateIdentifier(Mockito.eq(openmrsIdType), Mockito.eq("DemoData"))).thenAnswer(new Answer<String>() {
        	@Override
        	public String answer(InvocationOnMock invocation) throws Throwable {
        	    return generateIdentifier();
        	}
        });
        referenceDemoDataActivator.setIdentifierSourceService(mockIss);
    }
    
    private String generateIdentifier() {
    	seed++;
	    return mockIdGenerator.getIdentifierForSeed(seed);
    }
    
}
