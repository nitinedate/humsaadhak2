package org.humsaadhak.shared.entity;

import org.humsaadhak.shared.util.FieldEncryptor;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** JPA converter: add @Convert(converter = EncryptedStringConverter.class) to a String column to encrypt it at rest. */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    private final FieldEncryptor encryptor;

    public EncryptedStringConverter(@Value("${humsaadhak.security.field-key}") String base64Key) {
        this.encryptor = FieldEncryptor.fromBase64(base64Key);
    }

    @Override public String convertToDatabaseColumn(String attribute) { return encryptor.encrypt(attribute); }
    @Override public String convertToEntityAttribute(String dbData) { return encryptor.decrypt(dbData); }
}
