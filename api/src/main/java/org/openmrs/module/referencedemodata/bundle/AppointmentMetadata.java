/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.bundle;

import org.openmrs.module.appointmentscheduling.AppointmentType;
import org.openmrs.module.metadatadeploy.bundle.AbstractMetadataBundle;
import org.springframework.stereotype.Component;

@Component
public class AppointmentMetadata extends AbstractMetadataBundle {
	
	@Override
	public void install() {
		install(appointmentType("Dermatology", 10, "4da187c6-c436-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Dermatology (New Patient)", 20, "5ab6d8a8-c436-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("General Medicine", 15, "7dd9ac8e-c436-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("General Medicine (New Patient)", 30, "7e7d3e26-c436-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Gynecology", 10, "7efeaa60-c436-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Gynecology (New Patient)", 20, "95636ce6-c436-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Infectious Disease", 15, "9ebdc232-c436-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Infectious Disease (New Patient)", 30, "a62a40e0-c436-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Mental Health", 60, "ac71c996-c436-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Mental Health (New Patient)", 90, "b29be856-c436-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Neurology", 15, "cba5a260-c436-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Neurology (New Patient)", 30, "d248c6c4-c436-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Obstetrics", 10, "0c617770-c437-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Obstetrics (New Patient)", 20, "136ed9a4-c437-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Oncology", 15, "1b2d98c4-c437-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Oncology (New Patient)", 30, "25873c9e-c437-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Pediatrics", 10, "2febe6a8-c437-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Pediatrics (New Patient)", 20, "38081afa-c437-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Surgery", 10, "3f5a8ca2-c437-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Surgery (New Patient)", 20, "452c596c-c437-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Urology", 20, "4d3b6396-c437-11e4-a470-82b0ea87e2d8"));
		install(appointmentType("Urology (New Patient)", 20, "4d85dda4-c437-11e4-a470-82b0ea87e2d8e"));
	}
	
	private static AppointmentType appointmentType(String name, Integer duration, String uuid) {
		AppointmentType aType = new AppointmentType();
		aType.setName(name);
		aType.setDuration(duration);
		aType.setUuid(uuid);
		
		return aType;
	}
}
