package org.humsaadhak.notifications;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.humsaadhak.notifications.util.MailLinks;
import org.junit.jupiter.api.Test;

class MailLinksTest {
    @Test
    void findsEachLinkOnceAndUnescapesAmpersands() {
        String html = "<p>Hello</p><a href=\"https://localhost/set-password?token=abc&amp;x=1\">Set your password</a>"
                + " or open https://localhost/set-password?token=abc&amp;x=1 <img src='http://cdn/logo.png'>";
        assertThat(MailLinks.in(html)).isEqualTo(List.of("https://localhost/set-password?token=abc&x=1", "http://cdn/logo.png"));
        assertThat(MailLinks.in("No links here.")).isEmpty();
        assertThat(MailLinks.in(null)).isEmpty();
    }
}
