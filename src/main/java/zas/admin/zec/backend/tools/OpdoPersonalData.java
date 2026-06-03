package zas.admin.zec.backend.tools;

import ch.admin.zas.jweb.securityevents.core.utils.PersonalData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class OpdoPersonalData implements PersonalData {

    private final Map<String, String> fields;

    private OpdoPersonalData(Map<String, String> fields) {
        this.fields = fields;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String print() {
        return fields.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    public static class Builder {
        private final Map<String, String> fields = new LinkedHashMap<>();

        public Builder field(String key, String value) {
            fields.put(key, value);
            return this;
        }

        public OpdoPersonalData build() {
            return new OpdoPersonalData(fields);
        }
    }
}
