package in.codebasket.cbwa.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.codebasket.cbwa.common.dto.FollowUp;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts List<FollowUp> to JSON string for databases that don't support native JSON (MySQL 5.x).
 */
@Converter
public class FollowUpListConverter implements AttributeConverter<List<FollowUp>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public String convertToDatabaseColumn(List<FollowUp> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return mapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @Override
    public List<FollowUp> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<FollowUp>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}
