package org.openmrs.module.referencedemodata.fixture;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.openmrs.Concept;
import org.openmrs.api.context.Context;
import org.yaml.snakeyaml.Yaml;

public final class ConceptAliases {

    public static final class Ref {
        public final String uuid;
        public final String ciel;
        private Ref(String uuid, String ciel) { this.uuid = uuid; this.ciel = ciel; }
    }

    private final Map<String, Ref> map;
    private final Map<String, Concept> cache = new HashMap<>();

    private ConceptAliases(Map<String, Ref> map) { this.map = map; }

    public static ConceptAliases fromClasspath(String path) {
        try (InputStream in = ConceptAliases.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing classpath resource: " + path);
            Yaml y = new Yaml();
            Map<String, Map<String, Object>> raw;
            try {
                raw = y.load(in);
            } catch (org.yaml.snakeyaml.error.YAMLException e) {
                throw new IllegalStateException("failed to parse " + path + ": " + e.getMessage(), e);
            }
            Map<String, Ref> refs = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> e : raw.entrySet()) {
                String alias = e.getKey();
                Object uuid = e.getValue().get("uuid");
                Object ciel = e.getValue().get("ciel");
                if (uuid == null && ciel == null) {
                    throw new IllegalStateException("malformed alias entry '" + alias + "' in " + path + ": needs uuid or ciel");
                }
                refs.put(alias, new Ref(
                        uuid == null ? null : uuid.toString(),
                        ciel == null ? null : ciel.toString()));
            }
            return new ConceptAliases(refs);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to read " + path, e);
        }
    }

    public Concept resolve(String alias) {
        Concept cached = cache.get(alias);
        if (cached != null) return cached;
        Ref ref = map.get(alias);
        if (ref == null) throw new IllegalStateException("unknown concept alias: " + alias);
        Concept c = null;
        if (ref.uuid != null) {
            c = Context.getConceptService().getConceptByUuid(ref.uuid);
        } else if (ref.ciel != null) {
            c = Context.getConceptService().getConceptByMapping(ref.ciel, "CIEL");
        }
        if (c == null) {
            throw new IllegalStateException("concept alias " + alias + " did not resolve (uuid=" + ref.uuid + ", ciel=" + ref.ciel + ")");
        }
        cache.put(alias, c);
        return c;
    }
}
