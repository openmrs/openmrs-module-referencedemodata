/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.handler;

import java.util.List;

import org.openmrs.annotation.Handler;
import org.openmrs.module.appointmentscheduling.AppointmentType;
import org.openmrs.module.appointmentscheduling.api.AppointmentService;
import org.openmrs.module.metadatadeploy.handler.AbstractObjectDeployHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Deployment handler for appointment types
 */
@Handler(supports = { AppointmentType.class })
public class AppointmentTypeDeployHandler extends AbstractObjectDeployHandler<AppointmentType> {
	
	@Autowired
	@Qualifier("appointmentService")
	private AppointmentService appointmentService;
	
	/**
	 * @see org.openmrs.module.metadatadeploy.handler.ObjectDeployHandler#fetch(String)
	 */
	@Override
	public AppointmentType fetch(String uuid) {
		return appointmentService.getAppointmentTypeByUuid(uuid);
	}
	
	/**
	 * @see org.openmrs.module.metadatadeploy.handler.ObjectDeployHandler#save(org.openmrs.OpenmrsObject)
	 */
	@Override
	public AppointmentType save(AppointmentType obj) {
		return appointmentService.saveAppointmentType(obj);
	}
	
	/**
	 * @see org.openmrs.module.metadatadeploy.handler.ObjectDeployHandler#findAlternateMatch(org.openmrs.OpenmrsObject)
	 */
	@Override
	public AppointmentType findAlternateMatch(AppointmentType incoming) {
		List<AppointmentType> possibleMatches = appointmentService.getAppointmentTypes(incoming.getName(), true);
		for (AppointmentType possibleMatch : possibleMatches) {
			if (possibleMatch.getName().equals(incoming.getName())) {
				return possibleMatch;
			}
		}
		
		return null;
	}
	
	/**
	 * @param obj the object to uninstall
	 * @see org.openmrs.module.metadatadeploy.handler.ObjectDeployHandler#uninstall(org.openmrs.OpenmrsObject,
	 *      String)
	 */
	@Override
	public void uninstall(AppointmentType obj, String reason) {
		appointmentService.retireAppointmentType(obj, reason);
	}
}
