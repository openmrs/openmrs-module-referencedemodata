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

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.Period;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Form;
import org.openmrs.GlobalProperty;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.FormService;
import org.openmrs.api.ObsService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.UserService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.ModuleActivator;
import org.openmrs.module.ModuleException;
import org.openmrs.module.ModuleUtil;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.module.emrapi.utils.MetadataUtil;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.metadatadeploy.api.MetadataDeployService;
import org.openmrs.module.metadatadeploy.bundle.MetadataBundle;
import org.openmrs.module.providermanagement.ProviderRole;
import org.openmrs.module.providermanagement.api.ProviderManagementService;
import org.openmrs.module.referencemetadata.ReferenceMetadataConstants;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.util.PrivilegeConstants;
import org.openmrs.util.RoleConstants;

/**
 * This class contains the logic that is run every time this module is either started or stopped.
 */
public class ReferenceDemoDataActivator extends BaseModuleActivator {

	protected Log log = LogFactory.getLog(getClass());
	private IdentifierSourceService iss;	// So unit test can mock it.
    private static Random ConstRand=new Random(0);
    
    private Map<String, Concept> cachedConcepts = new WeakHashMap<String, Concept>();

	/**
	 * @see ModuleActivator#contextRefreshed()
	 */
	@Override
    public void contextRefreshed() {
		log.info("Reference Demo Data Module refreshed");
	}
	
	/**
	 * @see ModuleActivator#started()
	 * @should install the metadata package on startup
	 * @should link the admin account to unknown provider
     * @should create a scheduler user and set the related global properties
	 */
	@Override
    public void started() {
		installMDSPackages();
		//This should probably be removed once a test user is added to demo data
		//See https://tickets.openmrs.org/browse/RA-184
		linkAdminAccountToAProviderIfNecessary();
		setRequiredGlobalProperties();
		setupUsersAndProviders();
        createSchedulerUserAndGPs();
        createAppointmentTypes();
        createDemoPatients();
        
        cachedConcepts.clear();
	}
	
	private void createAppointmentTypes() {
		MetadataBundle bundle = Context.getRegisteredComponent("appointmentschedulingMetadata", MetadataBundle.class);
		Context.getService(MetadataDeployService.class).installBundles(Arrays.asList(bundle));
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
		
		PrivilegeCompatibility privilegeCompatibility = Context.getRegisteredComponents(PrivilegeCompatibility.class).get(0);

		try {
			//If unknown provider isn't yet linked to admin, then do it
			Context.addProxyPrivilege(privilegeCompatibility.GET_PROVIDERS());
			Context.addProxyPrivilege(privilegeCompatibility.GET_PERSONS());
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
			Context.removeProxyPrivilege(privilegeCompatibility.GET_PROVIDERS());
			Context.removeProxyPrivilege(privilegeCompatibility.GET_PERSONS());
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_PROVIDERS);
		}
	}

	private void setupUsersAndProviders() {
		Person clerkPerson = setupPerson(ReferenceDemoDataConstants.CLERK_PERSON_UUID, "M", "John", "Smith");
		Person nursePerson = setupPerson(ReferenceDemoDataConstants.NURSE_PERSON_UUID, "F", "Jane", "Smith");
		Person doctorPerson = setupPerson(ReferenceDemoDataConstants.DOCTOR_PERSON_UUID, "M", "Jake", "Smith");
        Person sysadminPerson = setupPerson(ReferenceDemoDataConstants.SYSADMIN_PERSON_UUID, "F", "Julie", "Smith");

		UserService userService = Context.getUserService();
		Role clerkRole = userService.getRole(ReferenceDemoDataConstants.CLERK_ROLE);
		Role nurseRole = userService.getRole(ReferenceDemoDataConstants.NURSE_ROLE);
		Role doctorRole = userService.getRole(ReferenceDemoDataConstants.DOCTOR_ROLE);
		Role sysadminRole = userService.getRole(ReferenceDemoDataConstants.SYSADMIN_ROLE);

		setupUser(ReferenceDemoDataConstants.CLERK_USER_UUID, "clerk", clerkPerson, "Clerk123", clerkRole);
		setupUser(ReferenceDemoDataConstants.NURSE_USER_UUID, "nurse", nursePerson, "Nurse123", nurseRole);
		setupUser(ReferenceDemoDataConstants.DOCTOR_USER_UUID, "doctor", doctorPerson, "Doctor123", doctorRole);
        setupUser(ReferenceDemoDataConstants.SYSADMIN_USER_UUID, "sysadmin", sysadminPerson, "Sysadmin123", sysadminRole);

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
        for (Role role : roles) {
            // we try to grant some module-defined roles without first verifying those modules/roles exist.
            if (role != null) {
                user.addRole(role);
            }
        }
		user = Context.getRegisteredComponents(UserServiceCompatibility.class).get(0).saveUser(user, password);

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
		propertyValueMap.put(ReferenceDemoDataConstants.CREATE_DEMO_PATIENTS_ON_NEXT_STARTUP, "0");
		
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

		GlobalProperty gp = new GlobalProperty("layout.address.format", "<org.openmrs.layout.address.AddressTemplate>\n" +
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
				"   </org.openmrs.layout.address.AddressTemplate>");
		
		//versions before platform 2.0 use a package in web
		if (!ModuleUtil.matchRequiredVersions(OpenmrsConstants.OPENMRS_VERSION_SHORT, "2.*")) {
			gp = new GlobalProperty("layout.address.format", "<org.openmrs.layout.web.address.AddressTemplate>\n" +
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
					"   </org.openmrs.layout.web.address.AddressTemplate>");
		}
		
		as.saveGlobalProperty(gp);
				
		//Hack to address RA-631 - clear out bogus default regex for patient name validation
		//and then blanks out the global property if it contains the bogus entry set by platform
		//TODO this can be removed once the 1.11.3 platform is released with the fix
		gp = as.getGlobalPropertyObject(OpenmrsConstants.GLOBAL_PROPERTY_PATIENT_NAME_REGEX);
		if (gp != null && "^[a-zA-Z \\-]+$".equals(gp.getValue())) {
			gp.setPropertyValue(null);
			as.saveGlobalProperty(gp);
		}
	}

    private void createSchedulerUserAndGPs() {
        UserService us = Context.getUserService();
        if (us.getUserByUsername(ReferenceDemoDataConstants.SCHEDULER_USERNAME) == null) {
            Person person = Context.getPersonService().getPerson(1);
            //Apparently admin has no name, set it to pass validation
            person.addName(new PersonName("super", null, "user"));
            Role superuserRole = us.getRole(RoleConstants.SUPERUSER);
            setupUser(ReferenceDemoDataConstants.SCHEDULER_USER_UUID, ReferenceDemoDataConstants.SCHEDULER_USERNAME, person,
                    ReferenceDemoDataConstants.SCHEDULER_PASSWORD, superuserRole);
            AdministrationService adminService = Context.getAdministrationService();
            GlobalProperty usernameGP = adminService.getGlobalPropertyObject("scheduler.username");
            if (usernameGP == null) {
                usernameGP = new GlobalProperty("scheduler.username", ReferenceDemoDataConstants.SCHEDULER_USERNAME);
            } else {
                usernameGP.setPropertyValue(ReferenceDemoDataConstants.SCHEDULER_USERNAME);
            }
            adminService.saveGlobalProperty(usernameGP);

            GlobalProperty passwordGP = adminService.getGlobalPropertyObject("scheduler.password");
            if (passwordGP == null) {
                passwordGP = new GlobalProperty("scheduler.password", ReferenceDemoDataConstants.SCHEDULER_PASSWORD);
            } else {
                passwordGP.setPropertyValue(ReferenceDemoDataConstants.SCHEDULER_PASSWORD);
            }
            adminService.saveGlobalProperty(passwordGP);
        }
    }

    // TODO Move all this demo-patient stuff to a separate class.
	private void createDemoPatients() {
		AdministrationService as = Context.getAdministrationService();
		GlobalProperty gp = as.getGlobalPropertyObject(ReferenceDemoDataConstants.CREATE_DEMO_PATIENTS_ON_NEXT_STARTUP);
		if (gp == null || (gp.getPropertyValue().equals("0"))) {
			return;
		}
		createVitalsForm();
		OpenmrsUtil.applyLogLevel(getClass().getName(), OpenmrsConstants.LOG_LEVEL_INFO);	// force the "created demo patient" below to show up
		
		int patientCount = Integer.parseInt(gp.getPropertyValue());

		PatientService ps = Context.getPatientService();
		Location rootLocation = randomArrayEntry(Context.getLocationService().getRootLocations(false));
		PatientIdentifierType patientIdentifierType = ps.getPatientIdentifierTypeByName(ReferenceMetadataConstants.OPENMRS_ID_NAME);
		for (int i = 0; i < patientCount; i++) {
			Patient patient = createDemoPatient(ps, patientIdentifierType, rootLocation);
			log.info("created demo patient: " + patient.getPatientIdentifier() + " " + patient.getGivenName() + " " + patient.getFamilyName());
			Context.flushSession();
			Context.clearSession();
		}

		// Set the global to zero so we won't create demo patients next time.
		gp.setPropertyValue("0");
		as.saveGlobalProperty(gp);
    }

	// A bit of a hack - see https://tickets.openmrs.org/browse/RA-264. 
	private void createVitalsForm() {
		FormService fs = Context.getFormService();
		if (fs.getFormByUuid(ReferenceDemoDataConstants.VITALS_FORM_UUID) != null) {
			return;
		}
		Form form = new Form();
		form.setUuid(ReferenceDemoDataConstants.VITALS_FORM_UUID);
		form.setName(ReferenceDemoDataConstants.VITALS_FORM_NAME);
		form.setEncounterType(Context.getEncounterService().getEncounterTypeByUuid(ReferenceDemoDataConstants.VITALS_FORM_ENCOUNTERTYPE_UUID));
		form.setVersion("1.0");
		fs.saveForm(form);
    }

	private Patient createDemoPatient(PatientService ps, PatientIdentifierType patientIdentifierType, Location location) {
	    Patient patient = createBasicDemoPatient(patientIdentifierType, location);
		patient = ps.savePatient(patient);
	    VisitService vs = Context.getVisitService();
	    int visitCount = randomBetween(0, 10);
	    for (int i = 0; i < visitCount; i++) {
	    	boolean shortVisit = i < (visitCount * 0.75);
	    	Visit visit = createDemoVisit(patient, vs.getAllVisitTypes(), location, shortVisit);
			vs.saveVisit(visit);
        }
	    return patient;
    }

	// Used by unit test
    public void setIdentifierSourceService(IdentifierSourceService iss) {
    	this.iss = iss;
    }

	private IdentifierSourceService getIdentifierSourceService() {
		if (iss == null) {
			iss = Context.getService(IdentifierSourceService.class);
		}
		return iss;
	}

	private Patient createBasicDemoPatient(PatientIdentifierType patientIdentifierType, Location location) {
		Patient patient = new Patient();
		
		PersonName pName = new PersonName();
		String gender = randomArrayEntry(GENDERS);
		boolean male = gender.equals("M");
		pName.setGivenName(randomArrayEntry(male ? MALE_FIRST_NAMES : FEMALE_FIRST_NAMES));
		pName.setFamilyName(randomArrayEntry(FAMILY_NAMES));
		patient.addName(pName);
		
		PersonAddress pAddress = new PersonAddress();
		String randomSuffix = randomSuffix();
		pAddress.setAddress1("Address1" + randomSuffix);
		pAddress.setCityVillage("City" + randomSuffix);
		pAddress.setStateProvince("State" + randomSuffix);
		pAddress.setCountry("Country" + randomSuffix);
		pAddress.setPostalCode(randomSuffix(5));
		patient.addAddress(pAddress);
		
		patient.setBirthdate(randomBirthdate());
		patient.setBirthdateEstimated(false);
		patient.setGender(gender);
		
		PatientIdentifier pa1 = new PatientIdentifier();
		pa1.setIdentifier(getIdentifierSourceService().generateIdentifier(patientIdentifierType, "DemoData"));
		pa1.setIdentifierType(patientIdentifierType);
		pa1.setDateCreated(new Date());
		pa1.setLocation(location);
		patient.addIdentifier(pa1);

		return patient;
	}
	
	private static final int ADMISSION_DAYS_MIN = 1;
	private static final int ADMISSION_DAYS_MAX = 3;
	
	private Visit createDemoVisit(Patient patient, List<VisitType> visitTypes, Location location, boolean shortVisit) {
		LocalDateTime visitStart = LocalDateTime.now().minus(Period.days(randomBetween(0, 365*2)).withHours(3));	// past 2 years
		if (!shortVisit) {
			visitStart = visitStart.minus(Period.days(ADMISSION_DAYS_MAX+1));	// just in case the start is today, back it up a few days.
		}
		Visit visit = new Visit(patient, randomArrayEntry(visitTypes), visitStart.toDate());
		visit.setLocation(location);
		LocalDateTime vitalsTime = visitStart.plus(Period.minutes(randomBetween(1, 60)));
		visit.addEncounter(createDemoVitalsEncounter(patient, vitalsTime.toDate()));
		LocalDateTime visitNoteTime = visitStart.plus(Period.minutes(randomBetween(60, 120)));
		visit.addEncounter(createVisitNote(patient, visitNoteTime.toDate(), location));
		if (shortVisit) {
			LocalDateTime visitEndTime = visitNoteTime.plus(Period.minutes(30));
			visit.setStopDatetime(visitEndTime.toDate());
		} else {
			// admit now and discharge a few days later
			Location admitLocation = Context.getLocationService().getLocation("Inpatient Ward");
			visit.addEncounter(createEncounter("Admission", patient, visitNoteTime.toDate(), admitLocation));
			LocalDateTime dischargeDateTime = visitNoteTime.plus(Period.days(randomBetween(ADMISSION_DAYS_MIN, ADMISSION_DAYS_MAX)));
			visit.addEncounter(createEncounter("Discharge", patient, dischargeDateTime.toDate(), admitLocation));
			visit.setStopDatetime(dischargeDateTime.toDate());
		}
		return visit;
	}
	
	private Encounter createVisitNote(Patient patient, Date encounterTime, Location location) {
		ObsService os = Context.getObsService();
		ConceptService cs = Context.getConceptService();
	    Encounter visitNote = createEncounter("Visit Note", patient, encounterTime, location);
	    visitNote.setForm(Context.getFormService().getForm("Visit Note"));
	    Context.getEncounterService().saveEncounter(visitNote);
	    
	    createTextObs("Text of encounter note"/*CIEL:162169*/, randomArrayEntry(RANDOM_TEXT), patient, visitNote, encounterTime, location, os, cs);

	    createDiagnosisObsGroup(true, patient, visitNote, encounterTime, location, os, cs);
	    
	    if (flipACoin()) {
	    	// add a second diagnosis
		    createDiagnosisObsGroup(false, patient, visitNote, encounterTime, location, os, cs);
	    }

	    return visitNote;
    }

	private void createDiagnosisObsGroup(boolean primary, Patient patient, Encounter visitNote, Date encounterTime,
                                    Location location, ObsService os, ConceptService cs) {
		Obs obsGroup = createBasicObs("Visit Diagnoses", patient, encounterTime, location, cs);
	    visitNote.addObs(obsGroup);

	    String certainty = flipACoin() ? "Presumed diagnosis" : "Confirmed diagnosis";
	    Obs obs = createCodedObs(EmrApiConstants.CONCEPT_CODE_DIAGNOSIS_CERTAINTY, certainty, patient, visitNote, encounterTime, location, os, cs);
	    obsGroup.addGroupMember(obs);

	    // TODO 5% of diagnoses should be non-coded.
	    List<Concept> allDiagnoses = cs.getConceptsByClass(cs.getConceptClassByName("Diagnosis"));
	    obs = createCodedObs("DIAGNOSIS LIST", randomArrayEntry(allDiagnoses), patient, visitNote, encounterTime, location, os, cs);
	    obsGroup.addGroupMember(obs);
	    
	    String order = primary ? EmrApiConstants.CONCEPT_CODE_DIAGNOSIS_ORDER_PRIMARY : EmrApiConstants.CONCEPT_CODE_DIAGNOSIS_ORDER_SECONDARY;
	    obs = createCodedObs(EmrApiConstants.CONCEPT_CODE_DIAGNOSIS_ORDER, order, patient, visitNote, encounterTime, location, os, cs);
	    obsGroup.addGroupMember(obs);
    }

	private Encounter createDemoVitalsEncounter(Patient patient, Date encounterTime) {
		Location location = Context.getLocationService().getLocation("Outpatient Clinic");
		Encounter encounter = createEncounter("Vitals", patient, encounterTime, location);
	    encounter.setForm(Context.getFormService().getForm("Vitals"));
		createDemoVitalsObs(patient, encounter, encounterTime, location);
		return encounter;
	}
	
	private Encounter createEncounter(String encounterType, Patient patient, Date encounterTime, Location location) {
		EncounterService es = Context.getEncounterService();
		Encounter encounter = new Encounter();
		encounter.setEncounterDatetime(encounterTime);
		encounter.setEncounterType(es.getEncounterType(encounterType));
		encounter.setPatient(patient);
		encounter.setLocation(location);
		es.saveEncounter(encounter);
		return encounter;
	}
	
	private void createDemoVitalsObs(Patient patient, Encounter encounter, Date encounterTime, Location location) {
		ObsService os = Context.getObsService();
		ConceptService cs = Context.getConceptService();
        createNumericObs("Height (cm)", 10, 228, patient, encounter, encounterTime, location, os, cs);
        createNumericObs("Weight (kg)", 1, 250, patient, encounter, encounterTime, location, os, cs);
        createNumericObs("Temperature (C)", 25, 43, patient, encounter, encounterTime, location, os, cs);
        createNumericObs("Pulse", 0, 230, patient, encounter, encounterTime, location, os, cs);
        createNumericObs("Respiratory rate", 5, 100, patient, encounter, encounterTime, location, os, cs);
        createNumericObs("SYSTOLIC BLOOD PRESSURE", 0, 250, patient, encounter, encounterTime, location, os, cs);
        createNumericObs("DIASTOLIC BLOOD PRESSURE", 0, 150, patient, encounter, encounterTime, location, os, cs);
        createNumericObs("Blood oxygen saturation", 0, 100, patient, encounter, encounterTime, location, os, cs);
	}

	private void createNumericObs(String conceptName, int min, int max, Patient patient, Encounter encounter, Date encounterTime, Location location,
                                  ObsService os, ConceptService cs) {
		Obs obs = createBasicObs(conceptName, patient, encounterTime, location, cs);
		obs.setValueNumeric((double) randomBetween(min, max));
		os.saveObs(obs, null);
		encounter.addObs(obs);
    }
	
	private void createTextObs(String conceptName, String text, Patient patient, Encounter encounter, Date encounterTime,
	                           Location location, ObsService os, ConceptService cs) {
		Obs obs = createBasicObs(conceptName, patient, encounterTime, location, cs);
		obs.setValueText(text);
		os.saveObs(obs, null);
		encounter.addObs(obs);
	}
	
	private Obs createCodedObs(String conceptName, String codedConceptName, Patient patient, Encounter encounter, Date encounterTime,
	                           Location location, ObsService os, ConceptService cs) {
		return createCodedObs(conceptName, findConcept(codedConceptName, cs), patient, encounter, encounterTime, location, os, cs);
	}

	private Concept findConcept(String conceptName, ConceptService cs) {
		if (cachedConcepts.containsKey(conceptName)) {
			return cachedConcepts.get(conceptName);
		} else {
			Concept concept = cs.getConcept(conceptName);
			cachedConcepts.put(conceptName, concept);
			return concept;
		}
    }
	
	private Obs createCodedObs(String conceptName, Concept concept, Patient patient, Encounter encounter,
                               Date encounterTime, Location location, ObsService os, ConceptService cs) {
		Obs obs = createBasicObs(conceptName, patient, encounterTime, location, cs);
		obs.setValueCoded(concept);
		os.saveObs(obs, null);
		encounter.addObs(obs);
		return obs;
    }

	private Obs createBasicObs(String conceptName, Patient patient, Date encounterTime, Location location, ConceptService cs) {
		Concept concept = findConcept(conceptName, cs);
		if (concept == null) {
			log.warn("incorrect concept name? " + conceptName);
		}
		return new Obs(patient, concept, encounterTime, location);
	}
	
	private static final int MIN_AGE = 16;
	private static final int MAX_AGE = 90;
	
	private Date randomBirthdate() {
		LocalDate now = LocalDate.now();
		LocalDate joda = new LocalDate(randomBetween(now.getYear() - MAX_AGE, now.getYear() - MIN_AGE), randomBetween(1,12), randomBetween(1, 28));
	    return joda.toDate();
    }

	static private int randomBetween(int min, int max) {
	    return min + (int) (ConstRand.nextDouble() * (max-min+1));
    }
	static int randomArrayIndex(int length) {
		return (int) (ConstRand.nextDouble() * length);
	}
	static int randomArrayIndex(String[] array) {
		return randomArrayIndex(array.length);
	}
	static String randomArrayEntry(String[] array) {
		return array[randomArrayIndex(array)];
	}
	static <T> T randomArrayEntry(List<T> list) {
		return list.get(randomArrayIndex(list.size()));
	}
	static String randomSuffix() {
		return randomSuffix(4);
	}
	static String randomSuffix(int digits) {
		// Last n digits of the current time.
		return StringUtils.right(String.valueOf(System.currentTimeMillis()), digits);
	}
	static boolean flipACoin() {
		return randomBetween(0,1) == 0;
	}

	private static final String[] GENDERS = {"M", "F"};
	
	private static final String[] MALE_FIRST_NAMES = { "James", "John", "Robert", "Michael", "William", "David", "Richard",
        "Joseph", "Charles", "Thomas", "Christopher", "Daniel", "Matthew", "Donald", "Anthony", "Paul", "Mark",
        "George", "Steven", "Kenneth", "Andrew", "Edward", "Brian", "Joshua", "Kevin" };

	private static final String[] FEMALE_FIRST_NAMES = { "Mary", "Patricia", "Elizabeth", "Jennifer", "Linda", "Barbara",
        "Susan", "Margaret", "Jessica", "Dorothy", "Sarah", "Karen", "Nancy", "Betty", "Lisa", "Sandra", "Helen",
        "Donna", "Ashley", "Kimberly", "Carol", "Michelle", "Amanda", "Emily", "Melissa" };

	private static final String[] FAMILY_NAMES = { "Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis",
        "García", "Rodríguez", "Wilson", "Martínez", "Anderson", "Taylor", "Thomas", "Hernández", "Moore", "Martin",
        "Jackson", "Thompson", "White", "López", "Lee", "González", "Harris", "Clark", "Lewis", "Robinson", "Walker",
        "Pérez", "Hall", "Young", "Allen", "Sánchez", "Wright", "King", "Scott", "Green", "Baker", "Adams", "Nelson",
        "Hill", "Ramírez", "Campbell", "Mitchell", "Roberts", "Carter", "Phillips", "Evans", "Turner", "Torres" };
	
	private static final String[] RANDOM_TEXT = {
		"Lorem ipsum dolor sit amet", 
		"consectetur adipisicing elit", 
		"sed do eiusmod tempor incididunt", 
		"ut labore et dolore magna aliqua",
		"Ut enim ad minim veniam", 
		"quis nostrud exercitation ullamco laboris",
		"nisi ut aliquip ex ea commodo consequat.",
		"Duis aute irure dolor in reprehenderit in voluptat",
		"velit esse cillum dolore eu fugiat nulla pariatur.",
		"Excepteur sint occaecat cupidatat non proident", 
		"sunt in culpa qui officia deserunt",
		"mollit anim id est laborum."};
	
}
