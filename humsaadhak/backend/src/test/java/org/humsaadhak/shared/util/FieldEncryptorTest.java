package org.humsaadhak.shared.util;

import static org.assertj.core.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class FieldEncryptorTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final FieldEncryptor enc = FieldEncryptor.fromBase64(KEY);

    @Test
    void roundTripsAndNeverStoresThePlainValue() {
        String stored = enc.encrypt("004512345678");
        assertThat(stored.startsWith("v1:")).isTrue();
        assertThat(stored.contains("004512345678")).isFalse();
        assertThat(enc.decrypt(stored)).isEqualTo("004512345678");
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        assertThat(enc.encrypt("12345678").equals(enc.encrypt("12345678"))).isFalse();
    }

    @Test
    void plainLegacyValuesAndNullsPassThrough() {
        assertThat(enc.decrypt("12345678")).isEqualTo("12345678");
        assertThat(enc.encrypt(null)).isEqualTo(null);
        assertThat(enc.decrypt(null)).isEqualTo(null);
    }

    @Test
    void tamperingOrAWrongKeyIsDetected() {
        String stored = enc.encrypt("12345678");
        String tampered = stored.substring(0, stored.length() - 2) + (stored.endsWith("A") ? "B" : "A") + "=";
        assertThatThrownBy(() -> enc.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        byte[] other = new byte[32];
        other[0] = 1;
        assertThatThrownBy(() -> new FieldEncryptor(other).decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keyMustBe32Bytes() {
        assertThatThrownBy(() -> new FieldEncryptor(new byte[16])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FieldEncryptor.fromBase64(" ")).hasMessageContaining("HUMSAADHAK_FIELD_KEY");
    }
}
