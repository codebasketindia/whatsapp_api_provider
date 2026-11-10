package in.codebasket.cbwa.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.codebasket.cbwa.common.dto.PassLayout;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts PassLayout to JSON string for databases that don't support native JSON (MySQL 5.x).
 */
@Converter
public class PassLayoutConverter implements AttributeConverter<PassLayout, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(PassLayout layout) {
        if (layout == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(layout);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public PassLayout convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, PassLayout.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
