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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.hibernate.cfg.Environment;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptName;
import org.openmrs.ConceptNumeric;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptSource;
import org.openmrs.Condition;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.EncounterRole;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.referencedemodata.DemoDataConceptCache;
import org.openmrs.module.referencedemodata.ReferenceDemoDataConstants;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.test.SkipBaseSetup;

/**
 * End-to-end test for {@link FixturePatientLoader}. Runs against an in-memory H2 context,
 * programmatically seeds CIEL concepts and providers, then loads {@code fixtures/devan-modi.json}
 * and asserts the resulting patient / encounters / orders.
 */
@SkipBaseSetup
public class FixturePatientLoaderTest extends BaseModuleContextSensitiveTest {

	private static final String DEVAN_PATIENT_UUID = "a1b2c3d4-e5f6-4789-a012-34567890abcd";

	private FixturePatientLoader loader;

	@Override
	public Properties getRuntimeProperties() {
		Properties props = super.getRuntimeProperties();
		String url = props.getProperty(Environment.URL);
		if (url != null && url.contains("jdbc:h2:") && !url.toUpperCase().contains(";MVCC=TRUE")) {
			props.setProperty(Environment.URL, url + ";MVCC=true");
		}
		return props;
	}

	@Before
	public void setUp() throws Exception {
		executeDataSet(INITIAL_XML_DATASET_PACKAGE_PATH);
		executeDataSet("FixturePatientLoaderTestDataset.xml");
		getConnection().commit();
		// Dataset rows above hardcode ids (concept_id / concept_reference_term_id /
		// concept_reference_map_id / concept_name_id / concept_set_id / encounter_type_id / etc.)
		// up to the 10,000s; bump the H2 identity counters past that so auto-generated ids from
		// later programmatic saves don't collide with fixed dataset rows.
		bumpH2IdentityCounters();
		getConnection().commit();
		updateSearchIndex();
		authenticate();

		seedCielConcepts();
		seedDrugOrderValidationConceptSets();
		seedProvidersAndEncounterRole();

		IdentifierSourceService mockIss = mock(IdentifierSourceService.class);
		AtomicInteger counter = new AtomicInteger(100000);
		when(mockIss.generateIdentifier(any(PatientIdentifierType.class), eq("DemoData")))
				.thenAnswer(inv -> "DEMO-" + counter.getAndIncrement());

		loader = new FixturePatientLoader(new DemoDataConceptCache(), mockIss);
	}

	// ------------------------------------------------------------------------
	// tests
	// ------------------------------------------------------------------------

	@Test
	public void loadFixture_createsDevanModiWithExpectedDemographics() {
		Patient patient = loader.loadFixture("fixtures/devan-modi.json");

		assertThat(patient, notNullValue());
		assertThat(patient.getUuid(), equalTo(DEVAN_PATIENT_UUID));
		assertThat(patient.getGivenName(), equalTo("Devan"));
		assertThat(patient.getFamilyName(), equalTo("Modi"));
		assertThat(patient.getGender(), equalTo("M"));

		LocalDate birth = patient.getBirthdate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		int age = Period.between(birth, LocalDate.now()).getYears();
		assertThat("Age should be 65 relative to today", age, equalTo(65));
	}

	@Test
	public void loadFixture_createsThreeActiveConditions() {
		Patient patient = loader.loadFixture("fixtures/devan-modi.json");

		List<Condition> active = Context.getConditionService().getActiveConditions(patient);

		// The `conditions[]` section creates exactly 3 conditions (DM, HTN, PUD). Every
		// `DemoDiagnosisGenerator.createDiagnosis` call also creates a backing Condition (ACTIVE),
		// so the total row count is 3 problem-list conditions + 5 diagnosis-backed conditions
		// (DM + HTN from visits, Non-bleeding gastric ulcer from EGD, PUD + Upper GI Bleed from
		// discharge summary). Assert the 3 problem-list conditions are present as a subset.
		java.util.Set<String> conceptUuids = active.stream()
				.map(c -> c.getCondition().getCoded())
				.filter(java.util.Objects::nonNull)
				.map(Concept::getUuid)
				.collect(java.util.stream.Collectors.toSet());

		String dmUuid = Context.getConceptService().getConceptByMapping("142474", "CIEL").getUuid();
		String htnUuid = Context.getConceptService().getConceptByMapping("117399", "CIEL").getUuid();
		String pudUuid = Context.getConceptService().getConceptByMapping("114262", "CIEL").getUuid();

		assertTrue("Active conditions should include Type 2 Diabetes Mellitus", conceptUuids.contains(dmUuid));
		assertTrue("Active conditions should include Hypertension", conceptUuids.contains(htnUuid));
		assertTrue("Active conditions should include Peptic Ulcer Disease", conceptUuids.contains(pudUuid));
		assertThat("Expected at least 3 active conditions (3 problem-list + diagnosis-backed)",
				active.size(), greaterThanOrEqualTo(3));
	}

	@Test
	public void loadFixture_createsVitalsAndBmiEncounters() {
		Patient patient = loader.loadFixture("fixtures/devan-modi.json");

		EncounterService es = Context.getEncounterService();
		List<Encounter> vitalsEncounters = es.getEncountersByPatient(patient).stream()
				.filter(e -> e.getEncounterType() != null && "Vitals".equals(e.getEncounterType().getName()))
				.collect(Collectors.toList());

		// 4 vitals encounters + 3 weight/height encounters (all use Vitals encounter type)
		assertThat("Expected 7 Vitals-type encounters (4 vitals + 3 weight/height)",
				vitalsEncounters, hasSize(7));
	}

	@Test
	public void loadFixture_createsSevenDrugOrders() {
		Patient patient = loader.loadFixture("fixtures/devan-modi.json");

		List<DrugOrder> drugOrders = Context.getOrderService().getAllOrdersByPatient(patient).stream()
				.filter(o -> o instanceof DrugOrder)
				.map(o -> (DrugOrder) o)
				.collect(Collectors.toList());

		assertThat("Expected 7 total drug orders", drugOrders, hasSize(7));

		long active = drugOrders.stream().filter(o -> o.getAutoExpireDate() == null).count();
		long inactive = drugOrders.stream().filter(o -> o.getAutoExpireDate() != null).count();
		assertThat("Expected 4 active (no autoExpire)", active, equalTo(4L));
		assertThat("Expected 3 inactive (autoExpire set)", inactive, equalTo(3L));

		Date now = new Date();
		long expired = drugOrders.stream()
				.filter(o -> o.getAutoExpireDate() != null && o.getAutoExpireDate().before(now))
				.count();
		assertThat("Expected 3 inactive orders with autoExpire in the past", expired, equalTo(3L));
	}

	@Test
	public void loadFixture_createsLabObsOnFiveDates() {
		Patient patient = loader.loadFixture("fixtures/devan-modi.json");

		EncounterService es = Context.getEncounterService();
		List<Encounter> labEncounters = es.getEncountersByPatient(patient).stream()
				.filter(e -> e.getEncounterType() != null && "Lab Results".equals(e.getEncounterType().getName()))
				.collect(Collectors.toList());

		assertThat("Expected 5 Lab Results encounters (one per fixture date)",
				labEncounters, hasSize(5));

		// Sanity-check that lab obs are attached.
		long totalLabObs = labEncounters.stream().mapToLong(e -> e.getAllObs().size()).sum();
		assertThat("Expected lab encounters to carry numeric obs", totalLabObs,
				greaterThanOrEqualTo((long) labEncounters.size()));
	}

	@Test
	public void loadFixture_createsNarrativeAndProcedureAndDischargeEncounters() {
		Patient patient = loader.loadFixture("fixtures/devan-modi.json");

		EncounterService es = Context.getEncounterService();
		List<Encounter> allEncounters = es.getEncountersByPatient(patient);

		long visitNotes = allEncounters.stream()
				.filter(e -> e.getEncounterType() != null && "Visit Note".equals(e.getEncounterType().getName()))
				.count();
		long procedureNotes = allEncounters.stream()
				.filter(e -> e.getEncounterType() != null && "Procedure Note".equals(e.getEncounterType().getName()))
				.count();

		// 4 narrative visits; discharge summary falls back to Visit Note when no "Discharge Summary" type exists
		assertThat("Expected at least 4 Visit Note encounters for narrative visits",
				visitNotes, greaterThanOrEqualTo(4L));
		assertThat("Expected exactly 1 Procedure Note encounter", procedureNotes, equalTo(1L));

		// Count text obs under CIEL:162169 (narrative + procedure + discharge question concept).
		Concept questionConcept = Context.getConceptService().getConceptByMapping("162169", "CIEL");
		assertNotNull("CIEL:162169 concept should be pre-seeded", questionConcept);
		long textObsCount = allEncounters.stream()
				.flatMap(e -> e.getAllObs().stream())
				.filter(o -> questionConcept.equals(o.getConcept()) && o.getValueText() != null)
				.count();
		// 4 narrative visit notes + 1 procedure note + 1 discharge summary = 6 text obs
		assertThat("Expected at least 6 text obs under the visit-note concept",
				textObsCount, greaterThanOrEqualTo(6L));
	}

	@Test
	public void loadFixture_isIdempotentOnReload() {
		Patient first = loader.loadFixture("fixtures/devan-modi.json");
		int encountersBefore = Context.getEncounterService().getEncountersByPatient(first).size();
		int ordersBefore = Context.getOrderService().getAllOrdersByPatient(first).size();

		Patient second = loader.loadFixture("fixtures/devan-modi.json");

		assertThat("Reload should return the same patient row", second.getPatientId(), equalTo(first.getPatientId()));

		PatientService ps = Context.getPatientService();
		long count = ps.getAllPatients().stream()
				.filter(p -> DEVAN_PATIENT_UUID.equals(p.getUuid()))
				.count();
		assertThat("Expected exactly one patient row with Devan's UUID", count, equalTo(1L));

		int encountersAfter = Context.getEncounterService().getEncountersByPatient(second).size();
		int ordersAfter = Context.getOrderService().getAllOrdersByPatient(second).size();
		assertThat("Reload must not duplicate encounters", encountersAfter, equalTo(encountersBefore));
		assertThat("Reload must not duplicate orders", ordersAfter, equalTo(ordersBefore));
	}

	@Test
	public void loadFixture_throwsAPIExceptionWhenRequiredConceptMissing() {
		try {
			loader.loadFixture("fixtures/test-missing-concept.json");
			fail("Expected APIException for unresolvable CIEL:99999999");
		}
		catch (APIException e) {
			assertThat(e.getMessage(), containsString("99999999"));
		}

		// No partial patient should have been saved.
		assertNull("No patient row should be saved on concept-resolution failure",
				Context.getPatientService().getPatientByUuid("00000000-1111-2222-3333-444444444444"));
	}

	// ------------------------------------------------------------------------
	// seeding helpers
	// ------------------------------------------------------------------------

	private void bumpH2IdentityCounters() throws java.sql.SQLException {
		// The in-memory H2 schema created by OpenMRS liquibase does NOT set up auto-increment on
		// every primary-key id column (e.g. concept_id comes through as a plain INT). Ensure every
		// *_id PK column on tables we'll write to becomes an AUTO_INCREMENT with a starting value
		// past the hardcoded dataset ids (~10,000s), so Hibernate's NativeIfNotAssigned generator
		// can issue identity-based INSERTs without NULL constraint violations.
		String[][] tableAndIdColumn = {
				{ "concept", "concept_id" },
				{ "concept_name", "concept_name_id" },
				{ "concept_reference_term", "concept_reference_term_id" },
				{ "concept_reference_map", "concept_map_id" },
				{ "concept_set", "concept_set_id" },
				{ "encounter", "encounter_id" },
				{ "encounter_type", "encounter_type_id" },
				{ "encounter_role", "encounter_role_id" },
				{ "obs", "obs_id" },
				{ "orders", "order_id" },
				{ "drug_order", "order_id" },
				{ "patient_identifier", "patient_identifier_id" },
				{ "person", "person_id" },
				{ "person_name", "person_name_id" },
				{ "person_attribute", "person_attribute_id" },
				{ "person_attribute_type", "person_attribute_type_id" },
				{ "provider", "provider_id" },
				{ "location", "location_id" },
				{ "visit", "visit_id" },
				{ "conditions", "condition_id" },
				{ "diagnosis", "diagnosis_id" },
				{ "order_frequency", "order_frequency_id" },
				{ "notification_alert", "alert_id" },
				{ "users", "user_id" }
		};
		try (java.sql.Statement exec = getConnection().createStatement()) {
			for (String[] pair : tableAndIdColumn) {
				try {
					exec.execute("ALTER TABLE " + pair[0] + " ALTER COLUMN " + pair[1]
							+ " INT NOT NULL AUTO_INCREMENT");
					exec.execute("ALTER TABLE " + pair[0] + " ALTER COLUMN " + pair[1]
							+ " RESTART WITH 200000");
				}
				catch (java.sql.SQLException ignored) {
					// Table may not exist or already be auto-increment; skip.
				}
			}
		}
	}


	private void seedCielConcepts() {
		// Diagnoses (class=Diagnosis, datatype=N/A)
		ensureCielConcept("142474", "Type 2 Diabetes Mellitus", "Diagnosis", "N/A");
		ensureCielConcept("117399", "Hypertension", "Diagnosis", "N/A");
		ensureCielConcept("114262", "Peptic Ulcer Disease", "Diagnosis", "N/A");
		ensureCielConcept("123529", "Non-bleeding gastric ulcer", "Diagnosis", "N/A");
		ensureCielConcept("123569", "Upper GI Bleed", "Diagnosis", "N/A");
		ensureCielConcept("117152", "H. pylori infection", "Diagnosis", "N/A");

		// Drugs (class=Drug, datatype=N/A)
		ensureCielConcept("75875", "Esomeprazole", "Drug", "N/A");
		ensureCielConcept("77696", "Hydrochlorothiazide", "Drug", "N/A");
		ensureCielConcept("71137", "Amlodipine", "Drug", "N/A");
		ensureCielConcept("79651", "Metformin", "Drug", "N/A");
		ensureCielConcept("70116", "Bismuth subsalicylate", "Drug", "N/A");
		ensureCielConcept("84893", "Tetracycline", "Drug", "N/A");
		ensureCielConcept("79782", "Metronidazole", "Drug", "N/A");

		// Units of Measure
		ensureCielConcept("1513", "mg", "Units of Measure", "N/A");

		// Procedure (route = Oral)
		ensureCielConcept("160240", "Oral", "Procedure", "N/A");

		// Frequency
		ensureCielConcept("160858", "Twice daily", "Frequency", "N/A");
		ensureCielConcept("160862", "Once daily", "Frequency", "N/A");
		ensureCielConcept("160870", "Four times daily", "Frequency", "N/A");

		// Labs (Test, datatype=Numeric)
		ensureCielConcept("21", "Haemoglobin-CIEL", "Test", "Numeric");
		ensureCielConcept("678", "WBC", "Test", "Numeric");
		ensureCielConcept("729", "Platelets", "Test", "Numeric");
		ensureCielConcept("1006", "Sodium", "Test", "Numeric");
		ensureCielConcept("857", "BUN", "Test", "Numeric");
		ensureCielConcept("790", "Creatinine", "Test", "Numeric");
		ensureCielConcept("1133", "Potassium-CIEL", "Test", "Numeric");
		ensureCielConcept("887", "Glucose", "Test", "Numeric");
		ensureCielConcept("159644", "HbA1c", "Test", "Numeric");

		// CIEL:162169 is pre-seeded by the dataset — verify its mapping resolves.
		Concept preSeeded = Context.getConceptService().getConceptByMapping("162169", "CIEL");
		assertNotNull("Pre-seeded CIEL:162169 mapping should resolve (visit-note question concept)",
				preSeeded);
	}

	private void ensureCielConcept(String code, String name, String className, String datatypeName) {
		ConceptService cs = Context.getConceptService();
		if (cs.getConceptByMapping(code, "CIEL") != null) {
			return;
		}

		ConceptSource cielSource = cs.getConceptSourceByName("CIEL");
		assertNotNull("CIEL concept source must exist", cielSource);

		ConceptClass conceptClass = cs.getConceptClassByName(className);
		assertNotNull("Concept class must exist: " + className, conceptClass);
		ConceptDatatype datatype = cs.getConceptDatatypeByName(datatypeName);
		assertNotNull("Concept datatype must exist: " + datatypeName, datatype);
		ConceptMapType sameAs = cs.getConceptMapTypeByName("same-as");
		assertNotNull("ConceptMapType 'same-as' must exist", sameAs);

		ConceptReferenceTerm term = new ConceptReferenceTerm(cielSource, code, null);
		term = cs.saveConceptReferenceTerm(term);

		Concept concept;
		if ("Numeric".equals(datatypeName)) {
			ConceptNumeric numeric = new ConceptNumeric();
			numeric.setAllowDecimal(true);
			concept = numeric;
		} else {
			concept = new Concept();
		}
		concept.setConceptClass(conceptClass);
		concept.setDatatype(datatype);
		ConceptName conceptName = new ConceptName(name + " [" + code + "]", Locale.ENGLISH);
		concept.setFullySpecifiedName(conceptName);
		concept.addConceptMapping(new ConceptMap(term, sameAs));

		cs.saveConcept(concept);
	}

	/**
	 * OpenMRS outpatient DrugOrder validation checks that dose-units / route / quantity-units
	 * concepts are members of concept sets named by the global properties
	 * {@code order.drugDosingUnitsConceptUuid}, {@code order.drugRoutesConceptUuid}, and
	 * {@code order.drugDispensingUnitsConceptUuid}. Build set concepts containing the fixture's
	 * "mg" and "Oral" CIEL concepts and point the GPs at them so validation passes in tests.
	 */
	private void seedDrugOrderValidationConceptSets() {
		ConceptService cs = Context.getConceptService();
		Concept mg = cs.getConceptByMapping("1513", "CIEL");
		Concept oral = cs.getConceptByMapping("160240", "CIEL");
		assertNotNull("seeded dose-units concept (CIEL:1513)", mg);
		assertNotNull("seeded route concept (CIEL:160240)", oral);

		ConceptClass convSet = cs.getConceptClassByName("ConvSet");
		ConceptDatatype naDatatype = cs.getConceptDatatypeByName("N/A");
		assertNotNull("ConceptClass ConvSet must exist", convSet);
		assertNotNull("ConceptDatatype N/A must exist", naDatatype);

		Concept doseUnitsSet = createConceptSet(cs, convSet, naDatatype, "Drug Dosing Units Test Set",
				java.util.Arrays.asList(mg));
		Concept routesSet = createConceptSet(cs, convSet, naDatatype, "Drug Routes Test Set",
				java.util.Arrays.asList(oral));
		Concept dispensingUnitsSet = createConceptSet(cs, convSet, naDatatype,
				"Drug Dispensing Units Test Set", java.util.Arrays.asList(mg));

		org.openmrs.api.AdministrationService as = Context.getAdministrationService();
		saveGpUuid(as, "order.drugDosingUnitsConceptUuid", doseUnitsSet.getUuid());
		saveGpUuid(as, "order.drugRoutesConceptUuid", routesSet.getUuid());
		saveGpUuid(as, "order.drugDispensingUnitsConceptUuid", dispensingUnitsSet.getUuid());
	}

	private Concept createConceptSet(ConceptService cs, ConceptClass clazz, ConceptDatatype datatype,
			String name, List<Concept> members) {
		Concept setConcept = new Concept();
		setConcept.setConceptClass(clazz);
		setConcept.setDatatype(datatype);
		setConcept.setSet(true);
		setConcept.setFullySpecifiedName(new ConceptName(name, Locale.ENGLISH));
		for (Concept member : members) {
			setConcept.addSetMember(member);
		}
		return cs.saveConcept(setConcept);
	}

	private void saveGpUuid(org.openmrs.api.AdministrationService as, String property, String value) {
		org.openmrs.GlobalProperty gp = as.getGlobalPropertyObject(property);
		if (gp == null) {
			gp = new org.openmrs.GlobalProperty(property, value);
		} else {
			gp.setPropertyValue(value);
		}
		as.saveGlobalProperty(gp);
	}

	private void seedProvidersAndEncounterRole() {
		PersonService personService = Context.getPersonService();
		ProviderService providerService = Context.getProviderService();

		// Ensure an encounter role named "Clinician" exists so the loader can attach providers
		// to encounters (the loader guards `addProvider` on role != null).
		EncounterService es = Context.getEncounterService();
		EncounterRole clinicianRole = es.getEncounterRoleByName("Clinician");
		if (clinicianRole == null) {
			clinicianRole = new EncounterRole();
			clinicianRole.setName("Clinician");
			clinicianRole.setDescription("Provider responsible for care during an encounter");
			es.saveEncounterRole(clinicianRole);
		}

		ensureProvider(personService, providerService, "doctor",
				ReferenceDemoDataConstants.DOCTOR_PERSON_UUID,
				ReferenceDemoDataConstants.DOCTOR_PROVIDER_UUID, "Jake", "Doctor", "M");
		ensureProvider(personService, providerService, "nurse",
				ReferenceDemoDataConstants.NURSE_PERSON_UUID,
				ReferenceDemoDataConstants.NURSE_PROVIDER_UUID, "Jane", "Nurse", "F");
	}

	private void ensureProvider(PersonService personService, ProviderService providerService,
			String identifier, String personUuid, String providerUuid,
			String given, String family, String gender) {
		if (providerService.getProviderByIdentifier(identifier) != null) {
			return;
		}
		Person person = personService.getPersonByUuid(personUuid);
		if (person == null) {
			person = new Person();
			person.setUuid(personUuid);
			person.setGender(gender);
			person.setBirthdate(new Date());
			PersonName personName = new PersonName(given, null, family);
			person.addName(personName);
			personService.savePerson(person);
		}
		Provider provider = new Provider();
		provider.setUuid(providerUuid);
		provider.setPerson(person);
		provider.setIdentifier(identifier);
		provider.setName(given + " " + family);
		providerService.saveProvider(provider);
	}
}
