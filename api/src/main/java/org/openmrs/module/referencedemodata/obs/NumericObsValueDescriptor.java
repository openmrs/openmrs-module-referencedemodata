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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.Range;
import org.openmrs.Concept;
import org.openmrs.module.referencedemodata.jackson.ConceptDeserializer;
import org.openmrs.module.referencedemodata.jackson.ConceptSerializer;
import org.openmrs.module.referencedemodata.jackson.RangeDeserializer;
import org.openmrs.module.referencedemodata.jackson.RangeSerializer;

@Getter
@Setter
public class NumericObsValueDescriptor {
	
	public enum DecayType {
		@JsonProperty("exponential")
		EXPONENTIAL,
		@JsonProperty("linear")
		LINEAR,
		@JsonProperty("constant")
		CONSTANT
	}
	
	public enum Precision {
		@JsonProperty("integer")
		INTEGER,
		@JsonProperty("float")
		FLOAT
	}
	
	@JsonProperty(required = true)
	@JsonSerialize(using = RangeSerializer.class)
	@JsonDeserialize(using = RangeDeserializer.class)
	private Range<Double> initialValue;
	
	@JsonProperty(required = true)
	@JsonSerialize(using = ConceptSerializer.class)
	@JsonDeserialize(using = ConceptDeserializer.class)
	private Concept concept;
	
	private double trend;
	
	private double standardDeviation;
	
	@JsonProperty(required = true)
	private DecayType decayType;
	
	@JsonProperty(required = true)
	private Precision precision;
	
}
