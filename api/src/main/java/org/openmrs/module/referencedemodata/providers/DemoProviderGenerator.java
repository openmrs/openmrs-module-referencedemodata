/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.providers;

import java.util.ArrayList;
import java.util.List;

import org.openmrs.Provider;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;

import static org.openmrs.module.referencedemodata.Randomizer.shouldRandomEventOccur;

public class DemoProviderGenerator {
	
	private ProviderService providerService = null;
	
	private List<Provider> clinicians = null;
	
	private List<Provider> labTechs = null;
	
	public Provider getRandomClinician() {
		if (clinicians == null) {
			clinicians = new ArrayList<>(2);
			clinicians.add(getProviderService().getProviderByIdentifier("doctor"));
			clinicians.add(getProviderService().getProviderByIdentifier("nurse"));
		}
		
		return shouldRandomEventOccur(.3) ? clinicians.get(0) : clinicians.get(1);
	}
	
	public Provider getRandomLabTech() {
		if (labTechs == null) {
			labTechs = new ArrayList<>(1);
			labTechs.add(getProviderService().getProviderByIdentifier("technician"));
		}
		
		return labTechs.get(0);
	}
	
	protected ProviderService getProviderService() {
		if (providerService == null) {
			providerService = Context.getProviderService();
		}
		
		return providerService;
	}
}
