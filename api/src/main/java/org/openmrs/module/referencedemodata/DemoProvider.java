package org.openmrs.module.referencedemodata;

import java.util.Date;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.api.context.Context;

public final class DemoProvider {
    private DemoProvider() {}

    public static Provider ensure() {
        Provider existing = Context.getProviderService()
                .getProviderByUuid(ReferenceDemoDataConstants.DEMO_PROVIDER_UUID);
        if (existing != null) return existing;

        Person person = Context.getPersonService()
                .getPersonByUuid(ReferenceDemoDataConstants.DEMO_PROVIDER_PERSON_UUID);
        if (person == null) {
            person = new Person();
            person.setUuid(ReferenceDemoDataConstants.DEMO_PROVIDER_PERSON_UUID);
            person.setGender("U");
            PersonName name = new PersonName("Demo", null, "Provider");
            person.addName(name);
            person.setBirthdate(Date.from(java.time.LocalDate.of(1970, 1, 1)
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
            Context.getPersonService().savePerson(person);
        }

        Provider p = new Provider();
        p.setUuid(ReferenceDemoDataConstants.DEMO_PROVIDER_UUID);
        p.setPerson(person);
        p.setIdentifier("DEMO-PROVIDER");
        return Context.getProviderService().saveProvider(p);
    }
}
