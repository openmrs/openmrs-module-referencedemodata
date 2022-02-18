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
import java.util.Random;
import java.util.stream.Collectors;

public class Randomizer {
	
	private static final Random CONST_RANDOM = new Random(0);
	
	public static double randomGaussian() {
		return CONST_RANDOM.nextGaussian();
	}
	
	public static int randomBetween(int min, int max) {
		return CONST_RANDOM.nextInt(max - min) + min;
	}
	
	public static double randomDoubleBetween(double min, double max) {
		if (min > max) {
			double tmp = min;
			min = max;
			max = tmp;
		}
		
		return (CONST_RANDOM.nextDouble() * (max - min)) + min;
	}
	
	public static int randomArrayIndex(int length) {
		return randomBetween(0, length);
	}
	
	public static <T> int randomArrayIndex(T[] array) {
		return randomArrayIndex(array.length);
	}
	
	public static <T> T randomArrayEntry(T[] array) {
		if (array.length == 0) {
			return null;
		}
		
		return array[randomArrayIndex(array)];
	}
	
	public static <T> T randomArrayEntry(List<T> list) {
		if (list.size() == 0) {
			return null;
		}
		
		return list.get(randomArrayIndex(list.size()));
	}
	
	public static String randomSuffix() {
		return randomSuffix(4);
	}
	
	public static String randomSuffix(int digits) {
		// Last n digits of the current time.
		return CONST_RANDOM.ints(digits, 0, 10).mapToObj(String::valueOf).collect(Collectors.joining());
	}
	
	public static boolean flipACoin() {
		return randomBetween(0, 1) == 0;
	}
	
}
