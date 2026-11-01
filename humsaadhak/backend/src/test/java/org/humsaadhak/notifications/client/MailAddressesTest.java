package org.humsaadhak.notifications.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MailAddressesTest {
    @Test
    void theLogShowsWhereMailWentWithoutTheFullAddress() {
        assertThat(MailAddresses.mask("ndate1976@gmail.com")).isEqualTo("n***@gmail.com");
        assertThat(MailAddresses.mask(" Support@teqnalyze.com ")).isEqualTo("S***@teqnalyze.com");
        assertThat(MailAddresses.mask("a@b.org")).isEqualTo("a***@b.org");
        assertThat(MailAddresses.mask("not-an-address")).isEqualTo("***");
        assertThat(MailAddresses.mask("@nobody.org")).isEqualTo("***");
        assertThat(MailAddresses.mask(null)).isEqualTo("***");
        assertThat(MailAddresses.maskAll(List.of("x@y.com", "zz@y.com"))).isEqualTo("[x***@y.com, z***@y.com]");
    }
}
