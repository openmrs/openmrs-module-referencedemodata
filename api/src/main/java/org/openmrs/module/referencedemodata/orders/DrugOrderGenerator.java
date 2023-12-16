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

import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.distinctByKey;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.openmrs.DrugOrder;
import org.openmrs.OrderType;
import org.openmrs.SimpleDosingInstructions;
import org.openmrs.api.context.Context;
import org.springframework.core.io.support.ResourcePatternResolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DrugOrderGenerator {
	
	/**
	 * Generates the drug order with the provided prescription value from the descriptor
	 *
	 * @param valueDescriptor The descriptor that describes how to generate values for this drug order
	 * @return A partial drug order with the prescription values filled in. Additional details need to be added before
	 *  the drug order can be saved.
	 */
	public static DrugOrder generateDrugOrder(DrugOrderDescriptor valueDescriptor) {
		DrugOrder drugOrder = new DrugOrder();
		drugOrder.setOrderType(getDrugOrderType());
		drugOrder.setDrug(valueDescriptor.getDrug());
		drugOrder.setDosingType(SimpleDosingInstructions.class);
		drugOrder.setDose(valueDescriptor.getDose());
		drugOrder.setDoseUnits(valueDescriptor.getDoseUnits());
		drugOrder.setFrequency(valueDescriptor.getFrequency());
		drugOrder.setRoute(valueDescriptor.getRoute());
		drugOrder.setNumRefills(valueDescriptor.getNumRefills());
		drugOrder.setQuantity(valueDescriptor.getQuantity());
		drugOrder.setQuantityUnits(valueDescriptor.getQuantityUnits());
		drugOrder.setOrderReasonNonCoded(valueDescriptor.getOrderReasonNonCoded());
		return drugOrder;
	}
	
	private static OrderType getDrugOrderType() {
		return Context.getOrderService().getOrderTypeByUuid(OrderType.DRUG_ORDER_TYPE_UUID);
	}
	
	public static DrugOrderDescriptor loadDrugOrderDescriptor(ResourcePatternResolver patternResolver,
			String resourcePattern) throws IOException {
		ObjectMapper om = new ObjectMapper();
		ObjectReader reader = om.readerFor(DrugOrderDescriptor.class);
		return Arrays.stream(patternResolver.getResources(resourcePattern))
				// not sure why, but each resource seems to be loaded twice
				// so we use the file basename as a unique key
				.filter(distinctByKey(r -> FilenameUtils.getBaseName(r.getFilename())))
				.map(r -> {
					try (InputStream is = r.getInputStream()) {
						if (is == null) {
							return Optional.<DrugOrderDescriptor>empty();
						}
						
						return Optional.<DrugOrderDescriptor>of(reader.readValue(is));
					}
					catch (IOException e) {
						log.warn("Exception caught while attempting to read [{}]", r.getFilename(), e);
						return Optional.<DrugOrderDescriptor>empty();
					}
				}).filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList()).get(0);
	}
}
