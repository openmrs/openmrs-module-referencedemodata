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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class Randomizer {
	
	private static final Random CONST_RANDOM = new Random(0);
	
	public static double randomGaussian() {
		return CONST_RANDOM.nextGaussian();
	}
	
	public static int randomBetween(int min, int max) {
		if (min > max) {
			int tmp = min;
			min = max;
			max = tmp;
		}
		
		if (min == max) {
			return min;
		}
		
		return CONST_RANDOM.nextInt(max - min) + min;
	}
	
	public static long randomLongBetween(long min, long max) {
		if (min > max) {
			long tmp = min;
			min = max;
			max = tmp;
		}
		
		if (min == max) {
			return min;
		}
		
		return nextLong(max - min) + min;
	}
	
	public static double randomDoubleBetween(double min, double max) {
		if (min > max) {
			double tmp = min;
			min = max;
			max = tmp;
		}
		
		if (min == max) {
			return min;
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
	
	/**
	 * Returns a random sublist consisting of <tt>n</tt> elements from the supplied population
	 * <p/>
	 * Note that {@code n} must be {@code <= population.size()}
	 * <p/>
	 * If {@code n == population.size()}, this is equivalent to {@link Collections#shuffle(List)}, except that we do not
	 * alter the population list.
	 *
	 * @param population the total set of things from which to select
	 * @param n          the number of things to select from the set
	 * @return a {@link List}  consisting of {@code n} sub-items of {@code population}
	 */
	public static <T> T[] randomSubArray(T[] population, int n) {
		int size = population.length;
		
		if (n > size) {
			throw new IllegalArgumentException(
					"n must be smaller than the size of the list, but '" + n + "' is greater than '" + size + "'");
		}
		
		if (size == 0) {
			return null;
		}
		
		List<T> localPopulation = Arrays.asList(population);
		Collections.shuffle(localPopulation, CONST_RANDOM);
		
		return localPopulation.subList(0, n).toArray(Arrays.copyOf(population, n));
	}
	
	public static <T> T randomListEntry(List<T> list) {
		if (list.size() == 0) {
			return null;
		}
		
		return list.get(randomArrayIndex(list.size()));
	}
	
	/**
	 * Returns a random sublist consisting of <tt>n</tt> elements from the supplied population
	 * <p/>
	 * Note that {@code n} must be {@code <= population.size()}
	 * <p/>
	 * If {@code n == population.size()}, this is equivalent to {@link Collections#shuffle(List)}, except that we do not
	 * alter the population list.
	 *
	 * @param population the total set of things from which to select
	 * @param n          the number of things to select from the set
	 * @return a {@link List}  consisting of {@code n} sub-items of {@code population}
	 */
	public static <T> List<T> randomSubList(List<T> population, int n) {
		int size = population.size();
		
		if (n > size) {
			throw new IllegalArgumentException(
					"n must be smaller than the size of the list, but '" + n + "' is greater than '" + size + "'");
		}
		
		if (size == 0) {
			return null;
		}
		
		List<T> localPopulation = new ArrayList<>(size);
		localPopulation.addAll(population);
		Collections.shuffle(localPopulation, CONST_RANDOM);
		
		return localPopulation.subList(0, n - 1);
	}
	
	public static String randomSuffix() {
		return randomSuffix(4);
	}
	
	public static String randomSuffix(int digits) {
		return CONST_RANDOM.ints(digits, 0, 10).mapToObj(String::valueOf).collect(Collectors.joining());
	}
	
	public static boolean shouldRandomEventOccur(double percentChance) {
		return randomDoubleBetween(0, 1) <= percentChance;
	}
	
	// brazenly stolen from https://stackoverflow.com/a/32906415
	private static long nextLong(long bound) {
		if (bound <= 0) {
			throw new IllegalArgumentException("bound must be positive");
		}
		
		long r = CONST_RANDOM.nextLong() & Long.MAX_VALUE;
		long m = bound - 1L;
		if ((bound & m) == 0) {  // i.e., bound is a power of 2
			r = (bound * r) >> (Long.SIZE - 1);
		} else {
			for (long u = r; u - (r = u % bound) + m < 0L; u = CONST_RANDOM.nextLong() & Long.MAX_VALUE);
		}
		return r;
	}
}
