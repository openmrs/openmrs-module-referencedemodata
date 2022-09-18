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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.ModuleActivator;
import org.openmrs.module.ModuleFactory;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.referencedemodata.appointments.DemoAppointmentsGenerator;
import org.openmrs.module.referencedemodata.condition.DemoConditionGenerator;
import org.openmrs.module.referencedemodata.diagnosis.DemoDiagnosisGenerator;
import org.openmrs.module.referencedemodata.obs.DemoObsGenerator;
import org.openmrs.module.referencedemodata.orders.DemoOrderGenerator;
import org.openmrs.module.referencedemodata.patient.DemoPatientGenerator;
import org.openmrs.module.referencedemodata.program.DemoProgramGenerator;
import org.openmrs.module.referencedemodata.providers.DemoProviderGenerator;
import org.openmrs.module.referencedemodata.visit.DemoVisitGenerator;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.util.PrivilegeConstants;

import static org.openmrs.api.context.Context.getVisitService;
import static org.openmrs.module.referencedemodata.Randomizer.randomBetween;
import static org.openmrs.module.referencedemodata.Randomizer.shouldRandomEventOccur;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toLocalDateTime;
import static org.openmrs.util.OpenmrsConstants.GP_CASE_SENSITIVE_DATABASE_STRING_COMPARISON;

/**
 * This is the activator responsible for setting up the demo data, if required. Note that setup will only be done
 * if the `referencedemodata.createDemoPatientsOnNextStartup` setting has a non-zero value.
 */
@Slf4j
public class ReferenceDemoDataActivator extends BaseModuleActivator {
	
	public static final String MODULE_ID = "referencedemodata";
	
	// this property exists for testing and should only have a value during the started() function
	private static IdentifierSourceService iss = null;
	
	/**
	 * @see ModuleActivator#started()
	 */
	@Override
	public void started() {
		// basic idea: on start-up work out how many patients, if any we should generate
		// if there aren't any, we exit. Otherwise, we ensure some users and providers are set up and then start generating data
		try {
			AdministrationService as = Context.getAdministrationService();
			GlobalProperty gp = as.getGlobalPropertyObject(ReferenceDemoDataConstants.CREATE_DEMO_PATIENTS_ON_NEXT_STARTUP);
			if (gp == null || (gp.getPropertyValue().equals("0"))) {
				return;
			}
			
			int patientCount;
			try {
				patientCount = Integer.parseInt(gp.getPropertyValue());
			}
			catch (NumberFormatException e) {
				log.error("Could not parse [{}] as an integer", gp.getPropertyValue(), e);
				return;
			}
			
			if (patientCount <= 0) {
				return;
			}
			
			try {
				linkAdminAccountToAProviderIfNecessary();
				setupUsersAndProvidersIfNecessary();
				
				// we temporarily set the database to be case-insensitive
				boolean valueBefore = Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive();
				try {
					Context.getAdministrationService()
							.setGlobalProperty(GP_CASE_SENSITIVE_DATABASE_STRING_COMPARISON, "true");
					
					// attempt to set the log level so that details about the created patients are logged
					OpenmrsUtil.applyLogLevel(ReferenceDemoDataActivator.class.getPackage().toString(),
							OpenmrsConstants.LOG_LEVEL_INFO);
					
					DemoDataConceptCache conceptCache = new DemoDataConceptCache();
					
					List<Integer> createdPatientIds = new DemoPatientGenerator(
							getIdentifierSourceService()).createDemoPatients(patientCount);
					Context.flushSession();
					
					log.info("Created {} patients", createdPatientIds.size());
					
					DemoProviderGenerator providerGenerator = new DemoProviderGenerator();
					DemoObsGenerator obsGenerator = new DemoObsGenerator(conceptCache);
					DemoOrderGenerator orderGenerator = new DemoOrderGenerator();
					DemoConditionGenerator conditionGenerator = new DemoConditionGenerator();
					DemoDiagnosisGenerator diagnosisGenerator = new DemoDiagnosisGenerator(conceptCache, conditionGenerator);
					DemoVisitGenerator visitGenerator = new DemoVisitGenerator(providerGenerator, obsGenerator,
							orderGenerator, diagnosisGenerator);
					DemoAppointmentsGenerator appointmentsGenerator = new DemoAppointmentsGenerator(providerGenerator);
					DemoProgramGenerator programGenerator = new DemoProgramGenerator();
					
					List<VisitType> visitTypes = getVisitService().getAllVisitTypes().stream()
							.filter(vt -> "Offline Visit".equals(vt.getName())).collect(Collectors.toList());
					
					for (Integer patientId : createdPatientIds) {
						boolean isInProgram = false;
						
						Patient patient = Context.getPatientService().getPatient(patientId);
						int visitCount = randomBetween(1, Math.min(Math.round(patient.getAge() / 1.5f), 15));
						
						Location visitLocation = patient.getPatientIdentifier().getLocation();
						
						Visit lastVisit = null;
						for (int i = 0; i < visitCount; i++) {
							boolean shortVisit = shouldRandomEventOccur(0.8);
							lastVisit = visitGenerator.createDemoVisit(patient, visitTypes, visitLocation, shortVisit,
									lastVisit, visitCount - (i + 1));
							
							Provider encounterProvider = null;
							if (lastVisit.getNonVoidedEncounters().size() > 0) {
								Encounter encounter = lastVisit.getNonVoidedEncounters().get(0);
								
								if (encounter.getActiveEncounterProviders().size() > 0) {
									encounterProvider = encounter.getActiveEncounterProviders().iterator().next()
											.getProvider();
								}
							}
							
							appointmentsGenerator.createDemoAppointment(patient, lastVisit.getStartDatetime(),
									lastVisit.getStopDatetime(), encounterProvider);
							
							// about 1/3 patients in their first 2 visits will be registered as part of a program
							if (!isInProgram && i < 2) {
								if (shouldRandomEventOccur(.33)) {
									isInProgram = true;
									programGenerator.createDemoPatientProgram(patient, lastVisit.getStartDatetime());
								}
							}
							
							// we want to ensure that all patients are relatively current, so basically should've had a
							// visit in the past 6 months
							if (i == visitCount - 1) {
								LocalDateTime now = LocalDateTime.now();
								LocalDateTime lastVisitDateTime = toLocalDateTime(lastVisit.getStopDatetime());
								int yearsMissing = now.getYear() - lastVisitDateTime.getYear();
								if (yearsMissing > 0) {
									visitCount += yearsMissing;
								} else {
									// schedule a potential future appointment?
									if (shouldRandomEventOccur(.5)) {
										LocalDateTime visitStartDate = now.plusDays(randomBetween(1, 365 - 1))
												.plusMinutes(randomBetween(-(24 * 60 - 1), 24 * 60 - 1));
										LocalDateTime visitEndDate = visitStartDate.plusMinutes(randomBetween(10, 30));
										Appointment futureAppointment = appointmentsGenerator.createDemoAppointment(patient,
												toDate(visitStartDate), toDate(visitEndDate),
												providerGenerator.getRandomClinician());
										
										if (log.isInfoEnabled()) {
											log.info("created a future appointment for patient {} at {}",
													patient.getPatientIdentifier(),
													toLocalDateTime(futureAppointment.getStartDateTime()));
										}
									}
								}
							}
						}
						
						log.info("created {} visits for patient {}", visitCount, patient.getPatientIdentifier());
						try {
							Context.flushSession();
						}
						catch (Exception ignored) {
						}
					}
					
					try {
						Context.flushSession();
					}
					catch (Exception ignored) {
					}
				}
				finally {
					Context.clearSession();
					
					try {
						// Restore the value of Global Property
						Context.getAdministrationService()
								.setGlobalProperty(GP_CASE_SENSITIVE_DATABASE_STRING_COMPARISON,
										String.valueOf(valueBefore));
					}
					catch (Exception ignored) {
					}
				}
			}
			catch (Exception e) {
				log.error("Exception caught while creating demo data", e);
			}
			finally {
				// don't hold a reference to the IdentifierSourceService
				iss = null;
				
				// Set the global property to zero so that we won't create demo patients next time.
				gp.setPropertyValue("0");
				as.saveGlobalProperty(gp);
			}
		}
		catch (Exception e) {
			log.error("Failed to load ReferenceDemoData module due to exception", e);
			ModuleFactory.stopModule(ModuleFactory.getModuleById(MODULE_ID));
		}
	}
	
	private void linkAdminAccountToAProviderIfNecessary() {
		try {
			// If unknown provider isn't yet linked to admin, then do it
			Context.addProxyPrivilege(PrivilegeConstants.GET_PROVIDERS);
			Context.addProxyPrivilege(PrivilegeConstants.GET_PERSONS);
			Context.addProxyPrivilege(PrivilegeConstants.MANAGE_PROVIDERS);
			
			ProviderService ps = Context.getProviderService();
			Person adminPerson = Context.getPersonService().getPerson(1);
			
			Collection<Provider> possibleProvider = ps.getProvidersByPerson(adminPerson);
			if (possibleProvider.size() == 0) {
				List<Provider> providers = ps.getAllProviders(false);
				
				Provider provider;
				if (providers.size() == 0) {
					provider = new Provider();
					provider.setIdentifier("admin");
				} else {
					provider = providers.get(0);
				}
				
				provider.setPerson(adminPerson);
				ps.saveProvider(provider);
			}
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_PROVIDERS);
			Context.removeProxyPrivilege(PrivilegeConstants.GET_PERSONS);
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_PROVIDERS);
		}
	}
	
	private void setupUsersAndProvidersIfNecessary() {
		UserService userService = Context.getUserService();
		Person clerkPerson = setupPerson(ReferenceDemoDataConstants.CLERK_PERSON_UUID, "M", "John", "Clerk");
		Role clerkRole = userService.getRole(ReferenceDemoDataConstants.CLERK_ROLE);
		setupUser(ReferenceDemoDataConstants.CLERK_USER_UUID, "clerk", clerkPerson, "Clerk123", clerkRole);
		setupProvider(ReferenceDemoDataConstants.CLERK_PROVIDER_UUID, clerkPerson, "clerk");
		
		Person labPerson = setupPerson(ReferenceDemoDataConstants.LAB_PERSON_UUID, "F", "June", "Technician");
		Role labRole = userService.getRole(ReferenceDemoDataConstants.NURSE_ROLE);
		setupUser(ReferenceDemoDataConstants.LAB_USER_UUID, "technician", labPerson, "Technician123", labRole);
		setupProvider(ReferenceDemoDataConstants.LAB_PROVIDER_UUID, labPerson, "technician");
		
		Person nursePerson = setupPerson(ReferenceDemoDataConstants.NURSE_PERSON_UUID, "F", "Jane", "Nurse");
		Role nurseRole = userService.getRole(ReferenceDemoDataConstants.NURSE_ROLE);
		setupUser(ReferenceDemoDataConstants.NURSE_USER_UUID, "nurse", nursePerson, "Nurse123", nurseRole);
		setupProvider(ReferenceDemoDataConstants.NURSE_PROVIDER_UUID, nursePerson, "nurse");
		
		Person doctorPerson = setupPerson(ReferenceDemoDataConstants.DOCTOR_PERSON_UUID, "M", "Jake", "Doctor");
		Role doctorRole = userService.getRole(ReferenceDemoDataConstants.DOCTOR_ROLE);
		setupUser(ReferenceDemoDataConstants.DOCTOR_USER_UUID, "doctor", doctorPerson, "Doctor123", doctorRole);
		setupProvider(ReferenceDemoDataConstants.DOCTOR_PROVIDER_UUID, doctorPerson, "doctor");
	}
	
	private void setupProvider(String uuid, Person person, String identifier) {
		ProviderService providerService = Context.getProviderService();
		Provider provider = providerService.getProviderByUuid(uuid);
		if (provider == null) {
			provider = new Provider();
			provider.setUuid(uuid);
		}
		
		provider.setPerson(person);
		provider.setName(person.getPersonName().toString());
		provider.setIdentifier(identifier);
		
		providerService.saveProvider(provider);
	}
	
	private void setupUser(String uuid, String username, Person person, String password, Role... roles) {
		UserService userService = Context.getUserService();
		
		User user = userService.getUserByUuid(uuid);
		if (user == null) {
			user = new User();
			user.setUuid(uuid);
			user.setRoles(new HashSet<>());
		}
		
		user.setUsername(username);
		user.setPerson(person);
		
		user.getRoles().clear();
		for (Role role : roles) {
			// we try to grant some module-defined roles without first verifying those modules/roles exist.
			if (role != null) {
				user.addRole(role);
			}
		}
		
		if (user.getId() == null) {
			userService.createUser(user, password);
		} else {
			userService.saveUser(user);
			userService.changePassword(user, null, password);
		}
	}
	
	private Person setupPerson(String uuid, String gender, String givenName, String familyName) {
		PersonService personService = Context.getPersonService();
		
		Person person = personService.getPersonByUuid(uuid);
		if (person == null) {
			person = new Person();
			person.setUuid(uuid);
		}
		
		person.setGender(gender);
		
		PersonName name = person.getPersonName();
		if (name == null) {
			name = new PersonName();
			person.addName(name);
		}
		name.setGivenName(givenName);
		name.setFamilyName(familyName);
		
		return person;
	}
	
	private IdentifierSourceService getIdentifierSourceService() {
		if (iss == null) {
			iss = Context.getService(IdentifierSourceService.class);
		}
		
		return iss;
	}
	
	protected static void setIdentifierSourceService(IdentifierSourceService iss) {
		ReferenceDemoDataActivator.iss = iss;
	}
}
