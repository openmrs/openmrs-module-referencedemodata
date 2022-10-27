/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.obs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.Range;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptNumeric;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.APIException;
import org.openmrs.api.ObsService;
import org.openmrs.api.context.Context;
import org.openmrs.module.ModuleFactory;
import org.openmrs.module.referencedemodata.DemoDataConceptCache;
import org.openmrs.module.referencedemodata.obs.NumericObsValueDescriptor.DecayType;
import org.openmrs.module.referencedemodata.obs.NumericObsValueDescriptor.Precision;
import org.openmrs.util.OpenmrsClassLoader;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.openmrs.module.referencedemodata.Randomizer.randomArrayEntry;
import static org.openmrs.module.referencedemodata.Randomizer.randomListEntry;
import static org.openmrs.module.referencedemodata.Randomizer.shouldRandomEventOccur;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataActivator.MODULE_ID;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.distinctByKey;

@Slf4j
public class DemoObsGenerator {
	
	private final DemoDataConceptCache conceptCache;
	
	private final ResourcePatternResolver patternResolver;
	
	private List<NumericObsValueDescriptor> vitalsDescriptors = null;
	
	private List<NumericObsValueDescriptor> labDescriptors = null;
	
	private ObsService os = null;
	
	private final String[] LAB_RESULT_TEXTS = {
			"Greenish-yellow matter detected.",
			"Appearance of bright red deposits against a blue background.",
			"Separate parasites detected from sample."
	};
	
	public DemoObsGenerator(DemoDataConceptCache conceptCache) {
		this.conceptCache = conceptCache;
		
		ClassLoader cl = null;
		try {
			cl = ModuleFactory.getModuleClassLoader(MODULE_ID);
		}
		catch (NullPointerException ignored) {
		
		}
		
		if (cl == null) {
			cl = OpenmrsClassLoader.getInstance();
		}
		
		patternResolver = new PathMatchingResourcePatternResolver(new DefaultResourceLoader(cl));
	}
	
	public void createDemoVitalsObs(Patient patient, Encounter encounter, Location location) {
		for (NumericObsValueDescriptor vitalsDescriptor : getVitalsDescriptors()) {
			createNumericObsFromDescriptor(vitalsDescriptor, patient, encounter, encounter.getEncounterDatetime(), location);
		}
	}
	
	public void createDemoLabObs(Patient patient, Encounter encounter, Location location) {
		Concept c = null;
		if (shouldRandomEventOccur(.6666)) {
			c = randomListEntry(conceptCache.getConceptsByClass("LabSet"));
		} else {
			c = randomListEntry(conceptCache.getConceptsByClass("Test"));
		}
		if (c.getConceptClass() != null && "LabSet".equalsIgnoreCase(c.getConceptClass().getName()) && c.getSet()) {
			List<Obs> obsPanel = new ArrayList<>(c.getSetMembers().size());
			for (Concept member : c.getSetMembers()) {
				obsPanel.add(createDemoLabObs(patient, null, encounter.getEncounterDatetime(), location, member));
			}
			createObsGroup(c.getUuid(), patient, encounter, encounter.getEncounterDatetime(), location, obsPanel);
		} else {
			createDemoLabObs(patient, encounter, encounter.getEncounterDatetime(), location, c);
		}
		
	}
	
	public void createTextObs(String conceptDescriptor, String text, Patient patient, Encounter encounter,
			Date encounterTime,
			Location location) {
		Obs obs = createBasicObs(conceptDescriptor, patient, encounterTime, location);
		obs.setValueText(text);
		if (encounter != null) {
			encounter.addObs(obs);
		}
		getObsService().saveObs(obs, null);
	}
	
	public Obs createObsGroup(String conceptDescriptor, Patient patient, Encounter encounter, Date encounterTime,
			Location location, List<Obs> childObs) {
		Obs parentOb = createBasicObs(conceptDescriptor, patient, encounterTime, location);
		for (Obs childOb : childObs) {
			parentOb.addGroupMember(childOb);
		}
		encounter.addObs(parentOb);
		return getObsService().saveObs(parentOb, "Adding obs to obs-group");
	}
	
	public Obs createCodedObs(String conceptDescriptor, String conceptAnswerDescriptor, Patient patient, Encounter encounter,
			Date encounterTime, Location location) {
		return createCodedObs(conceptDescriptor, conceptCache.findConcept(conceptAnswerDescriptor), patient, encounter,
				encounterTime, location);
	}
	
	protected void createNumericObsFromDescriptor(NumericObsValueDescriptor descriptor, Patient patient, Encounter encounter,
			Date encounterDateTime, Location location) {
		Obs previousObs = null;
		try {
			List<Obs> potentialObs = getMostRecentObs(patient, encounter, descriptor.getConcept(), 1);
			if (potentialObs.size() > 0) {
				previousObs = potentialObs.get(0);
			}
		}
		catch (Exception ignored) {
		
		}
		
		Obs partialObs = ObsValueGenerator.createObsWithNumericValue(descriptor,
				previousObs != null ? previousObs.getValueNumeric() : null);
		createObs(partialObs, patient, encounter, encounterDateTime, location);
	}
	
	protected Obs createCodedObs(String conceptDescriptor, Concept concept, Patient patient, Encounter encounter,
			Date encounterTime, Location location) {
		
		Obs obs = createBasicObs(conceptDescriptor, patient, encounterTime, location);
		obs.setValueCoded(concept);
		if (encounter != null) {
			encounter.addObs(obs);
		}
		
		return obs;
	}
	
	protected Obs createBasicObs(String conceptDescriptor, Patient patient, Date encounterTime, Location location) {
		Concept concept = conceptCache.findConcept(conceptDescriptor);
		if (concept == null) {
			log.warn("incorrect concept identifier? [{}]", conceptDescriptor);
		}
		
		return new Obs(patient, concept, encounterTime, location);
	}
	
	protected void createObs(Obs partialObs, Patient patient, Encounter encounter, Date encounterTime, Location location) {
		if (encounter != null) {
			encounter.addObs(partialObs);
		}
		partialObs.setPerson(patient);
		partialObs.setObsDatetime(encounterTime);
		partialObs.setLocation(location);
		
		getObsService().saveObs(partialObs, null);
	}
	
	private List<NumericObsValueDescriptor> getVitalsDescriptors() {
		if (vitalsDescriptors == null) {
			try {
				vitalsDescriptors = loadVitalsDescriptors(patternResolver);
			}
			catch (IOException e) {
				throw new APIException(e);
			}
		}
		
		return vitalsDescriptors;
	}
	
	private List<NumericObsValueDescriptor> getLabDescriptors() {
		if (labDescriptors == null) {
			try {
				labDescriptors = loadLabDescriptors(patternResolver);
			}
			catch (IOException e) {
				throw new APIException(e);
			}
		}
		
		return labDescriptors;
	}
	
	private List<NumericObsValueDescriptor> loadVitalsDescriptors(ResourcePatternResolver patternResolver)
			throws IOException {
		return loadObsValueDescriptorsFor(patternResolver, "classpath*:vitalsValueDescriptors/*.json");
	}
	
	private List<NumericObsValueDescriptor> loadLabDescriptors(ResourcePatternResolver patternResolver)
			throws IOException {
		return loadObsValueDescriptorsFor(patternResolver, "classpath*:labValueDescriptors/*.json");
	}
	
	private List<NumericObsValueDescriptor> loadObsValueDescriptorsFor(ResourcePatternResolver patternResolver,
			String resourcePattern) throws IOException {
		ObjectMapper om = new ObjectMapper();
		ObjectReader reader = om.readerFor(NumericObsValueDescriptor.class);
		return Arrays.stream(patternResolver.getResources(resourcePattern))
				// not sure why, but each resource seems to be loaded twice
				// so we use the file basename as a unique key
				.filter(distinctByKey(r -> FilenameUtils.getBaseName(r.getFilename())))
				.map(r -> {
					try (InputStream is = r.getInputStream()) {
						if (is == null) {
							return Optional.<NumericObsValueDescriptor>empty();
						}
						
						return Optional.<NumericObsValueDescriptor>of(reader.readValue(is));
					}
					catch (IOException e) {
						log.warn("Exception caught while attempting to read [{}]", r.getFilename(), e);
						return Optional.<NumericObsValueDescriptor>empty();
					}
				}).filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());
	}
	
	private ObsService getObsService() {
		if (os == null) {
			os = Context.getObsService();
		}
		
		return os;
	}
	
	private List<Obs> getMostRecentObs(Patient patient, Encounter encounter, Concept questionConcept, Integer mostRecentN) {
		List<Obs> potentialObs = getObsService().getObservations(
				Collections.singletonList(patient),
				encounter != null ? Collections.singletonList(encounter) : null,
				Collections.singletonList(questionConcept),
				null,
				null,
				null,
				null,
				mostRecentN,
				null,
				null,
				null,
				false
		);
		return potentialObs;
	};
	
	private Obs createDemoLabObs(Patient patient, Encounter encounter, Date encounterDateTime, Location location, Concept concept) {
		Obs obs = null;
		// Free text
		if (concept.getDatatype().isText()) {
			createTextObs(concept.getUuid(), randomArrayEntry(LAB_RESULT_TEXTS), patient, encounter, encounterDateTime, location);
			List<Obs> obsList = getMostRecentObs(patient, encounter, concept, 1);
			obs = obsList.get(0);
		}
		// Coded values
		else if (concept.getDatatype().isCoded()) {
			if (!concept.getAnswers().isEmpty()) {
				obs = createCodedObs(concept.getUuid(), (randomListEntry(concept.getAnswers().stream().collect(Collectors.toList()))).getConcept(), patient, encounter, encounterDateTime, location);
				
			} 
			else {
				List<Concept> findingConcepts = conceptCache.getConceptsByClass("Finding");
				if (findingConcepts != null) {
					obs = createCodedObs(concept.getUuid(), ((Concept)randomListEntry(findingConcepts)).getUuid(), patient, encounter, encounterDateTime, location);

				}
			}
		}
		// Numeric values
		else if (concept.getDatatype().isNumeric()) {
			List<NumericObsValueDescriptor> labDescriptor = getLabDescriptors().stream().filter(ld -> ld.getConcept().getUuid() == concept.getUuid()).collect(Collectors.toList());
			if (labDescriptor.isEmpty()) {
				// use default NumericObsValueDescriptor if none is defined
				NumericObsValueDescriptor novd = new NumericObsValueDescriptor();
				novd.setConcept(Context.getConceptService().getConceptNumeric(concept.getId()));
				novd.setDecayType(DecayType.LINEAR);
				novd.setInitialValue(Range.between(0.0, 150.0));
				novd.setPrecision(Precision.INTEGER);
				novd.setStandardDeviation(0.4);
				novd.setTrend(0);
				labDescriptor.add(novd);
			}
			createNumericObsFromDescriptor(labDescriptor.get(0), patient, encounter, encounterDateTime, location);
			List<Obs> obsList = getMostRecentObs(patient, encounter, concept, 1);
			obs =  obsList.get(0);
		}
		// Any other datatype, may never happen.
		else {
			obs = createCodedObs(concept.getUuid(), concept, patient, encounter, encounterDateTime, location);
		}
		return obs;
	}
}
