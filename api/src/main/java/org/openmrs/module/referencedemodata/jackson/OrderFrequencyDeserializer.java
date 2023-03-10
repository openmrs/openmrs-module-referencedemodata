/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.OrderFrequency;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;

public class OrderFrequencyDeserializer extends StdDeserializer<OrderFrequency> {
	
	public OrderFrequencyDeserializer() {
		super(OrderFrequency.class);
	}
	
	@Override
	public OrderFrequency deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String uuid = p.getText();
		
		if (StringUtils.isBlank(uuid)) {
			throw new IOException(p.getCurrentName() + " cannot be blank");
		}
		
		OrderService os = Context.getOrderService();
		OrderFrequency orderFrequency = os.getOrderFrequencyByUuid(uuid);
		
		if (orderFrequency == null) {
			throw new IOException("Order frequency [" + uuid + "] does not exist. Please create it.");
		}
		
		return orderFrequency;
	}
}
