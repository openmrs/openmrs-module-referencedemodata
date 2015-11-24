/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.referencedemodata;

import org.openmrs.User;
import org.openmrs.annotation.OpenmrsProfile;
import org.openmrs.api.context.Context;
import org.springframework.stereotype.Component;

@Component("referencedemodata.UserServiceCompatibility")
@OpenmrsProfile(openmrsPlatformVersion = "2.0.*")
public class UserServiceCompatibility2_0 implements UserServiceCompatibility {

	@Override
	public User saveUser(User user, String password) {
		if (user.getUserId() == null) {
			return Context.getUserService().createUser(user, password);
		}
		else {
			return Context.getUserService().saveUser(user);
		}
	}
}
