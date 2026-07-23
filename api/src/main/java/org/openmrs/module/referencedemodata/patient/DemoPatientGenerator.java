/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.patient;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.api.APIException;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.referencedemodata.Randomizer;

import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.OPENMRS_ID_NAME;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.DEMO_PATIENT_ATTR;
import static org.openmrs.module.referencedemodata.patient.DemoPersonGenerator.populatePerson;

@Slf4j
public class DemoPatientGenerator {

	private final IdentifierSourceService iss;

	public DemoPatientGenerator(IdentifierSourceService iss) {
		this.iss = iss;
	}

	/**
	 * Main entry point to create patients. Creates the specified number of patients and returns the
	 * created {@link Patient} entities in creation order.
	 *
	 * @param patientCount number of patients to create
	 * @return the created patients
	 */
	public List<Patient> createDemoPatients(int patientCount) {
		List<Patient> patients = new ArrayList<>(patientCount);

		PatientService ps = Context.getPatientService();
		Location rootLocation = Randomizer.randomListEntry(Context.getLocationService().getRootLocations(false));

		for (int i = 0; i < patientCount; i++) {
			Patient patient = createDemoPatient(ps, rootLocation);
			log.info("created demo patient: {} {} {} age: {}",
					new Object[] { patient.getPatientIdentifier(), patient.getGivenName(), patient.getFamilyName(),
							patient.getAge() });
			patients.add(patient);
		}

		return patients;
	}

	/**
	 * Builds an unsaved {@link Patient} skeleton with the given UUID (if non-blank), an OpenMRS
	 * identifier generated from the {@link IdentifierSourceService} attached at {@code location},
	 * and the demo person attribute. Callers are responsible for layering demographics and saving.
	 *
	 * @param patientUuid the UUID to assign, or {@code null}/blank to let the platform generate one
	 * @param location the location to attach the patient identifier to
	 * @return an unsaved {@link Patient} with identifier and demo attribute attached
	 */
	public Patient createPatientShell(String patientUuid, Location location) {
		PatientService ps = Context.getPatientService();
		PatientIdentifierType patientIdentifierType = ps.getPatientIdentifierTypeByName(OPENMRS_ID_NAME);
		if (patientIdentifierType == null) {
			throw new APIException("Could not find identifier type " + OPENMRS_ID_NAME);
		}
		PersonAttributeType personAttributeType = ensureDemoPatientAttributeType();

		Patient patient = new Patient();
		if (StringUtils.isNotBlank(patientUuid)) {
			patient.setUuid(patientUuid);
		}

		PatientIdentifier patientIdentifier = new PatientIdentifier();
		patientIdentifier.setIdentifier(iss.generateIdentifier(patientIdentifierType, "DemoData"));
		patientIdentifier.setIdentifierType(patientIdentifierType);
		patientIdentifier.setDateCreated(new Date());
		patientIdentifier.setLocation(location);
		patient.addIdentifier(patientIdentifier);

		PersonAttribute personAttribute = new PersonAttribute();
		personAttribute.setAttributeType(personAttributeType);
		personAttribute.setValue("true");
		patient.addAttribute(personAttribute);

		return patient;
	}

	private Patient createDemoPatient(PatientService ps, Location location) {
		Patient patient = createPatientShell(null, location);
		populatePerson(patient);
		return ps.savePatient(patient);
	}

	private PersonAttributeType ensureDemoPatientAttributeType() {
		PersonService personService = Context.getPersonService();
		PersonAttributeType personAttributeType = personService.getPersonAttributeTypeByName(DEMO_PATIENT_ATTR);
		if (personAttributeType == null) {
			personAttributeType = new PersonAttributeType();
			personAttributeType.setName(DEMO_PATIENT_ATTR);
			personAttributeType.setFormat("java.lang.Boolean");
			personAttributeType.setSearchable(true);
			personAttributeType = personService.savePersonAttributeType(personAttributeType);
		}
		return personAttributeType;
	}
}
