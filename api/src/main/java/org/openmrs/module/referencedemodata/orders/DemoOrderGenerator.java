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
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Provider;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;

import static org.openmrs.module.referencedemodata.Randomizer.shouldRandomEventOccur;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toLocalDateTime;

public class DemoOrderGenerator {
	
	private OrderService os = null;
	
	private OrderType testOrderType = null;
	
	private CareSetting careSetting = null;
	
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
	
}
