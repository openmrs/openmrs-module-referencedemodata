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
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.APIException;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.referencedemodata.Randomizer;

import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.OPENMRS_ID_NAME;
import static org.openmrs.module.referencedemodata.patient.DemoPersonGenerator.populatePerson;

@Slf4j
public class DemoPatientGenerator {
	
	private final IdentifierSourceService iss;
	
	public DemoPatientGenerator(IdentifierSourceService iss) {
		this.iss = iss;
	}
	
	/**
	 * Main entry point to create patients. Creates the specified number of patients and returns a list of database IDs for
	 * those patients.
	 *
	 * @param patientCount number of patients to create
	 * @return a list of the primary keys for each patient created
	 */
	public List<Integer> createDemoPatients(int patientCount) {
		List<Integer> patientIds = new ArrayList<>(patientCount);
		
		PatientService ps = Context.getPatientService();
		Location rootLocation = Randomizer.randomListEntry(Context.getLocationService().getRootLocations(false));
		PatientIdentifierType patientIdentifierType = ps.getPatientIdentifierTypeByName(OPENMRS_ID_NAME);
		
		if (patientIdentifierType == null) {
			throw new APIException("Could not find identifier type " + OPENMRS_ID_NAME);
		}
		
		for (int i = 0; i < patientCount; i++) {
			Patient patient = createDemoPatient(ps, patientIdentifierType, rootLocation);
			log.info("created demo patient: {} {} {} age: {}",
					new Object[] { patient.getPatientIdentifier(), patient.getGivenName(), patient.getFamilyName(),
							patient.getAge() });
			patientIds.add(patient.getId());
		}
		
		return patientIds;
	}
	
	private Patient createDemoPatient(PatientService ps, PatientIdentifierType patientIdentifierType, Location location) {
		Patient patient = new Patient();
		
		populatePerson(patient);
		
		PatientIdentifier patientIdentifier = new PatientIdentifier();
		patientIdentifier.setIdentifier(iss.generateIdentifier(patientIdentifierType, "DemoData"));
		patientIdentifier.setIdentifierType(patientIdentifierType);
		patientIdentifier.setDateCreated(new Date());
		patientIdentifier.setLocation(location);
		patient.addIdentifier(patientIdentifier);
		
		patient = ps.savePatient(patient);
		return patient;
	}
}
