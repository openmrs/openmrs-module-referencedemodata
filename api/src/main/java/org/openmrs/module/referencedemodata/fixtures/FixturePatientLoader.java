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

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.api.APIException;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.referencedemodata.DemoDataConceptCache;
import org.openmrs.module.referencedemodata.Randomizer;
import org.openmrs.module.referencedemodata.condition.DemoConditionGenerator;
import org.openmrs.module.referencedemodata.diagnosis.DemoDiagnosisGenerator;
import org.openmrs.module.referencedemodata.obs.DemoObsGenerator;
import org.openmrs.module.referencedemodata.orders.DemoOrderGenerator;
import org.openmrs.module.referencedemodata.providers.DemoProviderGenerator;

import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.DEMO_PATIENT_ATTR;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.OPENMRS_ID_NAME;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;
import static org.openmrs.module.referencedemodata.patient.DemoPersonGenerator.populatePerson;

/**
 * Loads a deterministic demo patient (e.g. Devan Modi) from a JSON fixture on the classpath.
 *
 * <p>The fixture describes the patient's demographics, longitudinal conditions, and visit history
 * using relative date offsets that are resolved against {@link java.time.LocalDate#now()} at load
 * time. Concept references (UUIDs or {@code CIEL:<code>} mappings) are resolved through
 * {@link DemoDataConceptCache} during a resolve phase that runs before any DB write — any
 * unresolved identifier fails loudly with an {@link APIException} and no partial patient is saved.
 */
@Slf4j
public class FixturePatientLoader {
	
	private final IdentifierSourceService iss;
	private final ObjectMapper objectMapper;
	private final DemoConditionGenerator conditionGenerator;
	private final FixtureResolver resolver;
	private final FixtureVisitApplier visitApplier;
	
	public FixturePatientLoader(DemoDataConceptCache conceptCache, IdentifierSourceService iss) {
		this(conceptCache, iss,
				new DemoConditionGenerator(),
				new DemoObsGenerator(conceptCache),
				new DemoProviderGenerator(),
				new DemoOrderGenerator(),
				null);
	}
	
	FixturePatientLoader(DemoDataConceptCache conceptCache, IdentifierSourceService iss,
			DemoConditionGenerator conditionGenerator, DemoObsGenerator obsGenerator,
			DemoProviderGenerator providerGenerator, DemoOrderGenerator orderGenerator,
			ObjectMapper objectMapper) {
		this.iss = iss;
		this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
		this.conditionGenerator = conditionGenerator;
		DemoDiagnosisGenerator diagnosisGenerator = new DemoDiagnosisGenerator(conceptCache, conditionGenerator);
		this.resolver = new FixtureResolver(conceptCache);
		this.visitApplier = new FixtureVisitApplier(obsGenerator, orderGenerator,
				diagnosisGenerator, providerGenerator);
	}
	
	public Patient loadFixture(String classpathResourcePath) {
		JsonNode root = readFixture(classpathResourcePath);
		
		String patientUuid = root.path("patientUuid").asText(null);
		if (StringUtils.isBlank(patientUuid)) {
			throw new APIException("Fixture [" + classpathResourcePath + "] is missing required field 'patientUuid'");
		}
		JsonNode demographics = root.path("demographics");
		if (demographics.isMissingNode() || !demographics.isObject()) {
			throw new APIException("Fixture [" + classpathResourcePath + "] is missing required object 'demographics'");
		}
		
		PatientService ps = Context.getPatientService();
		Patient existing = ps.getPatientByUuid(patientUuid);
		if (existing != null) {
			log.warn("Fixture patient {} already exists (uuid={}, resource={}); skipping reload",
					new Object[] { existing.getPersonName(), patientUuid, classpathResourcePath });
			return existing;
		}
		
		// Resolve phase — fail loudly before any DB write.
		List<ResolvedCondition> conditions = resolver.resolveConditions(root.path("conditions"));
		List<ResolvedVisit> visits = resolver.resolveVisits(root.path("visits"));
		
		// Apply phase.
		Patient saved = createPatient(patientUuid, demographics);
		applyConditions(saved, conditions);
		visitApplier.apply(saved, visits);
		
		log.info("Loaded fixture patient {} {} (uuid={})",
				new Object[] { saved.getGivenName(), saved.getFamilyName(), saved.getUuid() });
		return saved;
	}
	
	private Patient createPatient(String patientUuid, JsonNode demographics) {
		PatientService ps = Context.getPatientService();
		PersonService personService = Context.getPersonService();
		
		PatientIdentifierType identifierType = ps.getPatientIdentifierTypeByName(OPENMRS_ID_NAME);
		if (identifierType == null) {
			throw new APIException("Could not find identifier type " + OPENMRS_ID_NAME);
		}
		
		PersonAttributeType demoAttrType = personService.getPersonAttributeTypeByName(DEMO_PATIENT_ATTR);
		if (demoAttrType == null) {
			demoAttrType = new PersonAttributeType();
			demoAttrType.setName(DEMO_PATIENT_ATTR);
			demoAttrType.setFormat("java.lang.Boolean");
			demoAttrType.setSearchable(true);
			demoAttrType = personService.savePersonAttributeType(demoAttrType);
		}
		
		Patient patient = new Patient();
		patient.setUuid(patientUuid);
		populatePerson(patient);
		applyDemographics(patient, demographics);
		
		Location rootLocation = Randomizer.randomListEntry(Context.getLocationService().getRootLocations(false));
		PatientIdentifier patientIdentifier = new PatientIdentifier();
		patientIdentifier.setIdentifier(iss.generateIdentifier(identifierType, "DemoData"));
		patientIdentifier.setIdentifierType(identifierType);
		patientIdentifier.setDateCreated(new Date());
		patientIdentifier.setLocation(rootLocation);
		patient.addIdentifier(patientIdentifier);
		
		PersonAttribute demoAttr = new PersonAttribute();
		demoAttr.setAttributeType(demoAttrType);
		demoAttr.setValue("true");
		patient.addAttribute(demoAttr);
		
		return ps.savePatient(patient);
	}
	
	private void applyConditions(Patient patient, List<ResolvedCondition> conditions) {
		for (ResolvedCondition rc : conditions) {
			conditionGenerator.createDatedCondition(patient, rc.concept, rc.onsetDate, rc.status);
		}
	}
	
	private void applyDemographics(Patient patient, JsonNode demographics) {
		String givenName = demographics.path("givenName").asText(null);
		String familyName = demographics.path("familyName").asText(null);
		String gender = demographics.path("gender").asText(null);
		
		if (StringUtils.isBlank(givenName) || StringUtils.isBlank(familyName) || StringUtils.isBlank(gender)) {
			throw new APIException("Fixture demographics must provide givenName, familyName, and gender");
		}
		
		JsonNode ageNode = demographics.path("ageYearsAgo");
		if (!ageNode.canConvertToInt()) {
			throw new APIException("Fixture demographics.ageYearsAgo must be an integer");
		}
		int ageYearsAgo = ageNode.asInt();
		
		PersonName personName = patient.getPersonName();
		if (personName == null) {
			personName = new PersonName();
			patient.addName(personName);
		}
		personName.setGivenName(givenName);
		personName.setFamilyName(familyName);
		
		patient.setGender(gender);
		patient.setBirthdate(toDate(LocalDate.now().minusYears(ageYearsAgo)));
		patient.setBirthdateEstimated(false);
	}
	
	private JsonNode readFixture(String classpathResourcePath) {
		String normalized = classpathResourcePath.startsWith("/") ? classpathResourcePath : "/" + classpathResourcePath;
		try (InputStream in = getClass().getResourceAsStream(normalized)) {
			if (in == null) {
				throw new APIException("Fixture resource not found on classpath: " + normalized);
			}
			return objectMapper.readTree(in);
		} catch (IOException e) {
			throw new APIException("Failed to read fixture: " + normalized, e);
		}
	}
}
