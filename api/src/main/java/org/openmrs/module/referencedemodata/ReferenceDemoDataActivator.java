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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptSource;
import org.openmrs.GlobalProperty;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.ModuleActivator;
import org.openmrs.module.ModuleException;
import org.openmrs.module.emrapi.utils.MetadataUtil;
import org.openmrs.module.providermanagement.ProviderRole;
import org.openmrs.module.providermanagement.api.ProviderManagementService;
import org.openmrs.util.PrivilegeConstants;
import org.openmrs.util.RoleConstants;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * This class contains the logic that is run every time this module is either started or stopped.
 */
public class ReferenceDemoDataActivator extends BaseModuleActivator {

	protected Log log = LogFactory.getLog(getClass());
	
	/**
	 * @see ModuleActivator#contextRefreshed()
	 */
	public void contextRefreshed() {
		log.info("Reference Demo Data Module refreshed");
	}
	
	/**
	 * @see ModuleActivator#started()
	 * @should install the metadata package on startup
	 * @should link the admin account to unknown provider
     * @should create a scheduler user and set the related global properties
	 */
	public void started() {
		installMDSPackages();
		//This should probably be removed once a test user is added to demo data
		//See https://tickets.openmrs.org/browse/RA-184
		linkAdminAccountToAProviderIfNecessary();
		setRequiredGlobalProperties();
		setupUsersAndProviders();
        createSchedulerUserAndGPs();
	}
	
	private void installMDSPackages() {
		try {
			MetadataUtil.setupStandardMetadata(getClass().getClassLoader(), "org/openmrs/module/referencedemodata/packages.xml");
		}
		catch (Exception e) {
			throw new ModuleException("Failed to load reference demo data MDS packages", e);
		}
		
		log.info("Reference Demo Data Module started");
	}
	
	private void linkAdminAccountToAProviderIfNecessary() {
		try {
			//If unknown provider isn't yet linked to admin, then do it
			Context.addProxyPrivilege(PrivilegeConstants.VIEW_PROVIDERS);
			Context.addProxyPrivilege(PrivilegeConstants.VIEW_PERSONS);
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
			Context.removeProxyPrivilege(PrivilegeConstants.VIEW_PROVIDERS);
			Context.removeProxyPrivilege(PrivilegeConstants.VIEW_PERSONS);
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_PROVIDERS);
		}
	}

	private void setupUsersAndProviders() {
		Person clerkPerson = setupPerson(ReferenceDemoDataConstants.CLERK_PERSON_UUID, "M", "John", "Smith");
		Person nursePerson = setupPerson(ReferenceDemoDataConstants.NURSE_PERSON_UUID, "F", "Jane", "Smith");
		Person doctorPerson = setupPerson(ReferenceDemoDataConstants.DOCTOR_PERSON_UUID, "M", "Jake", "Smith");

		UserService userService = Context.getUserService();
		Role clerkRole = userService.getRoleByUuid(ReferenceDemoDataConstants.CLERK_ROLE_UUID);
		Role nurseRole = userService.getRoleByUuid(ReferenceDemoDataConstants.NURSE_ROLE_UUID);
		Role doctorRole = userService.getRoleByUuid(ReferenceDemoDataConstants.DOCTOR_ROLE_UUID);

		setupUser(ReferenceDemoDataConstants.CLERK_USER_UUID, "clerk", clerkPerson, "Clerk123", clerkRole);
		setupUser(ReferenceDemoDataConstants.NURSE_USER_UUID, "nurse", nursePerson, "Nurse123", nurseRole);
		setupUser(ReferenceDemoDataConstants.DOCTOR_USER_UUID, "doctor", doctorPerson, "Doctor123", doctorRole);

		ProviderManagementService providerManagementService = Context.getService(ProviderManagementService.class);

		ProviderRole clerkProviderRole = providerManagementService.getProviderRoleByUuid(ReferenceDemoDataConstants.CLERK_PROVIDER_ROLE_UUID);
		ProviderRole nurseProviderRole = providerManagementService.getProviderRoleByUuid(ReferenceDemoDataConstants.NURSE_PROVIDER_ROLE_UUID);
		ProviderRole doctorProviderRole = providerManagementService.getProviderRoleByUuid(ReferenceDemoDataConstants.DOCTOR_PROVIDER_ROLE_UUID);

		providerManagementService.assignProviderRoleToPerson(clerkPerson, clerkProviderRole, "clerk");
		providerManagementService.assignProviderRoleToPerson(nursePerson, nurseProviderRole, "nurse");
		providerManagementService.assignProviderRoleToPerson(doctorPerson, doctorProviderRole, "doctor");
	}

	private User setupUser(String uuid, String username, Person person, String password, Role... roles) {
		UserService userService = Context.getUserService();

		User user = userService.getUserByUuid(uuid);
		if (user == null) {
			user = new User();
			user.setUuid(uuid);
			user.setRoles(new HashSet<Role>());
		}
		user.setUsername(username);
		user.setPerson(person);

		user.getRoles().clear();
		user.getRoles().addAll(Arrays.asList(roles));
		user = userService.saveUser(user, password);

		return user;
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
	
	private void setRequiredGlobalProperties() {
		AdministrationService as = Context.getAdministrationService();
		Map<String, String> propertyValueMap = new HashMap<String, String>();
		//Add more GPs here
		propertyValueMap.put("registrationcore.identifierSourceId", "1");
		
		for (Map.Entry<String, String> entry : propertyValueMap.entrySet()) {
			if (StringUtils.isBlank(as.getGlobalProperty(entry.getKey()))) {
				GlobalProperty gp = as.getGlobalPropertyObject(entry.getKey());
				if (gp == null) {
					gp = new GlobalProperty();
					gp.setProperty(entry.getKey());
				}
				gp.setPropertyValue(entry.getValue());
				as.saveGlobalProperty(gp);
			}
		}

		as.saveGlobalProperty(new GlobalProperty("scheduler.username", "admin"));
		as.saveGlobalProperty(new GlobalProperty("scheduler.password", "Admin123"));

		as.saveGlobalProperty(new GlobalProperty("layout.address.format", "<org.openmrs.layout.web.address.AddressTemplate>\n" +
				"     <nameMappings class=\"properties\">\n" +
				"       <property name=\"postalCode\" value=\"Location.postalCode\"/>\n" +
				"       <property name=\"address2\" value=\"Location.address2\"/>\n" +
				"       <property name=\"address1\" value=\"Location.address1\"/>\n" +
				"       <property name=\"country\" value=\"Location.country\"/>\n" +
				"       <property name=\"stateProvince\" value=\"Location.stateProvince\"/>\n" +
				"       <property name=\"cityVillage\" value=\"Location.cityVillage\"/>\n" +
				"     </nameMappings>\n" +
				"     <sizeMappings class=\"properties\">\n" +
				"       <property name=\"postalCode\" value=\"10\"/>\n" +
				"       <property name=\"address2\" value=\"40\"/>\n" +
				"       <property name=\"address1\" value=\"40\"/>\n" +
				"       <property name=\"country\" value=\"10\"/>\n" +
				"       <property name=\"stateProvince\" value=\"10\"/>\n" +
				"       <property name=\"cityVillage\" value=\"10\"/>\n" +
				"     </sizeMappings>\n" +
				"     <lineByLineFormat>\n" +
				"       <string>address1</string>\n" +
				"       <string>address2</string>\n" +
				"       <string>cityVillage stateProvince country postalCode</string>\n" +
				"     </lineByLineFormat>\n" +
				"   </org.openmrs.layout.web.address.AddressTemplate>"));
	}

    private void createSchedulerUserAndGPs() {
        UserService us = Context.getUserService();
        if (us.getUserByUsername(ReferenceDemoDataConstants.SCHEDULER_USERNAME) == null) {
            Person person = Context.getPersonService().getPerson(1);
            //Apparently admin has no name, set it to pass validation
            person.addName(new PersonName("super", null, "user"));
            Role superuserRole = us.getRole(RoleConstants.SUPERUSER);
            setupUser(ReferenceDemoDataConstants.SCHEDULER_USER_UUID, ReferenceDemoDataConstants.SCHEDULER_USERNAME, person,
                    "Scheduler123", superuserRole);
            AdministrationService adminService = Context.getAdministrationService();
            GlobalProperty usernameGP = adminService.getGlobalPropertyObject("scheduler.username");
            usernameGP.setPropertyValue(ReferenceDemoDataConstants.SCHEDULER_USERNAME);
            adminService.saveGlobalProperty(usernameGP);
            GlobalProperty passwordGP = adminService.getGlobalPropertyObject("scheduler.password");
            passwordGP.setPropertyValue("Scheduler123");
            adminService.saveGlobalProperty(passwordGP);
        }
    }
}
