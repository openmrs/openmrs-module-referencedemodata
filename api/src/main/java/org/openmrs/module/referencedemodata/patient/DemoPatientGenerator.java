/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.referencedemodata.patient;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.map.DefaultedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PatientProgram;
import org.openmrs.PatientState;
import org.openmrs.Program;
import org.openmrs.ProgramWorkflowState;
import org.openmrs.Provider;
import org.openmrs.TestOrder;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.constants.PrivilegeConstants;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentKind;
import org.openmrs.module.appointments.model.AppointmentProvider;
import org.openmrs.module.appointments.model.AppointmentProviderResponse;
import org.openmrs.module.appointments.service.AppointmentServiceDefinitionService;
import org.openmrs.module.appointments.service.AppointmentsService;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.referencedemodata.ReferenceDemoDataConstants;
import org.openmrs.module.referencedemodata.diagnosis.DemoDiagnosisGenerator;
import org.openmrs.module.referencedemodata.obs.NumericObsValueDescriptor;
import org.openmrs.module.referencedemodata.obs.ObsValueGenerator;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.openmrs.module.referencedemodata.Randomizer.flipACoin;
import static org.openmrs.module.referencedemodata.Randomizer.randomArrayEntry;
import static org.openmrs.module.referencedemodata.Randomizer.randomBetween;
import static org.openmrs.module.referencedemodata.Randomizer.randomDoubleBetween;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataConstants.OPENMRS_ID_NAME;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toDate;
import static org.openmrs.module.referencedemodata.ReferenceDemoDataUtils.toLocalDateTime;
import static org.openmrs.module.referencedemodata.patient.DemoPersonGenerator.populatePerson;

@Getter
@Setter
public class DemoPatientGenerator {
	
	private static final Logger log = LoggerFactory.getLogger(DemoPatientGenerator.class);
	
	private static final int ADMISSION_DAYS_MIN = 1;
	
	private static final int ADMISSION_DAYS_MAX = 3;
	
	private static final String[] NOTE_TEXT = {
			"Lorem ipsum dolor sit amet",
			"consectetur adipisicing elit",
			"sed do eiusmod tempor incididunt",
			"ut labore et dolore magna aliqua",
			"Ut enim ad minim veniam",
			"quis nostrud exercitation ullamco laboris",
			"nisi ut aliquip ex ea commodo consequat.",
			"Duis aute irure dolor in reprehenderit in voluptat",
			"velit esse cillum dolore eu fugiat nulla pariatur.",
			"Excepteur sint occaecat cupidatat non proident",
			"sunt in culpa qui officia deserunt",
			"mollit anim id est laborum."
	};
	
	private static DemoDiagnosisGenerator demoDiagnosisGenerator;
	
	private static IdentifierSourceService iss;
	
	private static ConceptService cs;
	
	private static VisitService vs;
	
	private static ObsService os;
	
	private static AppointmentsService appointmentsService;
	
	private static ProviderService providerService;
	
	private static ProgramWorkflowService programWorkflowService;
	
	private static AppointmentServiceDefinitionService apts;
	
	private static OrderService orderService;
	
	private List<Concept> allDiagnoses;
	
	private List<Concept> testConcepts;
	
	private final ResourcePatternResolver patternResolver;
	
	private List<NumericObsValueDescriptor> vitalsDescriptors;
	
	private List<NumericObsValueDescriptor> labDescriptors;
	
	private Map<String, Concept> conceptCache = new DefaultedMap<>((key) -> {
		ConceptService cs = getConceptService();
		Concept concept = null;
		
		if (key.contains(":")) {
			String[] map = key.split(":", 2);
			if (map.length == 2 && StringUtils.isNotBlank(map[0]) && StringUtils.isNotBlank(map[1])) {
				try {
					concept = cs.getConceptByMapping(map[1], map[0]);
				}
				catch (Exception ignored) {
				
				}
			}
		}
		
		if (concept == null && key.length() >= 36 && key.length() <= 38) {
			try {
				concept = cs.getConceptByUuid(key);
			}
			catch (Exception ignored) {
			
			}
		}
		
		if (concept == null) {
			int conceptId = NumberUtils.toInt(key, -1);
			if (conceptId > 0) {
				try {
					concept = cs.getConcept(conceptId);
				}
				catch (Exception ignored) {
				
				}
			}
		}
		
		if (concept == null) {
			try {
				concept = cs.getConceptByName(key);
			}
			catch (Exception ignored) {
			
			}
		}
		
		if (concept == null) {
			throw new APIException("Could not find concept [" + key + "]");
		}
		
		return concept;
	});
	
	public DemoPatientGenerator(ResourcePatternResolver patternResolver)
			throws IOException {
		this.patternResolver = patternResolver;
		
		vitalsDescriptors = loadVitalsDescriptors(patternResolver);
		labDescriptors = loadLabDescriptors(patternResolver);
	}
	
	public void createDemoPatients(int patientCount) {
		// force the "created demo patient" below to show up
		OpenmrsUtil.applyLogLevel(DemoPatientGenerator.class.toString(), OpenmrsConstants.LOG_LEVEL_INFO);
		
		allDiagnoses = getConceptService().getConceptsByClass(getConceptService().getConceptClassByName("Diagnosis"));
		
		testConcepts = getConceptService().getConceptsByClass(getConceptService().getConceptClassByName("LabSet"));
		if (testConcepts != null) {
			testConcepts.addAll(getConceptService().getConceptsByClass(getConceptService().getConceptClassByName("Test")));
		} else {
			testConcepts = getConceptService().getConceptsByClass(getConceptService().getConceptClassByName("Test"));
		}
		
		PatientService ps = Context.getPatientService();
		Location rootLocation = randomArrayEntry(Context.getLocationService().getRootLocations(false));
		PatientIdentifierType patientIdentifierType = ps.getPatientIdentifierTypeByName(OPENMRS_ID_NAME);
		
		if (patientIdentifierType == null) {
			throw new APIException("Could not find identifier type " + OPENMRS_ID_NAME);
		}
		
		for (int i = 0; i < patientCount; i++) {
			Patient patient = createDemoPatient(ps, patientIdentifierType, rootLocation);
			log.info("created demo patient: {} {} {}", new Object[] { patient.getPatientIdentifier(), patient.getGivenName(), patient.getFamilyName() });
			Context.flushSession();
			Context.clearSession();
		}
	}
	
	private Patient createDemoPatient(PatientService ps, PatientIdentifierType patientIdentifierType, Location location) {
		int visitCount = randomBetween(1, 20);
		
		Patient patient = createBasicDemoPatient(patientIdentifierType, location, visitCount);
		patient = ps.savePatient(patient);
		
		Visit lastVisit = null;
		for (int i = 0; i < visitCount; i++) {
			boolean shortVisit = randomDoubleBetween(0, 1) < 0.8d;
			Visit visit = createDemoVisit(patient, getVisitService().getAllVisitTypes(), location, shortVisit, lastVisit,
					visitCount - (i + 1));
			lastVisit = visit;
		}
		
		return patient;
	}
	
	private Patient createBasicDemoPatient(PatientIdentifierType patientIdentifierType, Location location, int minAge) {
		Patient patient = new Patient();
		
		populatePerson(patient, minAge);
		
		PatientIdentifier patientIdentifier = new PatientIdentifier();
		patientIdentifier.setIdentifier(getIdentifierSourceService().generateIdentifier(patientIdentifierType, "DemoData"));
		patientIdentifier.setIdentifierType(patientIdentifierType);
		patientIdentifier.setDateCreated(new Date());
		patientIdentifier.setLocation(location);
		patient.addIdentifier(patientIdentifier);
		
		return patient;
	}
	
	private Visit createDemoVisit(Patient patient, List<VisitType> visitTypes, Location location, boolean shortVisit,
			Visit lastVisit, int remainingVisits) {
		int patientAge = Optional.ofNullable(patient.getAge()).orElse(0);
		
		LocalDateTime visitStart;
		if (lastVisit == null || lastVisit.getStopDatetime() == null) {
			if (patientAge > 5) {
				visitStart = LocalDateTime.now().minusDays(randomBetween(0, 365 * 5 - 1))
						.minusMinutes(randomBetween(0, 24 * 60 - 1));
				
				if (remainingVisits > 0) {
					LocalDateTime minimumStartDate = LocalDateTime.now().minusYears(remainingVisits);
					
					if (visitStart.isAfter(minimumStartDate)) {
						visitStart = minimumStartDate.plusMinutes(randomBetween(-(24 * 60 - 1), 24 * 60 - 1));
					}
				}
			} else {
				// all new/"non-retrospective" visits should happen 90 minutes before the current time, this is 
				// because some associated encounters happen anywhere from 0 to 120 minutes after the visit, this
				// is done to avoid encounter validation errors
				// (see https://github.com/openmrs/openmrs-core/blob/b7c03a28c64493023a8211c53bb60d4d944b39b7/api/src/main/java/org/openmrs/validator/EncounterValidator.java#L89)
				visitStart = LocalDateTime.now().minusDays(randomBetween(0, 365 * 1 - 2)).minusMinutes(360);
			}
		} else {
			visitStart = toLocalDateTime(lastVisit.getStopDatetime())
					.plusDays(randomBetween(0, 365 - 1))
					.plusMinutes(randomBetween(-(24 * 60 - 1), 24 * 60 - 1));
		}
		
		if (patientAge <= 5) {
			shortVisit = true;
		}
		
		// just in case the start is today, back it up a few days.
		if (!shortVisit && LocalDate.now().minusDays(ADMISSION_DAYS_MAX + 1).isBefore(visitStart.toLocalDate())) {
			visitStart = visitStart.minusDays(ADMISSION_DAYS_MAX + 1);
		}
		
		Visit visit = new Visit(patient, randomArrayEntry(visitTypes), toDate(visitStart));
		visit.setLocation(location);
		
		LocalDateTime lastEncounterTime;
		
		int vitalsStartMinutes = randomBetween(0, 60);
		lastEncounterTime = visitStart.plusMinutes(vitalsStartMinutes);
		visit.addEncounter(createDemoVitalsEncounter(patient, toDate(lastEncounterTime)));
		
		lastEncounterTime = lastEncounterTime.plusMinutes(randomBetween(0, 120));
		visit.addEncounter(createVisitNote(patient, toDate(lastEncounterTime), location));
		
		if (shortVisit) {
			// roughly 2/3rds of "short" visits will have lab results
			if (randomDoubleBetween(0.0, 1.0) < .67) {
				int labStartMinutes = randomBetween(0, 120);
				lastEncounterTime = lastEncounterTime.plusMinutes(labStartMinutes);
				visit.addEncounter(createDemoLabsEncounter(patient, toDate(lastEncounterTime), location));
			}
			
			LocalDateTime visitEndTime = lastEncounterTime.plusMinutes(randomBetween(20, 40));
			visit.setStopDatetime(toDate(visitEndTime));
		} else {
			// admit now and discharge a few days later
			Location admitLocation = Context.getLocationService().getLocation("Inpatient Ward");
			LocalDateTime admitTime = lastEncounterTime;
			visit.addEncounter(createEncounter("Admission", patient, toDate(admitTime), admitLocation));
			
			LocalDateTime dischargeTime = admitTime
					.plusDays(randomBetween(ADMISSION_DAYS_MIN, ADMISSION_DAYS_MAX))
					.plusMinutes(randomBetween(-12 * 30, 12 * 60));
			
			for (int i = 0; i < Duration.between(admitTime, dischargeTime).toDays(); i++) {
				lastEncounterTime = lastEncounterTime.plusDays(1)
						.plusMinutes(randomBetween(-12 * 60, 12 * 60));
				
				if (lastEncounterTime.isAfter(dischargeTime.minusHours(4))) {
					lastEncounterTime = dischargeTime.minusHours(4).minusMinutes(randomBetween(5, 60 * 12));
				}
				
				visit.addEncounter(createDemoVitalsEncounter(patient, toDate(lastEncounterTime), admitLocation));
				
				lastEncounterTime = lastEncounterTime.plusMinutes(randomBetween(0, 120));
				visit.addEncounter(createVisitNote(patient, toDate(lastEncounterTime), admitLocation));
				
				int labStartMinutes = randomBetween(0, 120);
				lastEncounterTime = lastEncounterTime.plusMinutes(labStartMinutes);
				visit.addEncounter(createDemoLabsEncounter(patient, toDate(lastEncounterTime), admitLocation));
			}
			
			if (randomDoubleBetween(0.0, 1.0) < .75) {
				Encounter orderEncounter = null;
				if (randomDoubleBetween(0.0, 1.0) < .5) {
					orderEncounter = createDemoOrdersEncounter(patient, toDate(lastEncounterTime), location);
					if (testConcepts != null && testConcepts.size() > 0) {
						createDemoOrder(orderEncounter, getOrderService().getOrderTypeByName("Test Order"), testConcepts);	
					}
				}
				// TODO: create drug orders
				
				if (orderEncounter != null) {
					visit.addEncounter(orderEncounter);
				}
			}
			visit.addEncounter(createEncounter("Discharge", patient, toDate(dischargeTime), admitLocation));
			visit.setStopDatetime(toDate(dischargeTime));
		}
		
		visit = getVisitService().saveVisit(visit);
		
		if (randomDoubleBetween(0.0, 1.0) < .1) {
			createDemoAppointment(patient, visit.getStartDatetime());
		}
		
		if (randomDoubleBetween(0.0, 1.0) < .1) {
			// there are three demo programs configured
			List<Program> programs = getProgramWorkflowService().getAllPrograms(false);
			if (programs != null && programs.size() > 0) {
				createDemoPatientProgram(patient, randomArrayEntry(programs), visit.getStartDatetime());	
			}
		}
		return visit;
	}
	
	private Encounter createVisitNote(Patient patient, Date encounterTime, Location location) {
		Encounter visitNote = createEncounter("Visit Note", patient, encounterTime, location);
		visitNote.setForm(Context.getFormService().getForm("Visit Note"));
		Context.getEncounterService().saveEncounter(visitNote);
		
		createTextObs("CIEL:162169", randomArrayEntry(NOTE_TEXT), patient, visitNote, encounterTime, location);
		
		if (allDiagnoses.size() > 0) {
			getDemoDiagnosisGenerator().createDiagnosis(true, patient, visitNote, location, getAllDiagnoses());
			
			if (flipACoin()) {
				// add a second diagnosis
				getDemoDiagnosisGenerator().createDiagnosis(false, patient, visitNote, location, getAllDiagnoses());
			}
		}
		
		return visitNote;
	}
	
	private Encounter createDemoVitalsEncounter(Patient patient, Date encounterTime) {
		Location location = Context.getLocationService().getLocation("Outpatient Clinic");
		return createDemoVitalsEncounter(patient, encounterTime, location);
	}
	
	private Encounter createDemoVitalsEncounter(Patient patient, Date encounterTime, Location location) {
		Encounter encounter = createEncounter("Vitals", patient, encounterTime, location);
		createDemoVitalsObs(patient, encounter, location);
		return encounter;
	}
	
	private Encounter createDemoLabsEncounter(Patient patient, Date encounterTime, Location location) {
		Encounter encounter = createEncounter("Lab Results", patient, encounterTime, location);
		createDemoLabObs(patient, encounter, location);
		return encounter;
	}
	
	private Encounter createEncounter(String encounterType, Patient patient, Date encounterTime, Location location) {
		EncounterService es = Context.getEncounterService();
		
		Encounter encounter = new Encounter();
		encounter.setEncounterDatetime(encounterTime);
		encounter.setEncounterType(es.getEncounterType(encounterType));
		encounter.setPatient(patient);
		encounter.setLocation(location);
		
		return es.saveEncounter(encounter);
	}
	
	private void createDemoVitalsObs(Patient patient, Encounter encounter, Location location) {
		for (NumericObsValueDescriptor vitalsDescriptor : vitalsDescriptors) {
			createNumericObsFromDescriptor(vitalsDescriptor, patient, encounter, location);
		}
	}
	
	private void createDemoLabObs(Patient patient, Encounter encounter, Location location) {
		for (NumericObsValueDescriptor labDescriptor : labDescriptors) {
			createNumericObsFromDescriptor(labDescriptor, patient, encounter, location);
		}
	}
	
	private void createDemoAppointment(Patient patient, Date visitTime) {
		Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        Date startDateTime = toDate(toLocalDateTime(visitTime).plusMinutes(randomBetween(0, 60)));
        Date endDateTime = toDate(toLocalDateTime(startDateTime).plusMinutes(randomBetween(10, 90)));
        appointment.setStartDateTime(startDateTime);
        appointment.setEndDateTime(endDateTime);
        appointment.setAppointmentKind(AppointmentKind.Scheduled);
        
        appointment.setService(getAppointmentServiceDefinitionService().getAllAppointmentServices(false).get(randomBetween(0, 20) % 2));
        appointment.setAppointmentAudits(new HashSet<>());
        Set<AppointmentProvider> appointmentProviders = new HashSet<>();
        AppointmentProvider appointmentProvider = new AppointmentProvider();
        appointmentProvider.setAppointment(appointment);
        Provider provider = getProviderService().getProviderByUuid(ReferenceDemoDataConstants.DOCTOR_PERSON_UUID) != null ? getProviderService().getProviderByUuid(ReferenceDemoDataConstants.DOCTOR_PERSON_UUID): randomArrayEntry(getProviderService().getAllProviders(false));
        appointmentProvider.setProvider(provider);
        appointmentProvider.setResponse(AppointmentProviderResponse.ACCEPTED);

        appointmentProviders.add(appointmentProvider);
        appointment.setProviders(appointmentProviders);
        Context.addProxyPrivilege(PrivilegeConstants.MANAGE_APPOINTMENTS);
        getAppointmentsService().validateAndSave(appointment);
        Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_APPOINTMENTS);
        
	}
	
	private void createDemoPatientProgram(Patient patient, Program program, Date visitTime) {
		
		Date startDate = toDate(toLocalDateTime(visitTime).plusHours((randomBetween(0, 72))));
		ProgramWorkflowState state = null;
		if (program.getWorkflows().stream().findFirst().get() != null ) {
			state = program.getWorkflows().stream().findFirst().get().getStates().stream().findFirst().get();
		}
		
		PatientProgram patientprogram = new PatientProgram();
		patientprogram.setProgram(program);
		patientprogram.setPatient(patient);
		patientprogram.setDateEnrolled(startDate);
		patientprogram.setDateCompleted(null);

		PatientState patientstate1 = new PatientState();
		patientstate1.setStartDate(startDate);
		patientstate1.setState(state);
		
		patientprogram.getStates().add(patientstate1);
		patientstate1.setPatientProgram(patientprogram);
		
		getProgramWorkflowService().savePatientProgram(patientprogram); 
	}
	
	private Encounter createDemoOrdersEncounter(Patient patient, Date encounterTime, Location location) {
		Encounter encounter = createEncounter("Consultation", patient, encounterTime, location);
		return encounter;
	}
	
	private void createDemoOrder(Encounter encounter, OrderType orderType, List<Concept> orderConcepts) {
		OrderService orderService = getOrderService();
		List<CareSetting> careSettings = orderService.getCareSettings(false);
		if ("Test Order".equalsIgnoreCase(orderType.getName())) {
			TestOrder order = new TestOrder();
			order.setAction(Order.Action.NEW);
			order.setUrgency(randomArrayEntry(Arrays.asList(Order.Urgency.values())));
			order.setScheduledDate(order.getUrgency() == Order.Urgency.ON_SCHEDULED_DATE ? toDate(toLocalDateTime(encounter.getEncounterDatetime()).plusMinutes(randomBetween(0, 60))) : null);
			order.setPatient(encounter.getPatient());
			order.setOrderType(orderType);
			order.setConcept(orderConcepts.get(randomBetween(0, orderConcepts.size())));
			Provider provider = getProviderService().getProviderByUuid(ReferenceDemoDataConstants.DOCTOR_PERSON_UUID) != null ? getProviderService().getProviderByUuid(ReferenceDemoDataConstants.DOCTOR_PERSON_UUID): randomArrayEntry(getProviderService().getAllProviders(false));
			order.setOrderer(provider);
			order.setCareSetting(careSettings.get(randomBetween(0, careSettings.size())));
			order.setEncounter(encounter);
			encounter.addOrder(order);
			order.setDateActivated(toDate(toLocalDateTime(encounter.getEncounterDatetime())));
			orderService.saveOrder(order, null);
		}
		else if ("Drug Order".equalsIgnoreCase(orderType.getName())) {
			
		}
	}

	private void createNumericObsFromDescriptor(NumericObsValueDescriptor descriptor, Patient patient, Encounter encounter,
			Location location) {
		Obs previousObs = null;
		try {
			List<Obs> potentialObs = os.getObservations(
					Collections.singletonList(patient),
					null,
					Collections.singletonList(descriptor.getConcept()),
					null,
					null,
					null,
					null,
					1,
					null,
					null,
					null,
					false
			);
			
			if (potentialObs.size() > 0) {
				previousObs = potentialObs.get(0);
			}
		}
		catch (Exception ignored) {
		
		}
		
		Obs partialObs = ObsValueGenerator.createObsWithNumericValue(descriptor,
				previousObs != null ? previousObs.getValueNumeric() : null);
		createObs(partialObs, patient, encounter, encounter.getEncounterDatetime(), location);
	}
	
	protected void createObs(Obs partialObs, Patient patient, Encounter encounter, Date encounterTime, Location location) {
		partialObs.setPerson(patient);
		partialObs.setEncounter(encounter);
		partialObs.setObsDatetime(encounterTime);
		partialObs.setLocation(location);
		
		getObsService().saveObs(partialObs, null);
	}
	
	private void createTextObs(String conceptName, String text, Patient patient, Encounter encounter, Date encounterTime,
			Location location) {
		Obs obs = createBasicObs(conceptName, patient, encounterTime, location);
		obs.setValueText(text);
		encounter.addObs(obs);
		getObsService().saveObs(obs, null);
	}
	
	protected Obs createCodedObs(String conceptName, String codedConceptName, Patient patient, Encounter encounter,
			Date encounterTime,
			Location location) {
		return createCodedObs(conceptName, findConcept(codedConceptName), patient, encounter, encounterTime, location);
	}
	
	protected Obs createCodedObs(String conceptName, Concept concept, Patient patient, Encounter encounter,
			Date encounterTime, Location location) {
		
		Obs obs = createBasicObs(conceptName, patient, encounterTime, location);
		obs.setValueCoded(concept);
		encounter.addObs(obs);
		
		return obs;
	}
	
	protected Obs createBasicObs(String conceptName, Patient patient, Date encounterTime, Location location) {
		Concept concept = findConcept(conceptName);
		if (concept == null) {
			log.warn("incorrect concept identifier? [{}]", conceptName);
		}
		
		return new Obs(patient, concept, encounterTime, location);
	}
	
	protected Concept findConcept(String conceptDescriptor) {
		return getConceptCache().get(conceptDescriptor);
	}
	
	private List<NumericObsValueDescriptor> loadVitalsDescriptors(ResourcePatternResolver patternResolver)
			throws IOException {
		return loadObsValueDescriptorsFor(patternResolver, "classpath*:vitalsValueDescriptors/*.json");
	}
	
	private List<NumericObsValueDescriptor> loadLabDescriptors(ResourcePatternResolver patternResolver)
			throws IOException {
		return loadObsValueDescriptorsFor(patternResolver, "classpath*:labValueDescriptors/*.json");
	}
	
	private List<NumericObsValueDescriptor> loadObsValueDescriptorsFor(ResourcePatternResolver patternResolver,
			String resourcePattern) throws IOException {
		ObjectMapper om = new ObjectMapper();
		ObjectReader reader = om.readerFor(NumericObsValueDescriptor.class);
		return Arrays.stream(patternResolver.getResources(resourcePattern))
				.map(r -> {
					try (InputStream is = r.getInputStream()) {
						if (is == null) {
							return Optional.<NumericObsValueDescriptor>empty();
						}
						
						return Optional.<NumericObsValueDescriptor>of(reader.readValue(is));
					}
					catch (IOException e) {
						log.warn("Exception caught while attempting to read [{}]", r.getFilename(), e);
						return Optional.<NumericObsValueDescriptor>empty();
					}
				}).filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());
	}
	
	private IdentifierSourceService getIdentifierSourceService() {
		if (iss == null) {
			iss = Context.getService(IdentifierSourceService.class);
		}
		
		return iss;
	}
	
	// exists for testing...
	public static void setIdentifierSourceService(IdentifierSourceService iss) {
		DemoPatientGenerator.iss = iss;
	}
	
	private ConceptService getConceptService() {
		if (cs == null) {
			cs = Context.getConceptService();
		}
		
		return cs;
	}
	
	private ObsService getObsService() {
		if (os == null) {
			os = Context.getObsService();
		}
		
		return os;
	}
	
	private ProviderService getProviderService() {
		if (providerService == null) {
			providerService = Context.getService(ProviderService.class);
		}
		
		return providerService;
	}
	
	private AppointmentsService getAppointmentsService() {
		if (appointmentsService == null) {
			appointmentsService = Context.getService(AppointmentsService.class);
		}
		
		return appointmentsService;
	}
	
	private ProgramWorkflowService getProgramWorkflowService() {
		if (programWorkflowService == null) {
			programWorkflowService = Context.getProgramWorkflowService();
		}
		
		return programWorkflowService;
	}
	
	private AppointmentServiceDefinitionService getAppointmentServiceDefinitionService() {
		if (apts == null) {
			apts = Context.getService(AppointmentServiceDefinitionService.class);
		}
		
		return apts;
	}
	
	private OrderService getOrderService() {
		if (orderService == null) {
			orderService = Context.getOrderService();
		}

		return orderService;
	}

	public static DemoDiagnosisGenerator getDemoDiagnosisGenerator() {
		if (demoDiagnosisGenerator == null) {
			demoDiagnosisGenerator = Context.getRegisteredComponents(DemoDiagnosisGenerator.class).get(0);
		}
		return demoDiagnosisGenerator;
	}
	
	public static VisitService getVisitService() {
		if (vs == null) {
			vs = Context.getVisitService();
		}
		return vs;
	}
}
