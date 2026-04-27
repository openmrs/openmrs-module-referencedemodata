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

import java.util.Date;

import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.OrderFrequency;
import org.openmrs.OrderType;
import org.openmrs.Provider;
import org.openmrs.SimpleDosingInstructions;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;

import static org.openmrs.module.referencedemodata.Randomizer.shouldRandomEventOccur;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toLocalDateTime;

public class DemoOrderGenerator {

	private OrderService os = null;

	private OrderType testOrderType = null;

	private OrderType drugOrderType = null;

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

	public DrugOrder createDatedDrugOrder(Encounter encounter, DrugOrderSpec spec) {
		DrugOrder order = new DrugOrder();
		order.setAction(Order.Action.NEW);
		order.setUrgency(Order.Urgency.ROUTINE);
		order.setPatient(encounter.getPatient());
		order.setEncounter(encounter);
		order.setOrderType(getDrugOrderType());
		order.setCareSetting(getCareSetting());
		order.setConcept(spec.getDrugConcept());
		order.setDateActivated(spec.getStartDate());
		if (spec.getAutoExpireDate() != null) {
			order.setAutoExpireDate(spec.getAutoExpireDate());
		}
		order.setDosingType(SimpleDosingInstructions.class);
		order.setAsNeeded(Boolean.FALSE);
		order.setDose(spec.getDoseValue());
		order.setDoseUnits(spec.getDoseUnits());
		order.setRoute(spec.getRoute());
		order.setFrequency(spec.getFrequency());
		// DrugOrderValidator requires quantity/quantityUnits/numRefills even though the fixture has
		// no real dispense data — use placeholder defaults to satisfy the validator.
		order.setQuantity(30.0);
		order.setQuantityUnits(spec.getDoseUnits());
		order.setNumRefills(0);
		if (spec.getDrug() != null) {
			order.setDrug(spec.getDrug());
		} else {
			order.setDrugNonCoded(spec.getDrugName());
		}
		if (spec.getIndication() != null) {
			order.setOrderReason(spec.getIndication());
		}

		Provider provider = encounter.getActiveEncounterProviders().iterator().next().getProvider();
		order.setOrderer(provider);

		encounter.addOrder(order);
		getOrderService().saveOrder(order, null);
		return order;
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

	protected OrderType getDrugOrderType() {
		if (drugOrderType == null) {
			drugOrderType = getOrderService().getOrderTypeByUuid(OrderType.DRUG_ORDER_TYPE_UUID);
		}

		return drugOrderType;
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

	public static final class DrugOrderSpec {

		private Concept drugConcept;

		private Drug drug;

		private String drugName;

		private double doseValue;

		private Concept doseUnits;

		private Concept route;

		private OrderFrequency frequency;

		private Concept indication;

		private Date startDate;

		private Date autoExpireDate;

		public Concept getDrugConcept() {
			return drugConcept;
		}

		public DrugOrderSpec setDrugConcept(Concept drugConcept) {
			this.drugConcept = drugConcept;
			return this;
		}

		public Drug getDrug() {
			return drug;
		}

		public DrugOrderSpec setDrug(Drug drug) {
			this.drug = drug;
			return this;
		}

		public String getDrugName() {
			return drugName;
		}

		public DrugOrderSpec setDrugName(String drugName) {
			this.drugName = drugName;
			return this;
		}

		public double getDoseValue() {
			return doseValue;
		}

		public DrugOrderSpec setDoseValue(double doseValue) {
			this.doseValue = doseValue;
			return this;
		}

		public Concept getDoseUnits() {
			return doseUnits;
		}

		public DrugOrderSpec setDoseUnits(Concept doseUnits) {
			this.doseUnits = doseUnits;
			return this;
		}

		public Concept getRoute() {
			return route;
		}

		public DrugOrderSpec setRoute(Concept route) {
			this.route = route;
			return this;
		}

		public OrderFrequency getFrequency() {
			return frequency;
		}

		public DrugOrderSpec setFrequency(OrderFrequency frequency) {
			this.frequency = frequency;
			return this;
		}

		public Concept getIndication() {
			return indication;
		}

		public DrugOrderSpec setIndication(Concept indication) {
			this.indication = indication;
			return this;
		}

		public Date getStartDate() {
			return startDate;
		}

		public DrugOrderSpec setStartDate(Date startDate) {
			this.startDate = startDate;
			return this;
		}

		public Date getAutoExpireDate() {
			return autoExpireDate;
		}

		public DrugOrderSpec setAutoExpireDate(Date autoExpireDate) {
			this.autoExpireDate = autoExpireDate;
			return this;
		}
	}
}
