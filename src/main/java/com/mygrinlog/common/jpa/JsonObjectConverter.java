package com.mygrinlog.common.jpa;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;

/**
 * 임의 객체 타입을 JSON 으로 직렬화하는 베이스 컨버터.
 * 하위 클래스에서 targetType() 만 지정. (Jackson 으로 record/POJO 둘 다 지원)
 */
public abstract class JsonObjectConverter<T> implements AttributeConverter<T, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    protected abstract Class<T> targetType();

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize " + targetType().getSimpleName(), e);
        }
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return MAPPER.readValue(dbData, targetType());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize " + targetType().getSimpleName(), e);
        }
    }
}
