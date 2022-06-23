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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.openmrs.GlobalProperty;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.ModuleActivator;
import org.openmrs.module.ModuleFactory;
import org.openmrs.module.referencedemodata.patient.DemoPatientGenerator;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.openmrs.util.OpenmrsConstants.GP_CASE_SENSITIVE_DATABASE_STRING_COMPARISON;

/**
 * This is the activator responsible for setting up the demo data, if required. Note that setup will only be done
 * if the `referencedemodata.createDemoPatientsOnNextStartup` setting has a non-zero value.
 */
public class ReferenceDemoDataActivator extends BaseModuleActivator {
	
	private static final Logger log = LoggerFactory.getLogger(ReferenceDemoDataActivator.class);
	
	private static final String MODULE_ID = "referencedemodata";
	
	/**
	 * @see ModuleActivator#started()
	 */
	@Override
	public void started() {
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
				
				ClassLoader cl = null;
				try {
					cl = ModuleFactory.getModuleClassLoader(MODULE_ID);
				}
				catch (NullPointerException ignored) {
				
				}
				
				if (cl == null) {
					cl = Thread.currentThread().getContextClassLoader();
				}
				
				PathMatchingResourcePatternResolver patternResolver =
						new PathMatchingResourcePatternResolver(new DefaultResourceLoader(cl));
				
				// Here we are going to make global property set to true. Reason, is while fetching concepts the case for concept name is different
				// from what we are sending to service call. This causes problem in PostgreSQL. This is not a problem in MySQL because For MySQL,
				// the collation is utf8_general_ci that is case insensitive so case does not matter but in other dbs like PostgreSQL it does.
				boolean valueBefore = Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive();
				try {
					new DemoPatientGenerator(patternResolver).createDemoPatients(patientCount);
				}
				finally {
					// Restore the value of Global Property
					Context.getAdministrationService()
							.setGlobalProperty(GP_CASE_SENSITIVE_DATABASE_STRING_COMPARISON, String.valueOf(valueBefore));
				}
			}
			catch (Exception e) {
				log.error("Exception caught while creating demo data", e);
			}
			finally {
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
		
		Person nursePerson = setupPerson(ReferenceDemoDataConstants.NURSE_PERSON_UUID, "F", "Jane", "Nurse");
		Role nurseRole = userService.getRole(ReferenceDemoDataConstants.NURSE_ROLE);
		setupUser(ReferenceDemoDataConstants.NURSE_USER_UUID, "nurse", nursePerson, "Nurse123", nurseRole);
		
		Person doctorPerson = setupPerson(ReferenceDemoDataConstants.DOCTOR_PERSON_UUID, "M", "Jake", "Doctor");
		Role doctorRole = userService.getRole(ReferenceDemoDataConstants.DOCTOR_ROLE);
		setupUser(ReferenceDemoDataConstants.DOCTOR_USER_UUID, "doctor", doctorPerson, "Doctor123", doctorRole);
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
}
