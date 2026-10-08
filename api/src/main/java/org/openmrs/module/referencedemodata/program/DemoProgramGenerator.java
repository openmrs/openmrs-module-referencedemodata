/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.program;

import java.util.Date;
import java.util.List;

import org.openmrs.Patient;
import org.openmrs.PatientProgram;
import org.openmrs.PatientState;
import org.openmrs.Program;
import org.openmrs.ProgramWorkflow;
import org.openmrs.ProgramWorkflowState;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.api.context.Context;

import static org.openmrs.module.referencedemodata.Randomizer.randomBetween;
import static org.openmrs.module.referencedemodata.Randomizer.randomListEntry;
import static org.openmrs.module.referencedemodata.Randomizer.shouldRandomEventOccur;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toLocalDateTime;

public class DemoProgramGenerator {
	
	private List<Program> programs = null;
	
	private ProgramWorkflowService pws = null;
	
	public void createDemoPatientProgram(Patient patient, Date startDate) {
		Program program = getRandomProgram();
		
		if (program == null || program.getWorkflows() == null || program.getWorkflows().isEmpty()) {
			return;
		}
		
		ProgramWorkflowState initialState;
		ProgramWorkflow workflow = program.getWorkflows().iterator().next();
		if (workflow == null || workflow.getStates() == null || workflow.getStates().isEmpty()) {
			return;
		}
		
		initialState = workflow.getStates().iterator().next();
		
		PatientProgram patientProgram = new PatientProgram();
		patientProgram.setProgram(program);
		patientProgram.setPatient(patient);
		patientProgram.setDateEnrolled(startDate);
		
		Date initialStateDate = startDate;
		if (shouldRandomEventOccur(.05)) {
			initialStateDate = toDate(toLocalDateTime(startDate).plusHours((randomBetween(0, 6))));
		}
		
		PatientState patientState1 = new PatientState();
		patientState1.setStartDate(initialStateDate);
		patientState1.setState(initialState);
		
		patientProgram.getStates().add(patientState1);
		patientState1.setPatientProgram(patientProgram);
		
		getProgramWorkflowService().savePatientProgram(patientProgram);
	}
	
	protected Program getRandomProgram() {
		if (programs == null) {
			programs = getProgramWorkflowService().getAllPrograms();
		}
		
		return randomListEntry(programs);
	}
	
	protected ProgramWorkflowService getProgramWorkflowService() {
		if (pws == null) {
			pws = Context.getProgramWorkflowService();
		}
		
		return pws;
	}
	
}
