/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.orders;

import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Provider;
import org.openmrs.SimpleDosingInstructions;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.referencedemodata.obs.DemoObsGenerator;
import org.openmrs.module.referencedemodata.obs.NumericObsValueDescriptor;
import org.springframework.core.io.support.ResourcePatternResolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.FilenameUtils;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;

import static org.openmrs.module.referencedemodata.Randomizer.shouldRandomEventOccur;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.distinctByKey;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toLocalDateTime;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;

public class DemoOrderGenerator {
	
	private OrderService os = null;
	
	private OrderType testOrderType = null;
	
	private CareSetting careSetting = null;
	
	private FhirTaskService fhirTaskService = null;
	
	public void createDemoTestOrder(Encounter encounter, Concept orderConcept) {
		OrderType orderType = getTestOrderType();
		
		TestOrder order = new TestOrder();
		order.setAction(Order.Action.NEW);
		order.setUrgency(shouldRandomEventOccur(.8) ? Order.Urgency.ROUTINE : Order.Urgency.STAT);
		order.setPatient(encounter.getPatient());
		order.setOrderType(orderType);
		order.setConcept(orderConcept);
		// TODO should care setting be somewhat randomised?
		order.setCareSetting(getCareSetting());
		order.setDateActivated(toDate(toLocalDateTime(encounter.getEncounterDatetime())));
		
		Provider provider = encounter.getActiveEncounterProviders().iterator().next().getProvider();
		order.setOrderer(provider);

		encounter.addOrder(order);
		getOrderService().saveOrder(order, null);
		
		Reference reference = new Reference();
		reference.setReference(order.getUuid());
		reference.setType(FhirConstants.SERVICE_REQUEST);
		
		Task orderTask = new Task();
		orderTask.setIntent(Task.TaskIntent.ORDER);
		orderTask.setStatus(Task.TaskStatus.COMPLETED);
		orderTask.addBasedOn(reference);
		
		getTaskTranslator().create(orderTask);
	}
	
	public void createDemoDrugOrder(Encounter encounter, DrugOrderDescriptor drugOrderDescriptor) {
		DrugOrder order = DrugOrderGenerator.generateDrugOrder(drugOrderDescriptor);
		order.setAction(Order.Action.NEW);
		order.setUrgency(Order.Urgency.ROUTINE);
		order.setPatient(encounter.getPatient());
		order.setCareSetting(getCareSetting());
		order.setDateActivated(toDate(toLocalDateTime(encounter.getEncounterDatetime())));
		
		Provider provider = encounter.getActiveEncounterProviders().iterator().next().getProvider();
		order.setOrderer(provider);
		order.setEncounter(encounter);
		getOrderService().saveOrder(order, null);
	}
	
	protected CareSetting getCareSetting() {
		if (careSetting == null) {
			careSetting = getOrderService().getCareSettingByName("Outpatient");
		}
		
		return careSetting;
	}
	
	protected OrderType getTestOrderType() {
		if (testOrderType == null) {
			testOrderType = getOrderService().getOrderTypeByUuid(OrderType.TEST_ORDER_TYPE_UUID);
		}
		
		return testOrderType;
	}
	
	
	protected OrderService getOrderService() {
		if (os == null) {
			os = Context.getOrderService();
		}
		
		return os;
	}
	
	protected FhirTaskService getTaskTranslator() {
		if (fhirTaskService == null) {
			fhirTaskService = Context.getRegisteredComponents(FhirTaskService.class).get(0);
		}
		
		return fhirTaskService;
	}
}
