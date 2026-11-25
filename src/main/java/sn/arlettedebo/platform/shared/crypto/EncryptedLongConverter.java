package sn.arlettedebo.platform.shared.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Salaire / montants sensibles : {@code long} métier ↔ texte AES-GCM en base. */
@Converter
public class EncryptedLongConverter implements AttributeConverter<Long, String> {

    @Override
    public String convertToDatabaseColumn(Long attribute) {
        if (attribute == null) {
            return FieldCrypto.requis().chiffrerLong(0L);
        }
        return FieldCrypto.requis().chiffrerLong(attribute);
    }

    @Override
    public Long convertToEntityAttribute(String dbData) {
        return FieldCrypto.requis().dechiffrerLong(dbData);
    }
}
