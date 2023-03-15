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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;
import org.openmrs.Concept;
import org.openmrs.DosingInstructions;
import org.openmrs.Drug;
import org.openmrs.OrderFrequency;
import org.openmrs.module.referencedemodata.jackson.ConceptDeserializer;
import org.openmrs.module.referencedemodata.jackson.ConceptSerializer;
import org.openmrs.module.referencedemodata.jackson.DrugDeserializer;
import org.openmrs.module.referencedemodata.jackson.DrugSerializer;
import org.openmrs.module.referencedemodata.jackson.OrderFrequencyDeserializer;
import org.openmrs.module.referencedemodata.jackson.OrderFrequencySerializer;

@Getter
@Setter
public class DrugOrderDescriptor {
	
	@JsonProperty(required = true)
	@JsonSerialize(using = DrugSerializer.class)
	@JsonDeserialize(using = DrugDeserializer.class)
	private Drug drug;
	
	private double dose;
	
	@JsonProperty(required = true)
	@JsonSerialize(using = ConceptSerializer.class)
	@JsonDeserialize(using = ConceptDeserializer.class)
	private Concept doseUnits;
	
	@JsonProperty(required = true)
	@JsonSerialize(using = OrderFrequencySerializer.class)
	@JsonDeserialize(using = OrderFrequencyDeserializer.class)
	private OrderFrequency frequency;
	
	@JsonProperty(required = true)
	@JsonSerialize(using = ConceptSerializer.class)
	@JsonDeserialize(using = ConceptDeserializer.class)
	private Concept route;
	
	private Integer numRefills;
	
	private double quantity;
	
	@JsonProperty(required = true)
	@JsonSerialize(using = ConceptSerializer.class)
	@JsonDeserialize(using = ConceptDeserializer.class)
	private Concept quantityUnits;
	
	private String orderReasonNonCoded;
}
