package org.openmrs.module.referencedemodata.web.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.referencedemodata.ReferenceDemoDataActivator;
import org.openmrs.module.referencedemodata.ReferenceDemoDataConstants;
import org.openmrs.module.referencedemodata.web.controller.DemoDataGenerationController;
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
	
	@Before
	public void setup() throws Exception {
		referenceDemoDataActivatorMock = mock(ReferenceDemoDataActivator.class);
		doNothing().when(referenceDemoDataActivatorMock).started();
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
	public void generateDemoData_shouldCreate6MorePatientsGivenNumberOfDemoPatientsParameterIs10() throws Exception {
		// Setup
		assertFalse(false);
		
		// Replay
		SimpleObject result = deserialize(handle(newGetRequest(getURI() + "/" + ReferenceDemoDataConstants.GENERATE_DEMO_DATA_URI, new Parameter(DemoDataGenerationController.NUMBER_OF_DEMO_PATIENTS_PARAMETER, "10"))));
		
		// Verify
		verify(referenceDemoDataActivatorMock).started();
		assertEquals("Generating Demo Data for 6 more Demo Patients to top-up the count of existing patients", result.get("outcome"));
	}
	
	@Test
	public void generateDemoData_shouldNotCreateAnyMorePatientsGivenNumberOfDemoPatientsParameterIsEqualAlreadyExistingPatients() throws Exception {
		// Setup
		assertFalse(false);
		
		// Replay
		SimpleObject result = deserialize(handle(newGetRequest(getURI() + "/" + ReferenceDemoDataConstants.GENERATE_DEMO_DATA_URI, new Parameter(DemoDataGenerationController.NUMBER_OF_DEMO_PATIENTS_PARAMETER, "4"))));
		
		// Verify
		verify(referenceDemoDataActivatorMock, never()).started();
		assertEquals("There already exists Demo Data for 4 or more Demo Patients", result.get("outcome"));
	}
	
	@Test
	public void generateDemoData_shouldNotRespondWithErrorGivenNumberOfDemoPatientsParameterIsNonNumeric() throws Exception {
		// Setup
		assertFalse(false);
		
		// Replay
		SimpleObject result = deserialize(handle(newGetRequest(getURI() + "/" + ReferenceDemoDataConstants.GENERATE_DEMO_DATA_URI, new Parameter(DemoDataGenerationController.NUMBER_OF_DEMO_PATIENTS_PARAMETER, "4e"))));
		
		// Verify
		verify(referenceDemoDataActivatorMock, never()).started();
		assertEquals("Could not parse '4e' as an integer", result.get("error"));
	}
	
}
