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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptSource;
import org.openmrs.GlobalProperty;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.api.APIException;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.ModuleActivator;
import org.openmrs.module.ModuleException;
import org.openmrs.module.emrapi.utils.MetadataUtil;
import org.openmrs.util.PrivilegeConstants;

/**
 * This class contains the logic that is run every time this module is either started or stopped.
 */
public class ReferenceDemoDataActivator extends BaseModuleActivator {
	
	protected Log log = LogFactory.getLog(getClass());
	
	/**
	 * @see ModuleActivator#contextRefreshed()
	 */
	public void contextRefreshed() {
		log.info("Reference Demo Data Module refreshed");
	}
	
	/**
	 * @see ModuleActivator#started()
	 * @should install the metadata package on startup
	 * @should link the admin account to unknown provider
	 */
	public void started() {
		installMDSPackages();
		//This should probably be removed once a test user is added to demo data
		//See https://tickets.openmrs.org/browse/RA-184
		linkAdminAccountToAProviderIfNecessary();
		configureConceptsIfNecessary();
		setRequiredGlobalProperties();
	}
	
	private void installMDSPackages() {
		try {
			MetadataUtil.setupStandardMetadata(getClass().getClassLoader());
		}
		catch (Exception e) {
			throw new ModuleException("Failed to load reference demo data MDS packages", e);
		}
		
		log.info("Reference Demo Data Module started");
	}
	
	private void linkAdminAccountToAProviderIfNecessary() {
		try {
			//If unknown provider isn't yet linked to admin, then do it
			Context.addProxyPrivilege(PrivilegeConstants.VIEW_PROVIDERS);
			Context.addProxyPrivilege(PrivilegeConstants.VIEW_PERSONS);
			Context.addProxyPrivilege(PrivilegeConstants.MANAGE_PROVIDERS);
			ProviderService ps = Context.getProviderService();
			Person adminPerson = Context.getPersonService().getPerson(1);
			Collection<Provider> possibleProvider = ps.getProvidersByPerson(adminPerson);
			if (possibleProvider.size() == 0) {
				List<Provider> providers = ps.getAllProviders(false);
				if (providers.size() == 0)
					throw new APIException("No un retired providers found in the system");
				
				Provider provider = providers.get(0);
				provider.setPerson(adminPerson);
				ps.saveProvider(provider);
			}
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.VIEW_PROVIDERS);
			Context.removeProxyPrivilege(PrivilegeConstants.VIEW_PERSONS);
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_PROVIDERS);
		}
	}
	
	private void configureConceptsIfNecessary() {
		try {
			Context.addProxyPrivilege(PrivilegeConstants.MANAGE_CONCEPTS);
			ConceptService cs = Context.getConceptService();
			ConceptMapType sameAsMapType = cs.getConceptMapTypeByUuid("35543629-7d8c-11e1-909d-c80aa9edcf4e");
			//Not bothering to check for null because i know demo data should have these
			Concept visitDiagnosisConcept = cs.getConceptByUuid("159947AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
			
			//diagnosis concept is required to be a set member of visit diagnosis concept
			Concept diagnosisConcept = cs.getConceptByUuid("226ed7ad-b776-4b99-966d-fd818d3302c2");
			if (visitDiagnosisConcept != null && !visitDiagnosisConcept.getSetMembers().contains(diagnosisConcept)) {
				visitDiagnosisConcept.addSetMember(diagnosisConcept);
				cs.saveConcept(visitDiagnosisConcept);
			}
			
			Map<String, String> conceptUuidCodeMap = new HashMap<String, String>();
			conceptUuidCodeMap.put("159947AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "Diagnosis Concept Set");
			conceptUuidCodeMap.put("161602AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "Non-Coded Diagnosis");
			conceptUuidCodeMap.put("159946AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "Diagnosis Order");
			conceptUuidCodeMap.put("159394AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "Diagnosis Certainty");
			
			ConceptSource source = cs.getConceptSourceByName("org.openmrs.module.emr");
			if (source != null) {
				for (Map.Entry<String, String> entry : conceptUuidCodeMap.entrySet()) {
					boolean hasMapping = cs.getConceptByMapping(entry.getValue(), source.getName(), false) != null;
					if (!hasMapping) {
						Concept c = cs.getConceptByUuid(entry.getKey());
						ConceptReferenceTerm term = new ConceptReferenceTerm(source, entry.getValue(), null);
						c.addConceptMapping(new ConceptMap(term, sameAsMapType));
						cs.saveConcept(c);
					}
				}
			}
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_CONCEPTS);
		}
	}
	
	private void setRequiredGlobalProperties() {
		AdministrationService as = Context.getAdministrationService();
		Map<String, String> propertyValueMap = new HashMap<String, String>();
		//Add more GPs here
		propertyValueMap.put("registrationcore.identifierSourceId", "1");
		
		for (Map.Entry<String, String> entry : propertyValueMap.entrySet()) {
			if (StringUtils.isBlank(as.getGlobalProperty(entry.getKey()))) {
				GlobalProperty gp = as.getGlobalPropertyObject(entry.getKey());
				if (gp == null) {
					gp = new GlobalProperty();
					gp.setProperty(entry.getKey());
				}
				gp.setPropertyValue(entry.getValue());
				as.saveGlobalProperty(gp);
			}
		}
	}
}
