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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.type.SimpleType;
import org.apache.commons.lang3.Range;

public class RangeDeserializer extends StdDeserializer<Range<Double>> {
	
	public RangeDeserializer() {
		super(SimpleType.constructUnsafe(Range.class));
	}
	
	@Override
	public Range<Double> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		JsonNode node = p.getCodec().readTree(p);
		
		if (!node.has("minimum") || !node.has("maximum")) {
			throw new IOException(node.asText() + " is not a valid representation of Range");
		}
		
		return Range.between(node.get("minimum").asDouble(), node.get("maximum").asDouble());
	}
}
