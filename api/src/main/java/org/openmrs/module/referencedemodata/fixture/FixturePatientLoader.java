package org.openmrs.module.referencedemodata.fixture;

import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.SimpleDosingInstructions;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.context.Context;
import org.openmrs.module.referencedemodata.DemoDataContext;
import org.openmrs.module.referencedemodata.DemoIdentifiers;
import org.openmrs.module.referencedemodata.DemoProvider;
import org.openmrs.module.referencedemodata.ReferenceDemoDataConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class FixturePatientLoader {

    private static final Logger log = LoggerFactory.getLogger(FixturePatientLoader.class);

    private final ConceptAliases aliases;

    public FixturePatientLoader(ConceptAliases aliases) { this.aliases = aliases; }

    /** Loads every fixture listed in fixtures/index.txt. */
    public int loadAll(DemoDataContext ctx) {
        int total = 0;
        for (String path : FixtureDiscovery.listFixtureResources()) {
            total += loadOne(path, ctx);
        }
        return total;
    }

    /**
     * Loads a single fixture, persisting the Patient row and then best-effort-attaching visits,
     * conditions, orders, and labs.
     *
     * @return 1 once the Patient is persisted (regardless of whether downstream clinical data
     *         attached successfully); 0 if the patient UUID already exists.
     */
    public int loadOne(String classpathPath, DemoDataContext ctx) {
        FixturePatient fx = read(classpathPath);
        if (Context.getPatientService().getPatientByUuid(fx.uuid) != null) {
            log.info("Skipping fixture " + classpathPath + " (patient UUID " + fx.uuid + " already exists)");
            return 0;
        }
        Location location = getDefaultLocation();
        PatientIdentifierType idType = DemoIdentifiers.type();

        Patient p = new Patient();
        p.setUuid(fx.uuid);
        p.setGender(fx.gender);
        p.setBirthdate(Date.from(RelativeTime.resolve(fx.birthdate, ctx.clockAnchor())));
        PersonName name = new PersonName(fx.givenName, null, fx.familyName);
        p.addName(name);
        if (fx.address != null) {
            PersonAddress a = new PersonAddress();
            a.setCityVillage(fx.address.city);
            a.setStateProvince(fx.address.state);
            a.setCountry(fx.address.country);
            a.setPostalCode(fx.address.postalCode);
            p.addAddress(a);
        }
        PatientIdentifier pid = new PatientIdentifier(
                fx.identifier != null ? fx.identifier : DemoIdentifiers.next(),
                idType, location);
        pid.setPreferred(true);
        p.addIdentifier(pid);
        Context.getPatientService().savePatient(p);

        // Catch IllegalStateException specifically: that's what our metadata-lookup helpers
        // (pickVisitType, lookupConceptByName, firstDrugForConcept, ConceptAliases.resolve, etc.)
        // throw when the dictionary is missing a referenced concept/drug/visit-type. OpenMRS
        // core APIs throw APIException/HibernateException for genuine runtime bugs — those
        // propagate so structural problems don't ship silently.
        try {
            Provider orderer = DemoProvider.ensure();
            for (FixturePatient.Visit v : fx.visits) {
                createVisit(p, v, ctx, location, orderer);
            }
            for (FixturePatient.Condition c : fx.conditions) {
                createCondition(p, c, ctx, location);
            }
            for (FixturePatient.Order o : fx.orders) {
                createOrder(p, o, ctx, orderer);
            }
            createLabs(p, fx, ctx, location);
        } catch (IllegalStateException e) {
            log.warn("Fixture " + classpathPath + " patient saved but clinical data incomplete: "
                    + e.getMessage(), e);
        }
        return 1;
    }

    private FixturePatient read(String classpathPath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpathPath)) {
            if (in == null) throw new IllegalStateException("missing " + classpathPath);
            Yaml y = new Yaml(new Constructor(FixturePatient.class, new LoaderOptions()));
            try {
                return y.load(in);
            } catch (org.yaml.snakeyaml.error.YAMLException e) {
                throw new IllegalStateException("failed to parse fixture " + classpathPath + ": " + e.getMessage(), e);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to read " + classpathPath, e);
        }
    }

    private Location getDefaultLocation() {
        Location loc = Context.getLocationService().getDefaultLocation();
        if (loc != null) return loc;
        List<Location> all = Context.getLocationService().getAllLocations();
        if (all == null || all.isEmpty()) {
            throw new IllegalStateException("no default location and getAllLocations() is empty");
        }
        Location fallback = all.iterator().next();
        log.warn("No default location configured; attaching demo data to first available location: "
                + fallback.getName());
        return fallback;
    }

    private void createVisit(Patient p, FixturePatient.Visit vf, DemoDataContext ctx, Location loc, Provider orderer) {
        Instant when = RelativeTime.resolve(vf.date, ctx.clockAnchor());
        Visit v = new Visit();
        v.setUuid(vf.uuid);
        v.setPatient(p);
        v.setLocation(loc);
        v.setStartDatetime(Date.from(when));
        v.setVisitType(pickVisitType(vf.type));
        // Save the visit before encounters so each encounter references a persistent Visit.
        Context.getVisitService().saveVisit(v);
        for (FixturePatient.Encounter ef : vf.encounters) {
            createEncounter(v, ef, ctx, when, loc, orderer);
        }
        v.setStopDatetime(Date.from(when.plusSeconds(3600)));
        Context.getVisitService().saveVisit(v);
    }

    private VisitType pickVisitType(String kind) {
        List<VisitType> all = Context.getVisitService().getAllVisitTypes();
        String wanted = "inpatient".equalsIgnoreCase(kind) ? "Inpatient" : "Outpatient";
        for (VisitType vt : all) if (vt.getName().equalsIgnoreCase(wanted)) return vt;
        throw new IllegalStateException("no VisitType named '" + wanted + "' (requested kind=" + kind + ")");
    }

    private void createEncounter(Visit v, FixturePatient.Encounter ef, DemoDataContext ctx, Instant when, Location loc, Provider orderer) {
        EncounterType type = Context.getEncounterService().getEncounterTypeByUuid(resolveEncounterTypeUuid(ef.type));
        Encounter e = new Encounter();
        e.setUuid(ef.uuid);
        e.setEncounterType(type);
        e.setPatient(v.getPatient());
        e.setEncounterDatetime(Date.from(when));
        e.setLocation(loc);
        e.setVisit(v);
        Context.getEncounterService().saveEncounter(e);

        for (FixturePatient.Obs of : ef.obs) {
            Obs obs = new Obs();
            obs.setUuid(of.uuid);
            obs.setPerson(v.getPatient());
            obs.setObsDatetime(e.getEncounterDatetime());
            obs.setEncounter(e);
            obs.setConcept(aliases.resolve(of.concept));
            obs.setValueNumeric(of.value);
            Context.getObsService().saveObs(obs, null);
        }
        if (ef.noteText != null && !ef.noteText.isEmpty()) {
            Obs textObs = new Obs();
            textObs.setPerson(v.getPatient());
            textObs.setObsDatetime(e.getEncounterDatetime());
            textObs.setEncounter(e);
            textObs.setConcept(aliases.resolve("TEXT_OF_ENCOUNTER"));
            textObs.setValueText(ef.noteText);
            Context.getObsService().saveObs(textObs, null);
        }
    }

    private String resolveEncounterTypeUuid(String alias) {
        switch (alias) {
            case "VISIT_NOTE":   return "d7151f82-c1f3-4152-a605-2f9ea7414a79";
            case "ADMISSION":    return "e22e39fd-7db2-45e7-80f1-60fa0d5a4378";
            case "DISCHARGE":    return "181820aa-88c9-479b-9077-af92f5364329";
            case "VITALS":       return "67a71486-1a54-468f-ac3e-7091a9a79584";
            case "CONSULTATION": return ReferenceDemoDataConstants.CONSULTATION_ENCOUNTER_TYPE_UUID;
            default: throw new IllegalArgumentException("unknown encounter type alias: " + alias);
        }
    }

    private void createCondition(Patient p, FixturePatient.Condition cf, DemoDataContext ctx, Location loc) {
        // Problem list is stored as Obs on CIEL 1284 (Problem added) with valueCoded = diagnosis concept.
        // status: ignored on 1.12 — no core ConditionService.
        // Needs an anchor encounter dated at onset.
        java.time.Instant onset = RelativeTime.resolve(cf.onset, ctx.clockAnchor());
        Encounter anchor = new Encounter();
        anchor.setEncounterType(Context.getEncounterService()
                .getEncounterTypeByUuid(ReferenceDemoDataConstants.CONSULTATION_ENCOUNTER_TYPE_UUID));
        anchor.setPatient(p);
        anchor.setEncounterDatetime(java.util.Date.from(onset));
        anchor.setLocation(loc);
        Context.getEncounterService().saveEncounter(anchor);

        Obs problem = new Obs();
        problem.setUuid(cf.uuid);
        problem.setPerson(p);
        problem.setObsDatetime(anchor.getEncounterDatetime());
        problem.setEncounter(anchor);
        problem.setConcept(aliases.resolve("PROBLEM_ADDED"));
        problem.setValueCoded(aliases.resolve(cf.alias));
        Context.getObsService().saveObs(problem, null);
    }

    private void createOrder(Patient p, FixturePatient.Order of, DemoDataContext ctx, Provider orderer) {
        DrugOrder o = new DrugOrder();
        o.setUuid(of.uuid);
        o.setPatient(p);
        Concept drugConcept = aliases.resolve(of.drug);
        Drug drug = firstDrugForConcept(drugConcept, of.uuid);
        o.setDrug(drug);
        o.setConcept(drugConcept);
        if (of.dose == null) {
            throw new IllegalStateException("fixture order " + of.uuid + " is missing required 'dose'");
        }
        o.setDose(of.dose);
        o.setDoseUnits(lookupConceptByName(of.doseUnits, of.uuid));
        o.setRoute(lookupConceptByName(of.route, of.uuid));
        o.setFrequency(lookupFrequencyByName(of.frequency, of.uuid));
        o.setDosingType(SimpleDosingInstructions.class);
        o.setUrgency(Order.Urgency.ROUTINE);
        o.setCareSetting(Context.getOrderService().getCareSettingByUuid(
                "inpatient".equalsIgnoreCase(of.careSetting)
                        ? ReferenceDemoDataConstants.CARE_SETTING_INPATIENT_UUID
                        : ReferenceDemoDataConstants.CARE_SETTING_OUTPATIENT_UUID));
        o.setDateActivated(Date.from(RelativeTime.resolve(of.startDate, ctx.clockAnchor())));
        o.setOrderer(orderer);
        o.setAction(Order.Action.NEW);
        if (of.durationDays != null) {
            o.setDuration(of.durationDays);
            o.setDurationUnits(lookupConceptByName("Days", of.uuid));
        }
        // Orders need an encounter; synthesize one anchored at startDate.
        Encounter syntheticEnc = new Encounter();
        syntheticEnc.setEncounterType(Context.getEncounterService()
                .getEncounterTypeByUuid(ReferenceDemoDataConstants.CONSULTATION_ENCOUNTER_TYPE_UUID));
        syntheticEnc.setPatient(p);
        syntheticEnc.setEncounterDatetime(o.getDateActivated());
        syntheticEnc.setLocation(getDefaultLocation());
        Context.getEncounterService().saveEncounter(syntheticEnc);
        o.setEncounter(syntheticEnc);
        Context.getOrderService().saveOrder(o, null);
    }

    private void createLabs(Patient p, FixturePatient fx, DemoDataContext ctx, Location loc) {
        java.util.Map<String, Encounter> byDate = new java.util.TreeMap<>();
        for (FixturePatient.Lab lab : fx.labs) {
            Encounter e = byDate.get(lab.date);
            if (e == null) {
                java.time.Instant when = RelativeTime.resolve(lab.date, ctx.clockAnchor());
                e = new Encounter();
                e.setEncounterType(Context.getEncounterService()
                        .getEncounterTypeByUuid(ReferenceDemoDataConstants.CONSULTATION_ENCOUNTER_TYPE_UUID));
                e.setPatient(p);
                e.setEncounterDatetime(java.util.Date.from(when));
                e.setLocation(loc);
                Context.getEncounterService().saveEncounter(e);
                byDate.put(lab.date, e);
            }
            Obs obs = new Obs();
            obs.setPerson(p);
            obs.setObsDatetime(e.getEncounterDatetime());
            obs.setEncounter(e);
            obs.setConcept(aliases.resolve(lab.concept));
            obs.setValueNumeric(lab.value);
            Context.getObsService().saveObs(obs, null);
        }
    }

    private Drug firstDrugForConcept(Concept concept, String fixtureUuid) {
        List<Drug> drugs = Context.getConceptService().getDrugsByConcept(concept);
        if (drugs == null || drugs.isEmpty()) {
            throw new IllegalStateException("fixture order " + fixtureUuid
                    + ": no Drug found for concept " + (concept == null ? "null" : concept.getUuid()));
        }
        return drugs.stream()
                .sorted(java.util.Comparator.comparing(Drug::getUuid))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "fixture order " + fixtureUuid + ": no Drug found for concept "
                                + (concept == null ? "null" : concept.getUuid())));
    }

    private Concept lookupConceptByName(String name, String fixtureUuid) {
        if (name == null) return null;
        Concept c = Context.getConceptService().getConceptByName(name);
        if (c == null) {
            throw new IllegalStateException("fixture " + fixtureUuid + ": missing concept named '" + name + "'");
        }
        return c;
    }

    private org.openmrs.OrderFrequency lookupFrequencyByName(String name, String fixtureUuid) {
        if (name == null) return null;
        Concept concept = Context.getConceptService().getConceptByName(name);
        if (concept == null) {
            throw new IllegalStateException("fixture " + fixtureUuid + ": missing frequency concept named '" + name + "'");
        }
        org.openmrs.OrderFrequency freq = Context.getOrderService().getOrderFrequencyByConcept(concept);
        if (freq == null) {
            throw new IllegalStateException("fixture " + fixtureUuid + ": no OrderFrequency for concept '" + name + "'");
        }
        return freq;
    }
}
