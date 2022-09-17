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

import org.apache.commons.lang3.Range;
import org.openmrs.ConceptNumeric;
import org.openmrs.Obs;
import org.openmrs.module.referencedemodata.Randomizer;

public class ObsValueGenerator {
	
	/**
	 * Generates the "next" numeric value for an observation based on the provided descriptor and previous value
	 *
	 * @param valueDescriptor The descriptor that describes how to generate values for this obs
	 * @param previousValue The previous numeric value or null if this is the first obs in the series
	 * @return A partial obs with the concept and next value filled in. Additional details need to be added before
	 *  the obs can be saved.
	 */
	public static Obs createObsWithNumericValue(NumericObsValueDescriptor valueDescriptor, Number previousValue) {
		Number newValue;
		if (previousValue == null) {
			newValue = generateInitialValue(valueDescriptor);
		} else {
			newValue = generateNextValue(valueDescriptor, previousValue);
		}
		
		ConceptNumeric concept = valueDescriptor.getConcept();
		
		if (concept.getHiAbsolute() != null && newValue.doubleValue() > concept.getHiAbsolute()) {
			newValue = concept.getHiAbsolute();
		}
		
		if (concept.getLowAbsolute() != null && newValue.doubleValue() < concept.getLowAbsolute()) {
			newValue = concept.getLowAbsolute();
		}
		
		Obs result = new Obs();
		result.setConcept(valueDescriptor.getConcept());
		result.setValueNumeric(newValue.doubleValue());
		return result;
	}
	
	private static Number generateInitialValue(NumericObsValueDescriptor valueDescriptor) {
		Range<Double> range = valueDescriptor.getInitialValue();
		double initialValue =  Randomizer.randomDoubleBetween(range.getMinimum(), range.getMaximum());
		
		if (valueDescriptor.getPrecision() == NumericObsValueDescriptor.Precision.INTEGER) {
			return (long) initialValue;
		}
		
		// round to one decimal place
		return Math.round(initialValue * 10d) / 10d;
	}
	
	private static Number generateNextValue(NumericObsValueDescriptor valueDescriptor, Number previousValue) {
		if (valueDescriptor.getDecayType() == NumericObsValueDescriptor.DecayType.CONSTANT) {
			return previousValue;
		}
		
		if (valueDescriptor.getPrecision() == NumericObsValueDescriptor.Precision.INTEGER) {
			long delta = generateNextLongDelta(valueDescriptor);
			return applyLongDelta(valueDescriptor.getDecayType(), previousValue.longValue(), delta);
		} else {
			double delta = generateNextDoubleDelta(valueDescriptor);
			return applyDoubleDelta(valueDescriptor.getDecayType(), previousValue.doubleValue(), delta);
		}
	}
	
	private static long generateNextLongDelta(NumericObsValueDescriptor valueDescriptor) {
		return (long) (valueDescriptor.getTrend() + valueDescriptor.getStandardDeviation() * Randomizer.randomGaussian());
	}
	
	private static double generateNextDoubleDelta(NumericObsValueDescriptor valueDescriptor) {
		return valueDescriptor.getTrend() + valueDescriptor.getStandardDeviation() * Randomizer.randomGaussian();
	}
	
	private static long applyLongDelta(NumericObsValueDescriptor.DecayType decayType, long longValue, long delta) {
		switch (decayType) {
			case LINEAR:
				return longValue + delta;
			case EXPONENTIAL:
				return longValue * delta;
		}
		
		throw new IllegalStateException("Unknown DecayType [" + decayType + "]");
	}
	
	private static double applyDoubleDelta(NumericObsValueDescriptor.DecayType decayType, double doubleValue, double delta) {
		double newValue;
		switch (decayType) {
			case LINEAR:
				newValue = doubleValue + delta;
				break;
			case EXPONENTIAL:
				newValue = doubleValue * delta;
				break;
			default:
				throw new IllegalStateException("Unknown DecayType [" + decayType + "]");
		}
		
		// round to one decimal place
		return Math.round(newValue * 10d) / 10d;
	}
	
}
