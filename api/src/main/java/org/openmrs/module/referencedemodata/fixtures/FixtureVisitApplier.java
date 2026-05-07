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

import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.Encounter;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.api.FormService;
import org.openmrs.api.context.Context;
import org.openmrs.module.referencedemodata.Randomizer;
import org.openmrs.module.referencedemodata.diagnosis.DemoDiagnosisGenerator;
import org.openmrs.module.referencedemodata.obs.DemoObsGenerator;
import org.openmrs.module.referencedemodata.orders.DemoOrderGenerator;
import org.openmrs.module.referencedemodata.orders.DrugOrderDescriptor;
import org.openmrs.module.referencedemodata.providers.DemoProviderGenerator;

@Slf4j
class FixtureVisitApplier {

    private static final String VISIT_NOTE_TEXT_CONCEPT_UUID = "CIEL:162169";

    private final DemoObsGenerator obsGenerator;
    private final DemoOrderGenerator orderGenerator;
    private final DemoDiagnosisGenerator diagnosisGenerator;
    private final DemoProviderGenerator providerGenerator;

    private EncounterRole clinicianRole;
    private Form visitNoteForm;

    FixtureVisitApplier(DemoObsGenerator obsGenerator, DemoOrderGenerator orderGenerator,
            DemoDiagnosisGenerator diagnosisGenerator, DemoProviderGenerator providerGenerator) {
        this.obsGenerator = obsGenerator;
        this.orderGenerator = orderGenerator;
        this.diagnosisGenerator = diagnosisGenerator;
        this.providerGenerator = providerGenerator;
    }

    void apply(Patient patient, List<ResolvedVisit> visits) {
        for (ResolvedVisit rv : visits) {
            VisitType visitType = lookupVisitType(rv.typeName);
            Location location = resolveLocation(rv.locationName);

            Visit visit = new Visit(patient, visitType, rv.startDate);
            visit.setStopDatetime(rv.stopDate);
            visit.setLocation(location);

            for (ResolvedEncounter re : rv.encounters) {
                Encounter encounter = createEncounter(re.typeName, patient, re.date, location,
                        resolveProvider(re.providerRole));
                if ("Visit Note".equals(re.typeName)) {
                    encounter.setForm(getVisitNoteForm());
                }
                Context.getEncounterService().saveEncounter(encounter);
                visit.addEncounter(encounter);
                applyEncounterPayload(patient, encounter, re, location);
            }

            Context.getVisitService().saveVisit(visit);
        }
    }

    private VisitType lookupVisitType(String name) {
        for (VisitType vt : Context.getVisitService().getAllVisitTypes()) {
            if (vt.getName().equalsIgnoreCase(name)) return vt;
        }
        throw new APIException("Fixture references unknown visit type: " + name
                + ". Visit types are platform-managed; ensure standard types exist.");
    }

    private Location resolveLocation(String name) {
        if (name != null) {
            Location loc = Context.getLocationService().getLocation(name);
            if (loc != null) return loc;
            log.warn("Fixture location '{}' not found in DB; falling back to default", name);
        }
        Location fallback = Context.getLocationService().getLocation("Outpatient Clinic");
        if (fallback != null) return fallback;
        List<Location> roots = Context.getLocationService().getRootLocations(false);
        return roots.isEmpty() ? null : Randomizer.randomListEntry(roots);
    }

    private Encounter createEncounter(String typeName, Patient patient, Date date, Location location,
            Provider provider) {
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

    private EncounterType ensureEncounterType(String name) {
        EncounterService es = Context.getEncounterService();
        EncounterType t = es.getEncounterType(name);
        if (t == null) {
            t = new EncounterType(name, "");
            t.setDateCreated(new Date());
            t = es.saveEncounterType(t);
        }
        return t;
    }

    private EncounterRole getClinicianRole() {
        if (clinicianRole == null) {
            clinicianRole = Context.getEncounterService().getEncounterRoleByName("Clinician");
        }
        return clinicianRole;
    }

    private Form getVisitNoteForm() {
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

    private Provider resolveProvider(String role) {
        if ("nurse".equalsIgnoreCase(role)) {
            return providerGenerator.getNurse();
        }
        return providerGenerator.getDoctor();
    }

    private void applyEncounterPayload(Patient patient, Encounter encounter, ResolvedEncounter re,
            Location location) {
        if (re.vitals != null)     applyVitals(patient, encounter, re.vitals, location);
        if (re.bmi != null)        applyBmi(patient, encounter, re.bmi, location);
        if (re.labs != null)       applyLabs(patient, encounter, re.labs, location);
        if (re.drugOrders != null) applyDrugOrders(encounter, re.drugOrders);
        if (re.noteText != null)   applyNote(patient, encounter, re.noteText, location);
        if (re.diagnoses != null)  applyDiagnoses(patient, encounter, re.diagnoses);
    }

    private void applyVitals(Patient patient, Encounter encounter, ResolvedVitals v, Location location) {
        Date date = encounter.getEncounterDatetime();
        if (v.systolicBp != null)
            obsGenerator.createNumericObs("5085AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", v.systolicBp, patient, encounter, date, location);
        if (v.diastolicBp != null)
            obsGenerator.createNumericObs("5086AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", v.diastolicBp, patient, encounter, date, location);
        if (v.heartRate != null)
            obsGenerator.createNumericObs("5087AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", v.heartRate, patient, encounter, date, location);
        if (v.temperatureC != null)
            obsGenerator.createNumericObs("5088AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", v.temperatureC, patient, encounter, date, location);
        if (v.respiratoryRate != null)
            obsGenerator.createNumericObs("5242AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", v.respiratoryRate, patient, encounter, date, location);
        if (v.oxygenSaturation != null)
            obsGenerator.createNumericObs("5092AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", v.oxygenSaturation, patient, encounter, date, location);
    }

    private void applyBmi(Patient patient, Encounter encounter, ResolvedBmi b, Location location) {
        Date date = encounter.getEncounterDatetime();
        if (b.weightKg != null)
            obsGenerator.createNumericObs("5089AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", b.weightKg, patient, encounter, date, location);
        if (b.heightCm != null)
            obsGenerator.createNumericObs("5090AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", b.heightCm, patient, encounter, date, location);
    }

    private void applyLabs(Patient patient, Encounter encounter, List<ResolvedNumericObs> labs, Location location) {
        Date date = encounter.getEncounterDatetime();
        for (ResolvedNumericObs lab : labs) {
            obsGenerator.createNumericObs(lab.conceptUuid, lab.value, patient, encounter, date, location);
        }
    }

    private void applyDrugOrders(Encounter encounter, List<DrugOrderDescriptor> orders) {
        for (DrugOrderDescriptor spec : orders) {
            orderGenerator.createDatedDrugOrder(encounter, spec);
        }
    }

    private void applyNote(Patient patient, Encounter encounter, String text, Location location) {
        obsGenerator.createTextObs(VISIT_NOTE_TEXT_CONCEPT_UUID, text, patient, encounter,
                encounter.getEncounterDatetime(), location);
    }

    private void applyDiagnoses(Patient patient, Encounter encounter, List<ResolvedDiagnosis> diagnoses) {
        for (ResolvedDiagnosis d : diagnoses) {
            diagnosisGenerator.createDiagnosis(d.primary, patient, encounter, d.concept);
        }
    }
}
