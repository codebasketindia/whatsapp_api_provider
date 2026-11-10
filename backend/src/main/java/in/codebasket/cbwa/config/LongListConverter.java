package in.codebasket.cbwa.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts List<Long> to JSON string for databases that don't support native JSON (MySQL 5.x).
 */
@Converter
public class LongListConverter implements AttributeConverter<List<Long>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Long> list) {
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
    public List<Long> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}
