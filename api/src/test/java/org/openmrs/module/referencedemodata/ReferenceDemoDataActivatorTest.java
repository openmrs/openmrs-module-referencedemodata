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

import org.junit.Test;
import org.openmrs.User;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.LocationService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.openmrs.module.referencemetadata.ReferenceMetadataActivator;
import org.openmrs.test.SkipBaseSetup;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SkipBaseSetup
public class ReferenceDemoDataActivatorTest extends BaseModuleWebContextSensitiveTest {

	@Autowired
	UserService userService;

	@Autowired
	ProviderService providerService;

    @Autowired
    AdministrationService adminService;

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
}
