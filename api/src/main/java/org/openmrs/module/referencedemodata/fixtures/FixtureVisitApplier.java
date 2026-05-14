/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.fixtures;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.module.referencedemodata.Randomizer;
import org.openmrs.module.referencedemodata.ReferenceDemoDataConstants;
import org.openmrs.module.referencedemodata.diagnosis.DemoDiagnosisGenerator;
import org.openmrs.module.referencedemodata.obs.DemoObsGenerator;
import org.openmrs.module.referencedemodata.orders.DemoOrderGenerator;
import org.openmrs.module.referencedemodata.orders.DrugOrderDescriptor;
import org.openmrs.module.referencedemodata.providers.DemoProviderGenerator;
import org.openmrs.module.referencedemodata.visit.EncounterFactory;

@Slf4j
class FixtureVisitApplier {

	static final String ENC_VISIT_NOTE = "Visit Note";

	static final String ENC_PROCEDURE_NOTE = "Procedure Note";

	static final String ENC_DISCHARGE_SUMMARY = "Discharge Summary";

	static final String ENC_ADMISSION = "Admission";

	static final String ENC_VITALS = "Vitals";

	private final DemoObsGenerator obsGenerator;
	private final DemoOrderGenerator orderGenerator;
	private final DemoDiagnosisGenerator diagnosisGenerator;
	private final DemoProviderGenerator providerGenerator;
	private final EncounterFactory encounterFactory = new EncounterFactory();

	private Map<String, VisitType> visitTypeCache;
	private final Map<String, Location> locationCache = new HashMap<>();

	FixtureVisitApplier(DemoObsGenerator obsGenerator, DemoOrderGenerator orderGenerator,
			DemoDiagnosisGenerator diagnosisGenerator, DemoProviderGenerator providerGenerator) {
		this.obsGenerator = obsGenerator;
		this.orderGenerator = orderGenerator;
		this.diagnosisGenerator = diagnosisGenerator;
		this.providerGenerator = providerGenerator;
	}

	void apply(Patient patient, List<ResolvedVisit> visits) {
		EncounterService encounterService = Context.getEncounterService();
		for (ResolvedVisit rv : visits) {
			VisitType visitType = lookupVisitType(rv.typeName);
			Location location = resolveLocation(rv.locationName);

			Visit visit = new Visit(patient, visitType, rv.startDate);
			visit.setStopDatetime(rv.stopDate);
			visit.setLocation(location);

			for (ResolvedEncounter re : rv.encounters) {
				Encounter encounter = encounterFactory.createEncounter(re.typeName, patient, re.date, location,
						resolveProvider(re.providerRole));
				if (ENC_VISIT_NOTE.equals(re.typeName)) {
					encounter.setForm(encounterFactory.getVisitNoteForm());
				}
				visit.addEncounter(encounter);
				encounterService.saveEncounter(encounter);
				applyEncounterPayload(patient, encounter, re, location);
			}

			Context.getVisitService().saveVisit(visit);
		}
	}

	private VisitType lookupVisitType(String name) {
		if (visitTypeCache == null) {
			visitTypeCache = new HashMap<>();
			for (VisitType vt : Context.getVisitService().getAllVisitTypes()) {
				visitTypeCache.put(vt.getName().toLowerCase(), vt);
			}
		}
		VisitType match = visitTypeCache.get(name.toLowerCase());
		if (match != null) return match;
		VisitType fallback = visitTypeCache.values().stream()
				.filter(vt -> !Boolean.TRUE.equals(vt.getRetired()))
				.sorted(Comparator.comparing(VisitType::getId))
				.findFirst().orElse(null);
		if (fallback == null) {
			throw new APIException("No unretired VisitType rows exist; cannot fall back for fixture visit type '"
					+ name + "'");
		}
		log.warn("Fixture visit type '{}' not found; substituting '{}'. " +
				"Add this VisitType to seed data to make fixtures reproducible.",
				name, fallback.getName());
		return fallback;
	}

	private Location resolveLocation(String name) {
		String key = name != null ? name : "__default__";
		Location cached = locationCache.get(key);
		if (cached != null) return cached;

		Location resolved = lookupLocation(name);
		if (resolved != null) {
			locationCache.put(key, resolved);
		}
		return resolved;
	}

	private Location lookupLocation(String name) {
		if (name != null) {
			Location loc = Context.getLocationService().getLocation(name);
			if (loc != null) return loc;
			log.warn("Fixture location '{}' not found in DB; falling back to default", name);
		}
		Location fallback = Context.getLocationService().getLocation("Outpatient Clinic");
		if (fallback != null) {
			if (name != null && !"Outpatient Clinic".equals(name)) {
				log.info("Substituting 'Outpatient Clinic' for fixture location '{}'", name);
			}
			return fallback;
		}
		List<Location> roots = Context.getLocationService().getRootLocations(false);
		if (roots.isEmpty()) {
			log.error("No root locations exist; fixture visit will have null location");
			return null;
		}
		return Randomizer.randomListEntry(roots);
	}

	private Provider resolveProvider(String role) {
		boolean wantNurse = "nurse".equalsIgnoreCase(role);
		Provider provider = wantNurse ? providerGenerator.getNurse() : providerGenerator.getDoctor();
		if (provider == null) {
			throw new APIException("Required Provider with identifier '" + (wantNurse ? "nurse" : "doctor")
					+ "' is missing; ensure ReferenceDemoDataActivator seeded providers before fixture load");
		}
		return provider;
	}

	private void applyEncounterPayload(Patient patient, Encounter encounter, ResolvedEncounter re,
			Location location) {
		if (re.vitals != null) {
			applyVitals(patient, encounter, re.vitals, location);
		}
		if (re.bmi != null) {
			applyBmi(patient, encounter, re.bmi, location);
		}
		if (!re.labs.isEmpty()) {
			applyLabs(patient, encounter, re.labs, location);
		}
		if (!re.drugOrders.isEmpty()) {
			applyDrugOrders(encounter, re.drugOrders);
		}
		if (re.noteText != null) {
			applyNote(patient, encounter, re.noteText, location);
		}
		if (!re.diagnoses.isEmpty()) {
			applyDiagnoses(patient, encounter, re.diagnoses);
		}
	}

	private void applyVitals(Patient patient, Encounter encounter, ResolvedVitals v, Location location) {
		emitNumericObs(patient, encounter, location, ReferenceDemoDataConstants.VITAL_SYSTOLIC_BP_UUID, v.systolicBp);
		emitNumericObs(patient, encounter, location, ReferenceDemoDataConstants.VITAL_DIASTOLIC_BP_UUID, v.diastolicBp);
		emitNumericObs(patient, encounter, location, ReferenceDemoDataConstants.VITAL_HEART_RATE_UUID, v.heartRate);
		emitNumericObs(patient, encounter, location, ReferenceDemoDataConstants.VITAL_TEMPERATURE_UUID, v.temperatureC);
		emitNumericObs(patient, encounter, location, ReferenceDemoDataConstants.VITAL_RESPIRATORY_RATE_UUID, v.respiratoryRate);
		emitNumericObs(patient, encounter, location, ReferenceDemoDataConstants.VITAL_OXYGEN_SATURATION_UUID, v.oxygenSaturation);
	}

	private void applyBmi(Patient patient, Encounter encounter, ResolvedBmi b, Location location) {
		emitNumericObs(patient, encounter, location, ReferenceDemoDataConstants.VITAL_WEIGHT_KG_UUID, b.weightKg);
		emitNumericObs(patient, encounter, location, ReferenceDemoDataConstants.VITAL_HEIGHT_CM_UUID, b.heightCm);
	}

	private void applyLabs(Patient patient, Encounter encounter, List<ResolvedNumericObs> labs, Location location) {
		for (ResolvedNumericObs lab : labs) {
			emitNumericObs(patient, encounter, location, lab.conceptUuid, lab.value);
		}
	}

	private void emitNumericObs(Patient patient, Encounter encounter, Location location,
			String conceptRef, Number value) {
		if (value == null) return;
		obsGenerator.createNumericObs(conceptRef, value, patient, encounter,
				encounter.getEncounterDatetime(), location);
	}

	private void applyDrugOrders(Encounter encounter, List<DrugOrderDescriptor> orders) {
		for (DrugOrderDescriptor spec : orders) {
			orderGenerator.createDatedDrugOrder(encounter, spec);
		}
	}

	private void applyNote(Patient patient, Encounter encounter, String text, Location location) {
		obsGenerator.createTextObs(ReferenceDemoDataConstants.VISIT_NOTE_TEXT_CONCEPT_REF, text, patient, encounter,
				encounter.getEncounterDatetime(), location);
	}

	private void applyDiagnoses(Patient patient, Encounter encounter, List<ResolvedDiagnosis> diagnoses) {
		for (ResolvedDiagnosis d : diagnoses) {
			diagnosisGenerator.createDiagnosis(d.primary, patient, encounter, d.concept);
		}
	}
}
