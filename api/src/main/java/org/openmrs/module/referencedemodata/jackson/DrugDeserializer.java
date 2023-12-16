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
import org.openmrs.Drug;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;

public class DrugDeserializer extends StdDeserializer<Drug> {
	
	public DrugDeserializer() {
		super(Drug.class);
	}
	
	@Override
	public Drug deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String uuid = p.getText();
		
		if (StringUtils.isBlank(uuid)) {
			throw new IOException(p.getCurrentName() + " cannot be blank");
		}
		
		ConceptService cs = Context.getConceptService();
		Drug drug = cs.getDrugByUuid(uuid);
		
		if (drug == null) {
			throw new IOException("Order frequency [" + uuid + "] does not exist. Please create it.");
		}
		
		return drug;
	}
}
