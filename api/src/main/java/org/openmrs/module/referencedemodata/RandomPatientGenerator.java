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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptNumeric;
import org.openmrs.Encounter;
import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.FormService;
import org.openmrs.api.ObsService;
import org.openmrs.api.PatientService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.util.OpenmrsConstants;

/**
 * Random patient generation extracted from {@link ReferenceDemoDataActivator}.
 *
 * <p>All sources of non-determinism (random numbers, clock reads) are routed
 * through the supplied {@link DemoDataContext}, so the same (seed, clockAnchor)
 * produces byte-identical output.
 */
public class RandomPatientGenerator {

    private final Log log = LogFactory.getLog(getClass());

    private static final int ADMISSION_DAYS_MIN = 1;
    private static final int ADMISSION_DAYS_MAX = 3;
    private static final int MIN_AGE = 2;
    private static final int MAX_AGE = 90;

    // Stable key extractors for ctx.pick — UUIDs are assigned by the MDS metadata and
    // are stable across runs regardless of locale / session state / DB insertion order.
    private static final java.util.function.Function<VisitType, String> VISIT_TYPE_UUID =
            new java.util.function.Function<VisitType, String>() {
                @Override public String apply(VisitType v) { return v.getUuid(); }
            };
    private static final java.util.function.Function<Concept, String> CONCEPT_UUID =
            new java.util.function.Function<Concept, String>() {
                @Override public String apply(Concept c) { return c.getUuid(); }
            };

    private final Map<String, Concept> cachedConcepts = new WeakHashMap<String, Concept>();

    /**
     * Creates {@code count} random patients, threading {@code ctx} through all
     * random/time calls.
     */
    public void generate(DemoDataContext ctx, int count) {
        if (count <= 0) {
            return;
        }

        createVitalsForm();

        PatientService ps = Context.getPatientService();
        Location rootLocation = ctx.pick(Context.getLocationService().getRootLocations(false), Location::getUuid);
        PatientIdentifierType patientIdentifierType = DemoIdentifiers.type();

        for (int i = 0; i < count; i++) {
            Patient patient = createDemoPatient(ctx, ps, patientIdentifierType, rootLocation);
            log.info("created demo patient: " + patient.getPatientIdentifier()
                    + " " + patient.getGivenName() + " " + patient.getFamilyName());
            Context.flushSession();
            Context.clearSession();
        }
    }

    private void createVitalsForm() {
        FormService fs = Context.getFormService();
        if (fs.getFormByUuid(ReferenceDemoDataConstants.VITALS_FORM_UUID) != null) {
            return;
        }
        Form form = new Form();
        form.setUuid(ReferenceDemoDataConstants.VITALS_FORM_UUID);
        form.setName(ReferenceDemoDataConstants.VITALS_FORM_NAME);
        form.setEncounterType(Context.getEncounterService().getEncounterTypeByUuid(
                ReferenceDemoDataConstants.VITALS_FORM_ENCOUNTERTYPE_UUID));
        form.setVersion("1.0");
        fs.saveForm(form);
    }

    private Patient createDemoPatient(DemoDataContext ctx, PatientService ps,
                                      PatientIdentifierType patientIdentifierType, Location location) {
        Patient patient = createBasicDemoPatient(ctx, patientIdentifierType, location);
        patient = ps.savePatient(patient);
        VisitService vs = Context.getVisitService();
        int visitCount = randomBetween(ctx, 0, 10);
        for (int i = 0; i < visitCount; i++) {
            boolean shortVisit = i < (visitCount * 0.75);
            Visit visit = createDemoVisit(ctx, patient, vs.getAllVisitTypes(), location, shortVisit);
            vs.saveVisit(visit);
        }
        return patient;
    }

    private Patient createBasicDemoPatient(DemoDataContext ctx, PatientIdentifierType patientIdentifierType,
                                           Location location) {
        Patient patient = new Patient();

        PersonName pName = new PersonName();
        String gender = randomArrayEntry(ctx, GENDERS);
        boolean male = gender.equals("M");
        pName.setGivenName(randomArrayEntry(ctx, male ? MALE_FIRST_NAMES : FEMALE_FIRST_NAMES));
        pName.setFamilyName(randomArrayEntry(ctx, FAMILY_NAMES));
        patient.addName(pName);

        PersonAddress pAddress = new PersonAddress();
        String randomSuffix = randomSuffix(ctx);
        pAddress.setAddress1("Address1" + randomSuffix);
        pAddress.setCityVillage("City" + randomSuffix);
        pAddress.setStateProvince("State" + randomSuffix);
        pAddress.setCountry("Country" + randomSuffix);
        pAddress.setPostalCode(randomSuffix(ctx, 5));
        patient.addAddress(pAddress);

        patient.setBirthdate(randomBirthdate(ctx));
        patient.setBirthdateEstimated(false);
        patient.setGender(gender);

        PatientIdentifier pa1 = new PatientIdentifier();
        pa1.setIdentifier(DemoIdentifiers.next());
        pa1.setIdentifierType(patientIdentifierType);
        pa1.setDateCreated(Date.from(ctx.clockAnchor()));
        pa1.setLocation(location);
        patient.addIdentifier(pa1);

        return patient;
    }

    private Visit createDemoVisit(DemoDataContext ctx, Patient patient, List<VisitType> visitTypes,
                                  Location location, boolean shortVisit) {
        LocalDateTime anchor = LocalDateTime.ofInstant(ctx.clockAnchor(), ZoneId.of("UTC"));
        LocalDateTime visitStart = anchor.minusDays(randomBetween(ctx, 0, 365 * 2)).minusHours(3); // past 2 years
        if (!shortVisit) {
            visitStart = visitStart.minusDays(ADMISSION_DAYS_MAX + 1);
        }
        // ctx.pick sorts by UUID before picking — DB list order varies with session state,
        // which would otherwise desynchronise the seeded RNG.
        Visit visit = new Visit(patient, ctx.pick(visitTypes, VISIT_TYPE_UUID), toDate(visitStart));
        visit.setLocation(location);
        LocalDateTime vitalsTime = visitStart.plusMinutes(randomBetween(ctx, 1, 60));
        visit.addEncounter(createDemoVitalsEncounter(ctx, patient, toDate(vitalsTime)));
        LocalDateTime visitNoteTime = visitStart.plusMinutes(randomBetween(ctx, 60, 120));
        visit.addEncounter(createVisitNote(ctx, patient, toDate(visitNoteTime), location));
        if (shortVisit) {
            LocalDateTime visitEndTime = visitNoteTime.plusMinutes(30);
            visit.setStopDatetime(toDate(visitEndTime));
        } else {
            Location admitLocation = Context.getLocationService().getLocation("Inpatient Ward");
            visit.addEncounter(createEncounter("Admission", patient, toDate(visitNoteTime), admitLocation));
            LocalDateTime dischargeDateTime = visitNoteTime.plusDays(
                    randomBetween(ctx, ADMISSION_DAYS_MIN, ADMISSION_DAYS_MAX));
            visit.addEncounter(createEncounter("Discharge", patient, toDate(dischargeDateTime), admitLocation));
            visit.setStopDatetime(toDate(dischargeDateTime));
        }
        return visit;
    }

    private static Date toDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.of("UTC")).toInstant());
    }

    private Encounter createVisitNote(DemoDataContext ctx, Patient patient, Date encounterTime, Location location) {
        ObsService os = Context.getObsService();
        ConceptService cs = Context.getConceptService();
        Encounter visitNote = createEncounter("Visit Note", patient, encounterTime, location);
        visitNote.setForm(Context.getFormService().getForm("Visit Note"));
        Context.getEncounterService().saveEncounter(visitNote);

        createTextObs("Text of encounter note"/*CIEL:162169*/, randomArrayEntry(ctx, RANDOM_TEXT),
                patient, visitNote, encounterTime, location, os, cs);

        createDiagnosisObsGroup(ctx, true, patient, visitNote, encounterTime, location, os, cs);

        if (flipACoin(ctx)) {
            createDiagnosisObsGroup(ctx, false, patient, visitNote, encounterTime, location, os, cs);
        }

        return visitNote;
    }

    private void createDiagnosisObsGroup(DemoDataContext ctx, boolean primary, Patient patient, Encounter visitNote,
                                         Date encounterTime, Location location, ObsService os, ConceptService cs) {
        Obs obsGroup = createBasicObs("Visit Diagnoses", patient, encounterTime, location, cs);
        visitNote.addObs(obsGroup);

        boolean valueBefore = Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive();
        Context.getAdministrationService().setGlobalProperty(
                OpenmrsConstants.GP_CASE_SENSITIVE_DATABASE_STRING_COMPARISON, "true");

        String certainty = flipACoin(ctx) ? "Presumed diagnosis" : "Confirmed diagnosis";
        Obs obs1 = createCodedObs(EmrApiConstants.CONCEPT_CODE_DIAGNOSIS_CERTAINTY, certainty,
                patient, visitNote, encounterTime, location, os, cs);

        // TODO 5% of diagnoses should be non-coded.
        // ctx.pick sorts by UUID so DB-returned list order (which is locale- and session-
        // sensitive via getConceptsByClass) can't drift the seeded RNG between runs.
        List<Concept> allDiagnoses = cs.getConceptsByClass(cs.getConceptClassByName("Diagnosis"));
        Obs obs2 = createCodedObs("DIAGNOSIS LIST", ctx.pick(allDiagnoses, CONCEPT_UUID),
                patient, visitNote, encounterTime, location, os, cs);

        String order = primary
                ? EmrApiConstants.CONCEPT_CODE_DIAGNOSIS_ORDER_PRIMARY
                : EmrApiConstants.CONCEPT_CODE_DIAGNOSIS_ORDER_SECONDARY;
        Obs obs3 = createCodedObs(EmrApiConstants.CONCEPT_CODE_DIAGNOSIS_ORDER, order,
                patient, visitNote, encounterTime, location, os, cs);

        obsGroup.addGroupMember(obs1);
        obsGroup.addGroupMember(obs2);
        obsGroup.addGroupMember(obs3);
        os.saveObs(obsGroup, "testing");

        Context.getAdministrationService().setGlobalProperty(
                OpenmrsConstants.GP_CASE_SENSITIVE_DATABASE_STRING_COMPARISON, String.valueOf(valueBefore));
    }

    private Encounter createDemoVitalsEncounter(DemoDataContext ctx, Patient patient, Date encounterTime) {
        Location location = Context.getLocationService().getLocation("Outpatient Clinic");
        Encounter encounter = createEncounter("Vitals", patient, encounterTime, location);
        encounter.setForm(Context.getFormService().getForm("Vitals"));
        createDemoVitalsObs(ctx, patient, encounter, encounterTime, location);
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

    private void createDemoVitalsObs(DemoDataContext ctx, Patient patient, Encounter encounter,
                                     Date encounterTime, Location location) {
        ObsService os = Context.getObsService();
        ConceptService cs = Context.getConceptService();
        createNumericObs(ctx, "Height (cm)", 10, 228, patient, encounter, encounterTime, location, os, cs);
        createNumericObs(ctx, "Weight (kg)", 1, 250, patient, encounter, encounterTime, location, os, cs);
        createNumericObs(ctx, "Temperature (C)", 25, 43, patient, encounter, encounterTime, location, os, cs);
        createNumericObs(ctx, "Pulse", 0, 230, patient, encounter, encounterTime, location, os, cs);
        createNumericObs(ctx, "Respiratory rate", 5, 100, patient, encounter, encounterTime, location, os, cs);
        createNumericObs(ctx, "Systolic blood pressure", 0, 250, patient, encounter, encounterTime, location, os, cs);
        createNumericObs(ctx, "Diastolic blood pressure", 0, 150, patient, encounter, encounterTime, location, os, cs);
        createNumericObs(ctx, "Blood oxygen saturation", 0, 100, patient, encounter, encounterTime, location, os, cs);
    }

    private void createNumericObs(DemoDataContext ctx, String conceptName, int min, int max, Patient patient,
                                  Encounter encounter, Date encounterTime, Location location,
                                  ObsService os, ConceptService cs) {
        Obs obs = createBasicObs(conceptName, patient, encounterTime, location, cs);

        Concept concept = obs.getConcept();
        if (concept != null) {
            ConceptNumeric conceptNumeric = cs.getConceptNumeric(concept.getConceptId());
            if (conceptNumeric.getHiAbsolute() != null) {
                max = conceptNumeric.getHiAbsolute().intValue();
            }
            if (conceptNumeric.getLowAbsolute() != null) {
                min = conceptNumeric.getLowAbsolute().intValue();
            }
        }

        obs.setValueNumeric((double) randomBetween(ctx, min, max));
        encounter.addObs(obs);
        os.saveObs(obs, null);
    }

    private void createTextObs(String conceptName, String text, Patient patient, Encounter encounter,
                               Date encounterTime, Location location, ObsService os, ConceptService cs) {
        Obs obs = createBasicObs(conceptName, patient, encounterTime, location, cs);
        obs.setValueText(text);
        encounter.addObs(obs);
        os.saveObs(obs, null);
    }

    private Obs createCodedObs(String conceptName, String codedConceptName, Patient patient, Encounter encounter,
                               Date encounterTime, Location location, ObsService os, ConceptService cs) {
        return createCodedObs(conceptName, findConcept(codedConceptName, cs),
                patient, encounter, encounterTime, location, os, cs);
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
        encounter.addObs(obs);
        os.saveObs(obs, null);
        return obs;
    }

    private Obs createBasicObs(String conceptName, Patient patient, Date encounterTime, Location location,
                               ConceptService cs) {
        Concept concept = findConcept(conceptName, cs);
        if (concept == null) {
            log.warn("incorrect concept name? " + conceptName);
        }
        return new Obs(patient, concept, encounterTime, location);
    }

    private Date randomBirthdate(DemoDataContext ctx) {
        LocalDate now = ctx.clockAnchor().atZone(ZoneId.of("UTC")).toLocalDate();
        int year = randomBetween(ctx, now.getYear() - MAX_AGE, now.getYear() - MIN_AGE);
        int month = randomBetween(ctx, 1, 12);
        int day = randomBetween(ctx, 1, 28);
        LocalDate birth = LocalDate.of(year, month, day);
        return Date.from(birth.atStartOfDay(ZoneId.of("UTC")).toInstant());
    }

    // ---- Random helpers routed through DemoDataContext ----

    private static int randomBetween(DemoDataContext ctx, int min, int max) {
        return min + (int) (ctx.random().nextDouble() * (max - min + 1));
    }

    private static int randomArrayIndex(DemoDataContext ctx, int length) {
        return (int) (ctx.random().nextDouble() * length);
    }

    private static String randomArrayEntry(DemoDataContext ctx, String[] array) {
        return array[randomArrayIndex(ctx, array.length)];
    }

    private static <T> T randomArrayEntry(DemoDataContext ctx, List<T> list) {
        return list.get(randomArrayIndex(ctx, list.size()));
    }

    /**
     * Deterministic replacement for the prior "last digits of currentTimeMillis"
     * suffix — uses ctx.random() to avoid wall-clock reads.
     */
    private static String randomSuffix(DemoDataContext ctx) {
        return randomSuffix(ctx, 4);
    }

    private static String randomSuffix(DemoDataContext ctx, int digits) {
        int bound = (int) Math.pow(10, digits);
        int n = ctx.random().nextInt(bound);
        String format = "%0" + digits + "d";
        return String.format(format, n);
    }

    private static boolean flipACoin(DemoDataContext ctx) {
        return randomBetween(ctx, 0, 1) == 0;
    }

    // ---- Static name/text tables (order-stable, so deterministic) ----

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
            "mollit anim id est laborum." };
}
