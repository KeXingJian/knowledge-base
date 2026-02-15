package com.kxj.knowledgebase.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class VectorConverter implements AttributeConverter<String, Object> {

    @Override
    public Object convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute;
    }

    @Override
    public String convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return null;
        }
        return dbData.toString();
    }
}