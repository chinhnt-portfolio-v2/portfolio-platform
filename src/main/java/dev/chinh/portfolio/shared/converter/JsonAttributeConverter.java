package dev.chinh.portfolio.shared.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.Map;

/**
 * JPA AttributeConverter: Map<String, Object> ↔ JSON TEXT column.
 * Used to replace JSONB columns with plain TEXT in SQLite/libSQL.
 * Default (empty map) serializes as "{}".
 */
@Converter
public class JsonAttributeConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) return "{}";
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize Map to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank() || dbData.equals("{}")) return Collections.emptyMap();
        try {
            return MAPPER.readValue(dbData, MAP_TYPE);
        } catch (Exception e) {
            // Corrupt stored data: return empty map rather than crash
            return Collections.emptyMap();
        }
    }
}
