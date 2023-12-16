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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.openmrs.OrderFrequency;

public class OrderFrequencySerializer extends StdSerializer<OrderFrequency> {
	
	protected OrderFrequencySerializer() {
		super(OrderFrequency.class);
	}
	
	@Override
	public void serialize(OrderFrequency value, JsonGenerator gen, SerializerProvider provider) throws IOException {
		gen.writeString(value.getUuid());
	}
}
