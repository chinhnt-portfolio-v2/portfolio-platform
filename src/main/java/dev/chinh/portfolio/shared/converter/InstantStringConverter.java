package dev.chinh.portfolio.shared.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;

/**
 * JPA AttributeConverter: Instant ↔ ISO-8601 TEXT column.
 *
 * SQLite JDBC (xerial) cannot parse epoch-millis longs via getTimestamp().
 * Storing as ISO-8601 string ("2026-06-23T16:24:19.335Z") allows round-trip
 * via Instant.parse() regardless of SQLite affinity.
 *
 * Applied autoApply=true so ALL Instant fields in ALL @Entity classes use
 * this converter without per-field annotation — matches the TEXT column type
 * in V1__init_schema.sql.
 */
@Converter(autoApply = true)
public class InstantStringConverter implements AttributeConverter<Instant, String> {

    @Override
    public String convertToDatabaseColumn(Instant attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public Instant convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return Instant.parse(dbData);
        } catch (Exception e) {
            // Fallback: try epoch millis stored as string (legacy rows)
            try {
                return Instant.ofEpochMilli(Long.parseLong(dbData.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
