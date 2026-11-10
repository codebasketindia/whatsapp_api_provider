package in.codebasket.cbwa.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts List<Map<String, String>> to JSON string for databases that don't support native JSON (MySQL 5.x).
 */
@Converter
public class MapListConverter implements AttributeConverter<List<Map<String, String>>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Map<String, String>> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public List<Map<String, String>> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}
