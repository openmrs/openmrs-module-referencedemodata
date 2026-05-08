/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.web.controller;

import java.util.Collections;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;
import org.openmrs.module.referencedemodata.ReferenceDemoDataActivator;
import org.openmrs.module.referencedemodata.ReferenceDemoDataConstants;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.openmrs.scheduler.TaskDefinition;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Slf4j
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/" + ReferenceDemoDataConstants.REFERENCE_DEMO_DATA_URI)
public class DemoDataGenerationController extends BaseRestController {

	private static ReferenceDemoDataActivator referenceDemoDataActivator;

	private static final String REFERENCE_DEMO_DATA_TASK_NAME = "Reference demo data generation task";

	public static final String NUMBER_OF_DEMO_PATIENTS_PARAMETER = "numberOfDemoPatients";

	public static final String CREATE_IF_NOT_EXISTS = "createIfNotExists";

	@RequestMapping(value = ReferenceDemoDataConstants.GENERATE_DEMO_DATA_URI, method = RequestMethod.POST, produces = "application/json")
	@ResponseBody()
	public Map<String, Object> generateDemoData(@RequestBody Map<String, Object> body) throws Exception {

		Object rawNumberOfDemoPatients = body.get(NUMBER_OF_DEMO_PATIENTS_PARAMETER);
		if (rawNumberOfDemoPatients == null) {
			throw new BadRequestException("missing '" + NUMBER_OF_DEMO_PATIENTS_PARAMETER + "' field");
		}
		String numberOfDemoPatients = rawNumberOfDemoPatients.toString();

		int requestedCount;
		try {
			requestedCount = Integer.parseInt(numberOfDemoPatients);
		}
		catch (NumberFormatException e) {
			String errorMsg = String.format("Could not parse '%s' as an integer", numberOfDemoPatients);
			log.warn(errorMsg, e);
			throw new BadRequestException(errorMsg, e);
		}

		TaskDefinition taskDef = Context.getSchedulerService().getTaskByName(REFERENCE_DEMO_DATA_TASK_NAME);
		if (taskDef == null) {
			taskDef = new TaskDefinition();
			taskDef.setTaskClass(GenerateDemoDataTask.class.getName());
			taskDef.setStartOnStartup(false);
			taskDef.setStartTime(null); // Executes immediately
			taskDef.setName(REFERENCE_DEMO_DATA_TASK_NAME);
			taskDef.setDescription(REFERENCE_DEMO_DATA_TASK_NAME);
			taskDef.setRepeatInterval(0L); // Executes once
			taskDef.setProperty("numberOfDemoPatients", numberOfDemoPatients);
			Context.getSchedulerService().saveTaskDefinition(taskDef);
		}
		int remainingNumberOfPatientsToGenerate = requestedCount;
		if (body.get(CREATE_IF_NOT_EXISTS) != null && Boolean.parseBoolean(body.get(CREATE_IF_NOT_EXISTS).toString())) {
			int existing = Context.getPatientService().getAllPatients().size();
			if (existing >= requestedCount) {
				return Collections.singletonMap("outcome",
						String.format("There already exists Demo Data for %s or more Demo Patients", numberOfDemoPatients));
			}
			remainingNumberOfPatientsToGenerate = requestedCount - existing;
		}
		GlobalProperty createDemoPatients = new GlobalProperty(
				ReferenceDemoDataConstants.CREATE_DEMO_PATIENTS_ON_NEXT_STARTUP, String.valueOf(remainingNumberOfPatientsToGenerate));
		Context.getAdministrationService().saveGlobalProperty(createDemoPatients);
		synchronized (DemoDataGenerationController.class) {
			Context.getSchedulerService().scheduleTask(taskDef);
		}
		return Collections.singletonMap("outcome",
				String.format("Generating Demo Data for %s more Demo Patients to top-up the count of existing patients",
						String.valueOf(remainingNumberOfPatientsToGenerate)));
	}

	public static class GenerateDemoDataTask extends AbstractTask {

		@Override
		public void initialize(TaskDefinition config) {
			super.initialize(config);
		}

		@Override
		public void execute() {
			DemoDataGenerationController.getReferenceDemoDataActivator().started();
		}
	}

	protected static ReferenceDemoDataActivator getReferenceDemoDataActivator() {
		if (referenceDemoDataActivator == null) {
			referenceDemoDataActivator = new ReferenceDemoDataActivator();
		}
		return referenceDemoDataActivator;
	}

	protected static void setReferenceDemoDataActivator(ReferenceDemoDataActivator referenceDemoDataActivator) {
		DemoDataGenerationController.referenceDemoDataActivator = referenceDemoDataActivator;
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	private static class BadRequestException extends RuntimeException {

		BadRequestException(String message) {
			super(message);
		}

		BadRequestException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
