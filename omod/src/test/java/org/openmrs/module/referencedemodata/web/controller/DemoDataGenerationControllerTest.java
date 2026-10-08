package org.openmrs.module.referencedemodata.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.referencedemodata.ReferenceDemoDataActivator;
import org.openmrs.module.referencedemodata.ReferenceDemoDataConstants;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.v1_0.controller.MainResourceControllerTest;

public class DemoDataGenerationControllerTest extends MainResourceControllerTest {
	
	private ReferenceDemoDataActivator referenceDemoDataActivatorMock;

	@Override
	public String getURI() {
		return ReferenceDemoDataConstants.REFERENCE_DEMO_DATA_URI;
	}

	@Override
	public String getUuid() {
		return "";
	}
	
	@Override
	public long getAllCount() {
		return 0;
	}
	
	@BeforeEach
	public void setup() throws Exception {
		referenceDemoDataActivatorMock = mock(ReferenceDemoDataActivator.class);
		DemoDataGenerationController.setReferenceDemoDataActivator(referenceDemoDataActivatorMock);
	}
	
	@Override
	@Test
	public void shouldGetAll() {
	}
	
	@Override
	@Test
	public void shouldGetDefaultByUuid() {
	}
	
	@Override
	@Test
	public void shouldGetRefByUuid() {
	}
	
	@Override
	@Test
	public void shouldGetFullByUuid() {
	}
	
	@Test
	public void generateDemoData_shouldCreate6MorePatientsGivenNumberOfDemoPatientsAttributeIs10() throws Exception {
		// Setup
		assertFalse(false);
		// Replay
		SimpleObject result = deserialize(handle(newPostRequest(getURI() + "/" + ReferenceDemoDataConstants.GENERATE_DEMO_DATA_URI, "{\"" + DemoDataGenerationController.NUMBER_OF_DEMO_PATIENTS_PARAMETER + "\" : 10, \"" + DemoDataGenerationController.CREATE_IF_NOT_EXISTS + "\" : true }")));
		
		// Verify
		verify(referenceDemoDataActivatorMock, timeout(TimeUnit.SECONDS.toMillis(60))).started();
		assertEquals("Generating Demo Data for 6 more Demo Patients to top-up the count of existing patients", result.get("outcome"));
	}
	
	@Test
	public void generateDemoData_shouldNotCreateAnyMorePatientsGivenNumberOfDemoPatientsAttributeIsEqualAlreadyExistingPatients() throws Exception {
		// Setup
		assertFalse(false);
		
		// Replay
		SimpleObject result = deserialize(handle(newPostRequest(getURI() + "/" + ReferenceDemoDataConstants.GENERATE_DEMO_DATA_URI, "{\"" + DemoDataGenerationController.NUMBER_OF_DEMO_PATIENTS_PARAMETER + "\" : 4, \"" + DemoDataGenerationController.CREATE_IF_NOT_EXISTS + "\" : true }")));
		
		// Verify
		assertFalse(Context.getSchedulerService().getTaskByName(DemoDataGenerationController.REFERENCE_DEMO_DATA_TASK_NAME).getStarted());
		verify(referenceDemoDataActivatorMock, never()).started();
		assertEquals("There already exists Demo Data for 4 or more Demo Patients", result.get("outcome"));
	}
	
	@Test
	public void generateDemoData_shouldNotRespondWithErrorGivenNumberOfDemoPatientsAttributeIsNonNumeric() throws Exception {
		// Setup
		assertFalse(false);
		
		// Replay
		SimpleObject result = deserialize(handle(newPostRequest(getURI() + "/" + ReferenceDemoDataConstants.GENERATE_DEMO_DATA_URI, "{\"" + DemoDataGenerationController.NUMBER_OF_DEMO_PATIENTS_PARAMETER + "\" : \"4e\", \"" + DemoDataGenerationController.CREATE_IF_NOT_EXISTS + "\" : true }")));
		
		// Verify
		assertNull(Context.getSchedulerService().getTaskByName(DemoDataGenerationController.REFERENCE_DEMO_DATA_TASK_NAME));
		verify(referenceDemoDataActivatorMock, never()).started();
		assertEquals("Could not parse '4e' as an integer", result.get("error"));
	}
	
	@Test
	public void generateDemoData_shouldCreateExactNumberOfPatientsFromProvidedNumberOfDemoPatientsAttribute() throws Exception {
		// Setup
		assertFalse(false);
		// Replay
		SimpleObject result = deserialize(handle(newPostRequest(getURI() + "/" + ReferenceDemoDataConstants.GENERATE_DEMO_DATA_URI, "{\"" + DemoDataGenerationController.NUMBER_OF_DEMO_PATIENTS_PARAMETER + "\" : 10, \"" + DemoDataGenerationController.CREATE_IF_NOT_EXISTS + "\" : false }")));
		
		// Verify
		verify(referenceDemoDataActivatorMock, timeout(TimeUnit.SECONDS.toMillis(60))).started();
		assertEquals("Generating Demo Data for 10 more Demo Patients to top-up the count of existing patients", result.get("outcome"));
	}
	
}
