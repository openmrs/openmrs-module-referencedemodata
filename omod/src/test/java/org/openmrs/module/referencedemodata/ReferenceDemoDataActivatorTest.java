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
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.openmrs.GlobalProperty;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Visit;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.UserService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.SequentialIdentifierGenerator;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.idgen.validator.LuhnMod30IdentifierValidator;
import org.openmrs.module.referencedemodata.patient.DemoPatientGenerator;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.test.SkipBaseSetup;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
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
    VisitService visitService;
    
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
    
    /**
     * @see ReferenceDemoDataActivator#started()
     */
    @Test
    public void started_shouldCreateDemoPatients() throws Exception {
    	final int demoPatientCount = 10;

        GlobalProperty createDemoPatients = new GlobalProperty(ReferenceDemoDataConstants.CREATE_DEMO_PATIENTS_ON_NEXT_STARTUP, ""+demoPatientCount);
        adminService.saveGlobalProperty(createDemoPatients);
        
		ReferenceDemoDataActivator referenceDemoDataActivator = new ReferenceDemoDataActivator();
        initMockGenerator();
		referenceDemoDataActivator.started();

		List<Patient> allPatients = patientService.getAllPatients();
        for (Patient patient : allPatients) {
	        System.out.println(patient + " " + patient.getPatientIdentifier() + " " + patient.getPersonName() + " " + patient.getBirthdate() + " " + patient.getAddresses());
        }
		
		assertThat(allPatients, hasSize(demoPatientCount));

        List<Visit> allVisits = visitService.getAllVisits();
        for (Visit visit : allVisits) {
	        System.out.println(visit + " " + visit.getStartDatetime() + " - " + visit.getStopDatetime() + " " + visit.getEncounters());
        }
		
		assertThat(allVisits.size(), greaterThan(demoPatientCount));
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
		
        DemoPatientGenerator.setIdentifierSourceService(mockIss);
    }
    
    private String generateIdentifier() {
    	seed++;
	    return mockIdGenerator.getIdentifierForSeed(seed);
    }
    
}
