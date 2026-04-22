package org.openmrs.module.referencedemodata.fixture;

import java.util.ArrayList;
import java.util.List;

public class FixturePatient {
    public String uuid;
    public String identifier;          // optional; default DEMO-NNNNNNNNNN
    public String givenName;
    public String familyName;
    public String gender;              // "M" / "F" / "U"
    public String birthdate;           // "-P65Y"
    public Address address;
    public List<Condition> conditions = new ArrayList<>();
    public List<Allergy> allergies = new ArrayList<>();
    public List<Visit> visits = new ArrayList<>();
    public List<Order> orders = new ArrayList<>();
    public List<Lab> labs = new ArrayList<>();

    public static class Address { public String city, state, country, postalCode; }
    public static class Condition { public String uuid; public String alias; public String onset; public String status; /* active|inactive */ }
    public static class Allergy   { public String uuid; public String allergen; public String severity; public String reaction; }
    public static class Visit {
        public String uuid;
        public String date;            // "-P1M"
        public String type;            // "outpatient" | "inpatient"
        public List<Encounter> encounters = new ArrayList<>();
    }
    public static class Encounter {
        public String uuid;
        public String type;            // alias: VISIT_NOTE, VITALS, ADMISSION, DISCHARGE, CONSULTATION
        public List<Obs> obs = new ArrayList<>();
        public String noteText;        // stored as Obs on TEXT_OF_ENCOUNTER if present
    }
    public static class Obs {
        public String uuid;
        public String concept;         // alias into concepts.yaml
        public Double value;           // numeric obs only (MVP)
    }
    public static class Order {
        public String uuid;
        public String drug;            // alias: DRUG_HCTZ etc
        public Double dose;
        public String doseUnits;       // e.g. "mg"
        public String route;           // e.g. "PO"
        public String frequency;       // e.g. "daily", "twice daily", "four times daily"
        public String careSetting;     // "outpatient" | "inpatient"; default "outpatient"
        public String indication;      // alias: DX_HTN etc
        public String startDate;       // "-P25Y"
        public Integer durationDays;   // optional
        public String status;          // "active" | "inactive"
    }
    public static class Lab {
        public String concept;         // alias
        public String date;            // "-P29D"
        public Double value;
    }
}
