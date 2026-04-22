/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 */
package org.openmrs.module.referencedemodata;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Visit;

import org.hibernate.cfg.Environment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.SequentialIdentifierGenerator;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.idgen.validator.LuhnMod30IdentifierValidator;
import org.openmrs.module.referencemetadata.ReferenceMetadataActivator;
import org.openmrs.module.referencemetadata.ReferenceMetadataConstants;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.test.SkipBaseSetup;

/**
 * Asserts that random demo-patient generation is reproducible given a fixed seed,
 * down through visits, encounters, and obs values.
 *
 * <p>Hashes every ctx-derived field: patient names / gender / birthdate / address,
 * visit datetimes + type UUID, encounter datetimes + type UUID, and each obs's
 * concept UUID + value. Uses UUIDs rather than names so locale-sensitive
 * {@code Concept.getName()} can't smuggle non-determinism into the hash.
 *
 * <p>Excludes:
 * <ul>
 *   <li>Patient identifiers — idgen owns the sequence and is outside the ctx contract.</li>
 *   <li>Hibernate-assigned row UUIDs and primary keys — non-deterministic regardless
 *       of seed.</li>
 * </ul>
 *
 * <p>Determinism at the obs layer depends on {@code RandomPatientGenerator} using
 * {@code ctx.pick(list, stableKey)} (not {@code randomArrayEntry}) for any list
 * that comes back from the DB — otherwise locale-preferred concept-name sorting
 * in {@code ConceptService.getConceptsByClass} or session-state-dependent ordering
 * in {@code VisitService.getAllVisitTypes} would desynchronise the RNG.
 */
@SkipBaseSetup
public class ReproducibilityTest extends BaseModuleContextSensitiveTest {

    private static final Instant CLOCK_ANCHOR = Instant.parse("2025-01-01T00:00:00Z");
    private static final long SEED = 42L;
    private static final int PATIENT_COUNT = 3;

    private long idgenCounter;
    private long idgenSeedOffset;
    private SequentialIdentifierGenerator mockIdGenerator;
    private Locale originalLocale;
    private TimeZone originalTimeZone;

    @Override
    public Properties getRuntimeProperties() {
        Properties props = super.getRuntimeProperties();
        String url = props.getProperty(Environment.URL);
        if (url != null && url.contains("jdbc:h2:") && !url.contains(";MVCC=TRUE")) {
            props.setProperty(Environment.URL, url + ";MVCC=true");
        }
        return props;
    }

    @Before
    public void setUp() throws Exception {
        initializeInMemoryDatabase();
        executeDataSet("requiredDataTestDataset.xml");
        authenticate();
        new ReferenceMetadataActivator().started();
        // Also run the demo module's activator for its MDS packages (root locations, forms,
        // etc.) that RandomPatientGenerator depends on. The GP defaults to 0 so no patients
        // are created here — those come from the explicit generate() call below.
        new ReferenceDemoDataActivator().started();
        originalLocale = Locale.getDefault();
        originalTimeZone = TimeZone.getDefault();
    }

    @After
    public void tearDown() {
        DemoIdentifiers.setIdentifierSourceService(null);
        if (originalLocale != null) Locale.setDefault(originalLocale);
        if (originalTimeZone != null) TimeZone.setDefault(originalTimeZone);
    }

    @Test
    public void sameSeedYieldsIdenticalCtxDerivedHash() throws Exception {
        Locale.setDefault(Locale.US);
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

        String h1 = generateAndHashNew();
        // No DB cleanup between runs — UUID-set diff ensures each run hashes only its own
        // patients. The idgen mock uses a nanoTime offset so identifiers don't collide.
        String h2 = generateAndHashNew();

        assertEquals("same (seed, clockAnchor) must produce identical ctx-derived output", h1, h2);
    }

    private String generateAndHashNew() throws Exception {
        Set<String> before = snapshotPatientUuids();
        installMockIdentifierSource();
        DemoDataContext ctx = DemoDataContext.of(SEED, CLOCK_ANCHOR);
        new RandomPatientGenerator().generate(ctx, PATIENT_COUNT);
        return hashCtxDerived(patientsAddedSince(before));
    }

    private Set<String> snapshotPatientUuids() {
        Set<String> uuids = new HashSet<String>();
        for (Patient p : Context.getPatientService().getAllPatients()) {
            uuids.add(p.getUuid());
        }
        return uuids;
    }

    private List<Patient> patientsAddedSince(Set<String> beforeUuids) {
        List<Patient> fresh = new ArrayList<Patient>();
        for (Patient p : Context.getPatientService().getAllPatients()) {
            if (!beforeUuids.contains(p.getUuid())) fresh.add(p);
        }
        return fresh;
    }

    private void installMockIdentifierSource() {
        idgenCounter = 0;
        // Large per-run offset so encoded IDs (a) don't collide with MDS pre-baked patients
        // (which occupy the low 6-char Luhn-mod-30 range) and (b) differ between runs —
        // identifiers intentionally aren't in the hash.
        idgenSeedOffset = Math.abs(System.nanoTime());
        PatientIdentifierType openmrsIdType = Context.getPatientService()
                .getPatientIdentifierTypeByName(ReferenceMetadataConstants.OPENMRS_ID_NAME);
        mockIdGenerator = new SequentialIdentifierGenerator();
        mockIdGenerator.setIdentifierType(openmrsIdType);
        mockIdGenerator.setName(ReferenceMetadataConstants.OPENMRS_ID_GENERATOR_NAME);
        mockIdGenerator.setUuid(ReferenceMetadataConstants.OPENMRS_ID_GENERATOR_UUID);
        mockIdGenerator.setBaseCharacterSet(new LuhnMod30IdentifierValidator().getBaseCharacters());
        mockIdGenerator.setMinLength(6);
        mockIdGenerator.setFirstIdentifierBase("100000");

        IdentifierSourceService mockIss = Mockito.mock(IdentifierSourceService.class);
        Mockito.when(mockIss.generateIdentifier(Mockito.eq(openmrsIdType),
                Mockito.eq(ReferenceDemoDataConstants.DEMO_IDGEN_SOURCE_NAME)))
                .thenAnswer(new Answer<String>() {
                    @Override
                    public String answer(InvocationOnMock invocation) {
                        idgenCounter++;
                        return mockIdGenerator.getIdentifierForSeed(idgenSeedOffset + idgenCounter);
                    }
                });
        DemoIdentifiers.setIdentifierSourceService(mockIss);
    }

    private String hashCtxDerived(List<Patient> patients) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        Collections.sort(patients, BY_PATIENT_CTX_KEY);
        for (Patient p : patients) {
            hashPatient(md, p);
        }
        return Base64.getEncoder().encodeToString(md.digest());
    }

    private void hashPatient(MessageDigest md, Patient p) {
        putStr(md, p.getGivenName());
        putStr(md, p.getFamilyName());
        putStr(md, p.getGender());
        if (p.getBirthdate() != null) {
            md.update(longBytes(p.getBirthdate().getTime()));
        }
        PersonAddress a = p.getPersonAddress();
        if (a != null) {
            putStr(md, a.getAddress1());
            putStr(md, a.getCityVillage());
            putStr(md, a.getStateProvince());
            putStr(md, a.getCountry());
            putStr(md, a.getPostalCode());
        }
        List<Visit> visits = new ArrayList<Visit>(
                Context.getVisitService().getVisitsByPatient(p));
        Collections.sort(visits, BY_VISIT_START);
        for (Visit v : visits) hashVisit(md, v);
    }

    private void hashVisit(MessageDigest md, Visit v) {
        putStr(md, v.getVisitType() == null ? null : v.getVisitType().getUuid());
        if (v.getStartDatetime() != null) md.update(longBytes(v.getStartDatetime().getTime()));
        if (v.getStopDatetime() != null)  md.update(longBytes(v.getStopDatetime().getTime()));
        List<Encounter> encs = new ArrayList<Encounter>(
                Context.getEncounterService().getEncountersByVisit(v, false));
        Collections.sort(encs, BY_ENCOUNTER_DATETIME_TYPE);
        for (Encounter e : encs) hashEncounter(md, e);
    }

    private void hashEncounter(MessageDigest md, Encounter e) {
        putStr(md, e.getEncounterType() == null ? null : e.getEncounterType().getUuid());
        if (e.getEncounterDatetime() != null) md.update(longBytes(e.getEncounterDatetime().getTime()));
        List<Obs> obs = new ArrayList<Obs>(e.getAllObs(false));
        Collections.sort(obs, BY_OBS_CONCEPT_VALUE);
        for (Obs o : obs) hashObs(md, o);
    }

    private void hashObs(MessageDigest md, Obs o) {
        putStr(md, conceptUuid(o.getConcept()));
        if (o.getValueNumeric() != null) {
            md.update(longBytes(Double.doubleToLongBits(o.getValueNumeric())));
        }
        putStr(md, o.getValueText());
        putStr(md, conceptUuid(o.getValueCoded()));
    }

    private static String conceptUuid(Concept c) { return c == null ? null : c.getUuid(); }

    private static void putStr(MessageDigest md, String s) {
        if (s == null) {
            md.update((byte) 0);
        } else {
            md.update(s.getBytes(StandardCharsets.UTF_8));
        }
        md.update((byte) '|');
    }

    private static byte[] longBytes(long l) {
        return ByteBuffer.allocate(8).putLong(l).array();
    }

    // Anonymous Comparators — avoid method references for consistency with other
    // context-sensitive tests in this module.
    private static final Comparator<Patient> BY_PATIENT_CTX_KEY = new Comparator<Patient>() {
        @Override public int compare(Patient a, Patient b) {
            int c = Long.compare(
                    a.getBirthdate() == null ? 0L : a.getBirthdate().getTime(),
                    b.getBirthdate() == null ? 0L : b.getBirthdate().getTime());
            if (c != 0) return c;
            c = nullToEmpty(a.getGivenName()).compareTo(nullToEmpty(b.getGivenName()));
            if (c != 0) return c;
            c = nullToEmpty(a.getFamilyName()).compareTo(nullToEmpty(b.getFamilyName()));
            if (c != 0) return c;
            PersonAddress aa = a.getPersonAddress();
            PersonAddress ba = b.getPersonAddress();
            return nullToEmpty(aa == null ? null : aa.getPostalCode())
                    .compareTo(nullToEmpty(ba == null ? null : ba.getPostalCode()));
        }
    };

    private static final Comparator<Visit> BY_VISIT_START = new Comparator<Visit>() {
        @Override public int compare(Visit a, Visit b) {
            return Long.compare(
                    a.getStartDatetime() == null ? 0L : a.getStartDatetime().getTime(),
                    b.getStartDatetime() == null ? 0L : b.getStartDatetime().getTime());
        }
    };

    private static final Comparator<Encounter> BY_ENCOUNTER_DATETIME_TYPE = new Comparator<Encounter>() {
        @Override public int compare(Encounter a, Encounter b) {
            int c = Long.compare(
                    a.getEncounterDatetime() == null ? 0L : a.getEncounterDatetime().getTime(),
                    b.getEncounterDatetime() == null ? 0L : b.getEncounterDatetime().getTime());
            if (c != 0) return c;
            return nullToEmpty(a.getEncounterType() == null ? null : a.getEncounterType().getUuid())
                    .compareTo(nullToEmpty(b.getEncounterType() == null ? null : b.getEncounterType().getUuid()));
        }
    };

    private static final Comparator<Obs> BY_OBS_CONCEPT_VALUE = new Comparator<Obs>() {
        @Override public int compare(Obs a, Obs b) {
            int c = nullToEmpty(conceptUuid(a.getConcept()))
                    .compareTo(nullToEmpty(conceptUuid(b.getConcept())));
            if (c != 0) return c;
            Double av = a.getValueNumeric();
            Double bv = b.getValueNumeric();
            if (av != null && bv != null) {
                c = Double.compare(av, bv);
                if (c != 0) return c;
            }
            c = nullToEmpty(a.getValueText()).compareTo(nullToEmpty(b.getValueText()));
            if (c != 0) return c;
            // Tiebreaker: coded value (matters for diagnosis groups where two obs share
            // the same concept UUID but differ in valueCoded).
            return nullToEmpty(conceptUuid(a.getValueCoded()))
                    .compareTo(nullToEmpty(conceptUuid(b.getValueCoded())));
        }
    };

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
