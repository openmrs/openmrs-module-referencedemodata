/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.visit;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.openmrs.Encounter;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.api.FormService;
import org.openmrs.api.context.Context;

/**
 * Shared helpers for creating Encounters, looking up/creating EncounterTypes, and resolving
 * the Clinician encounter role and Visit Note form. Used by both {@link DemoVisitGenerator}
 * (random demo data) and the fixture-driven applier.
 */
public class EncounterFactory {

	private final Map<String, EncounterType> encounterTypeCache = new HashMap<>();

	private EncounterRole clinicianRole;

	private Form visitNoteForm;

	public Encounter createEncounter(String typeName, Patient patient, Date date, Location location, Provider provider) {
		Encounter encounter = new Encounter();
		encounter.setEncounterDatetime(date);
		encounter.setEncounterType(ensureEncounterType(typeName));
		encounter.setPatient(patient);
		encounter.setLocation(location);
		if (provider != null) {
			encounter.addProvider(getClinicianRole(), provider);
		}
		return encounter;
	}

	public EncounterType ensureEncounterType(String name) {
		EncounterType cached = encounterTypeCache.get(name);
		if (cached != null) {
			return cached;
		}
		EncounterService es = Context.getEncounterService();
		EncounterType t = es.getEncounterType(name);
		if (t == null) {
			t = new EncounterType(name, "");
			t.setDateCreated(new Date());
			t = es.saveEncounterType(t);
		}
		encounterTypeCache.put(name, t);
		return t;
	}

	public EncounterRole getClinicianRole() {
		if (clinicianRole == null) {
			clinicianRole = Context.getEncounterService().getEncounterRoleByName("Clinician");
			if (clinicianRole == null) {
				throw new APIException("Required EncounterRole 'Clinician' is missing from seed data");
			}
		}
		return clinicianRole;
	}

	public Form getVisitNoteForm() {
		if (visitNoteForm == null) {
			FormService fs = Context.getFormService();
			visitNoteForm = fs.getForm("Visit Note");
			if (visitNoteForm == null) {
				visitNoteForm = new Form();
				visitNoteForm.setName("Visit Note");
				visitNoteForm.setVersion("1.0");
				visitNoteForm = fs.saveForm(visitNoteForm);
			}
		}
		return visitNoteForm;
	}
}
