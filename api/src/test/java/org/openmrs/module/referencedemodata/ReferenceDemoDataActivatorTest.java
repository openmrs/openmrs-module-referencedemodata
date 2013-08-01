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

import org.junit.Assert;
import org.junit.Test;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;

public class ReferenceDemoDataActivatorTest extends BaseModuleWebContextSensitiveTest {
	
	/**
	 * @see ReferenceDemoDataActivator#started()
	 * @verifies install the metadata package on startup
	 */
	@Test
	public void started_shouldInstallTheMetadataPackageOnStartup() throws Exception {
		LocationService ls = Context.getLocationService();
		final int initialLocationCount = ls.getAllLocations().size();
		
		new ReferenceDemoDataActivator().started();
		
		Assert.assertEquals(initialLocationCount + 5, ls.getAllLocations().size());
	}
}
