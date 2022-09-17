/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata;

import java.util.List;
import java.util.Map;

import lombok.AccessLevel;
import lombok.Getter;
import org.apache.commons.collections4.map.DefaultedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.openmrs.Concept;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;

/**
 * This is basically a temporary cache to store any concept-related metadata once it's been loaded once.
 * <p/>
 * This supports concepts specified by id, UUID, or concept reference.
 * <p/>
 * Note that by necessity, this will be a slower mechanism to use to get concepts by primary key than just using the Hibernate
 * Session itself as a cache. The real strength is that we can use a single method regardless of how the concept is to be cached.
 * <p/>
 * Nothing is done to ensure that the same concept isn't loaded multiple times using different identifiers. I.e., if CIEL:1066
 * is cached asking for 1066AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA will still do a look-up.
 */
@Getter(AccessLevel.PROTECTED)
public class DemoDataConceptCache {
	
	private ConceptService cs;
	
	private final Map<String, Concept> conceptCache = new DefaultedMap<>((key) -> {
		ConceptService cs = getConceptService();
		Concept concept = null;
		
		if (key.contains(":")) {
			String[] map = key.split(":", 2);
			if (map.length == 2 && StringUtils.isNotBlank(map[0]) && StringUtils.isNotBlank(map[1])) {
				try {
					concept = cs.getConceptByMapping(map[1], map[0]);
				}
				catch (Exception ignored) {
				
				}
			}
		}
		
		if (concept == null && key.length() >= 36 && key.length() <= 38) {
			try {
				concept = cs.getConceptByUuid(key);
			}
			catch (Exception ignored) {
			
			}
		}
		
		if (concept == null) {
			int conceptId = NumberUtils.toInt(key, -1);
			if (conceptId > 0) {
				try {
					concept = cs.getConcept(conceptId);
				}
				catch (Exception ignored) {
				
				}
			}
		}
		
		if (concept == null) {
			try {
				concept = cs.getConceptByName(key);
			}
			catch (Exception ignored) {
			
			}
		}
		
		if (concept == null) {
			throw new APIException("Could not find concept [" + key + "]");
		}
		
		return concept;
	});
	
	private final Map<String, List<Concept>> conceptClassCache = new DefaultedMap<>((key) -> {
		ConceptService conceptService = getConceptService();
		return conceptService.getConceptsByClass(conceptService.getConceptClassByName(key));
	});
	
	public Concept findConcept(String conceptDescriptor) {
		return getConceptCache().get(conceptDescriptor);
	}
	
	public List<Concept> getConceptsByClass(String className) {
		return getConceptClassCache().get(className);
	}
	
	private ConceptService getConceptService() {
		if (cs == null) {
			cs = Context.getConceptService();
		}
		
		return cs;
	}
}
