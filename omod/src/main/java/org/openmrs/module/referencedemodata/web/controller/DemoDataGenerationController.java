package org.openmrs.module.referencedemodata.web.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.GlobalProperty;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.referencedemodata.ReferenceDemoDataActivator;
import org.openmrs.module.referencedemodata.ReferenceDemoDataConstants;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.openmrs.scheduler.SchedulerException;
import org.openmrs.scheduler.TaskDefinition;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/" + ReferenceDemoDataConstants.REFERENCE_DEMO_DATA_URI)
public class DemoDataGenerationController extends BaseRestController {
	
	private static ReferenceDemoDataActivator referenceDemoDataActivator;
	
	private static final String REFERENCE_DEMO_DATA_TASK_NAME = "Reference demo data generation task";
	
	public static final String NUMBER_OF_DEMO_PATIENTS_PARAMETER = "numberOfDemoPatients";

	@RequestMapping(value = ReferenceDemoDataConstants.GENERATE_DEMO_DATA_URI, method = RequestMethod.POST, produces = "application/json")
	@ResponseBody()
	public String generateDemoData(@RequestBody Map<String, Object> body) throws Exception {
		
		String numberOfDemoPatients = body.get(NUMBER_OF_DEMO_PATIENTS_PARAMETER).toString();
		
		try {
			Integer.parseInt(numberOfDemoPatients);
		}
		catch (NumberFormatException e) {
			String errorMsg = String.format("Could not parse '%s' as an integer", numberOfDemoPatients);
			return String.format("{\"error\": \"%s\"}", errorMsg);
		}
		
		Context.addProxyPrivilege(PrivilegeConstants.MANAGE_SCHEDULER);
		TaskDefinition taskDef = Context.getSchedulerService().getTaskByName(REFERENCE_DEMO_DATA_TASK_NAME);
		if (taskDef == null) {
			try {
				taskDef = new TaskDefinition();
				taskDef.setTaskClass(GenerateDemoDataTask.class.getName());
				taskDef.setStartOnStartup(false);
				taskDef.setStartTime(null); // Executes immediately
				taskDef.setName(REFERENCE_DEMO_DATA_TASK_NAME);
				taskDef.setDescription(REFERENCE_DEMO_DATA_TASK_NAME);
				taskDef.setRepeatInterval(0L); // Executes once
				taskDef.setProperty("numberOfDemoPatients", numberOfDemoPatients);
				Context.getSchedulerService().saveTaskDefinition(taskDef);
			} finally {
				Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_SCHEDULER);
			}
		}
		if (Context.getPatientService().getAllPatients().size() < Integer.parseInt(numberOfDemoPatients)) {
			int remainingNumberOfPatientsToGenerate;
			try {
				remainingNumberOfPatientsToGenerate = Integer.parseInt(numberOfDemoPatients) - Context.getPatientService().getAllPatients().size();
				Context.addProxyPrivilege(PrivilegeConstants.MANAGE_GLOBAL_PROPERTIES);
				GlobalProperty createDemoPatients = new GlobalProperty(
						ReferenceDemoDataConstants.CREATE_DEMO_PATIENTS_ON_NEXT_STARTUP, "" + Integer.toString(remainingNumberOfPatientsToGenerate));
				Context.getAdministrationService().saveGlobalProperty(createDemoPatients);
				synchronized (DemoDataGenerationController.class) {
					Context.getSchedulerService().scheduleTask(taskDef);
				}
			} finally {
				Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_GLOBAL_PROPERTIES);
				Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_SCHEDULER);
			}
			return String.format("{\"outcome\": \"Generating Demo Data for %s more Demo Patients to top-up the count of existing patients\"}", Integer.toString(remainingNumberOfPatientsToGenerate));
		} else {
			return String.format("{\"outcome\": \"There already exists Demo Data for %s or more Demo Patients\"}", numberOfDemoPatients);
		}
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
}
