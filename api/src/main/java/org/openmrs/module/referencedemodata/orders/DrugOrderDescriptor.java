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

import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.OrderFrequency;

public final class DrugOrderDescriptor {

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

	public DrugOrderDescriptor setDrugConcept(Concept drugConcept) {
		this.drugConcept = drugConcept;
		return this;
	}

	public Drug getDrug() {
		return drug;
	}

	public DrugOrderDescriptor setDrug(Drug drug) {
		this.drug = drug;
		return this;
	}

	public String getDrugName() {
		return drugName;
	}

	public DrugOrderDescriptor setDrugName(String drugName) {
		this.drugName = drugName;
		return this;
	}

	public double getDoseValue() {
		return doseValue;
	}

	public DrugOrderDescriptor setDoseValue(double doseValue) {
		this.doseValue = doseValue;
		return this;
	}

	public Concept getDoseUnits() {
		return doseUnits;
	}

	public DrugOrderDescriptor setDoseUnits(Concept doseUnits) {
		this.doseUnits = doseUnits;
		return this;
	}

	public Concept getRoute() {
		return route;
	}

	public DrugOrderDescriptor setRoute(Concept route) {
		this.route = route;
		return this;
	}

	public OrderFrequency getFrequency() {
		return frequency;
	}

	public DrugOrderDescriptor setFrequency(OrderFrequency frequency) {
		this.frequency = frequency;
		return this;
	}

	public Concept getIndication() {
		return indication;
	}

	public DrugOrderDescriptor setIndication(Concept indication) {
		this.indication = indication;
		return this;
	}

	public Date getStartDate() {
		return startDate;
	}

	public DrugOrderDescriptor setStartDate(Date startDate) {
		this.startDate = startDate;
		return this;
	}

	public Date getAutoExpireDate() {
		return autoExpireDate;
	}

	public DrugOrderDescriptor setAutoExpireDate(Date autoExpireDate) {
		this.autoExpireDate = autoExpireDate;
		return this;
	}
}
