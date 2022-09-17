/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.appointments;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.constants.PrivilegeConstants;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentKind;
import org.openmrs.module.appointments.model.AppointmentProvider;
import org.openmrs.module.appointments.model.AppointmentProviderResponse;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.service.AppointmentServiceDefinitionService;
import org.openmrs.module.appointments.service.AppointmentsService;
import org.openmrs.module.referencedemodata.providers.DemoProviderGenerator;

import static org.openmrs.module.referencedemodata.Randomizer.randomBetween;
import static org.openmrs.module.referencedemodata.Randomizer.randomListEntry;
import static org.openmrs.module.referencedemodata.Randomizer.shouldRandomEventOccur;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toLocalDateTime;

public class DemoAppointmentsGenerator {
	
	private final DemoProviderGenerator providerGenerator;
	
	private AppointmentsService appointmentsService = null;
	
	private AppointmentServiceDefinitionService appointmentServiceDefinitionService = null;
	
	private List<AppointmentServiceDefinition> appointmentsServices = null;
	
	public DemoAppointmentsGenerator(DemoProviderGenerator providerGenerator) {
		this.providerGenerator = providerGenerator;
	}
	
	public Appointment createDemoAppointment(Patient patient, Date visitStartTime, Date visitEndTime, Provider provider) {
		Appointment appointment = new Appointment();
		appointment.setPatient(patient);
		
		Date scheduledStartTime = visitStartTime;
		if (shouldRandomEventOccur(.2)) {
			scheduledStartTime = toDate(toLocalDateTime(scheduledStartTime).plusMinutes(randomBetween(-20, 20)));
		}
		
		Date scheduledEndTime = visitEndTime;
		if (shouldRandomEventOccur(.2)) {
			scheduledEndTime = toDate(toLocalDateTime(scheduledEndTime).plusMinutes(randomBetween(-20, 20)));
		}
		
		appointment.setStartDateTime(scheduledStartTime);
		appointment.setEndDateTime(scheduledEndTime);
		appointment.setAppointmentKind(AppointmentKind.Scheduled);
		
		appointment.setService(randomListEntry(getAppointmentsServices()));
		appointment.setAppointmentAudits(new HashSet<>());
		
		Set<AppointmentProvider> appointmentProviders = new HashSet<>();
		AppointmentProvider appointmentProvider = new AppointmentProvider();
		appointmentProvider.setAppointment(appointment);

		Provider scheduledProvider = provider;
		if (scheduledProvider == null || shouldRandomEventOccur(.05)) {
			scheduledProvider = providerGenerator.getRandomClinician();
		}
		appointmentProvider.setProvider(scheduledProvider);
		appointmentProvider.setResponse(AppointmentProviderResponse.ACCEPTED);
		appointmentProviders.add(appointmentProvider);
		appointment.setProviders(appointmentProviders);
		
		Context.addProxyPrivilege(PrivilegeConstants.MANAGE_APPOINTMENTS);
		try {
			return getAppointmentsService().validateAndSave(appointment);
		} finally {
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_APPOINTMENTS);
		}
	}
	
	protected AppointmentsService getAppointmentsService() {
		if (appointmentsService == null) {
			appointmentsService = Context.getService(AppointmentsService.class);
		}
		
		return appointmentsService;
	}
	
	protected AppointmentServiceDefinitionService getAppointmentServiceDefinitionService() {
		if (appointmentServiceDefinitionService == null) {
			appointmentServiceDefinitionService = Context.getService(AppointmentServiceDefinitionService.class);
		}
		
		return appointmentServiceDefinitionService;
	}
	
	protected List<AppointmentServiceDefinition> getAppointmentsServices() {
		if (appointmentsServices == null) {
			appointmentsServices = getAppointmentServiceDefinitionService().getAllAppointmentServices(false);
		}
		
		return appointmentsServices;
	}
}
