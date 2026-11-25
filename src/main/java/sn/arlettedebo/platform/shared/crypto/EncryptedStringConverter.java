package sn.arlettedebo.platform.shared.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persistance AES-GCM ; lecture rétrocompatible du clair (re-chiffrage au prochain flush / backfill). */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return FieldCrypto.requis().chiffrer(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return FieldCrypto.requis().dechiffrer(dbData);
    }
}
