package org.humsaadhak.documents;

import org.humsaadhak.documents.util.DocumentAccessPolicy;
import org.humsaadhak.documents.util.UploadPolicy;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DocumentPoliciesTest {
    @Test void donorSeesOnlyShareableDocumentsWithApprovedShare() {
        assertThat(DocumentAccessPolicy.canView(false, 9L, 5, true, true)).isTrue();
        assertThat(DocumentAccessPolicy.canView(false, 9L, 5, true, false)).isFalse();   // S2: no share, no access
        assertThat(DocumentAccessPolicy.canView(false, 9L, 5, false, true)).isFalse();   // PAN is never shared
        assertThat(DocumentAccessPolicy.canView(false, 5L, 5, false, false)).isTrue();   // owner
        assertThat(DocumentAccessPolicy.canView(true, null, 5, false, false)).isTrue();  // staff
    }

    @Test void uploadChecksExtensionAndContent() {
        byte[] pdf = {'%', 'P', 'D', 'F', '-', '1'};
        assertThat(UploadPolicy.check("trust.PDF", 1000, pdf)).isEqualTo("application/pdf");
        assertThatThrownBy(() -> UploadPolicy.check("trust.exe", 1000, pdf)).hasMessageContaining("PDF, PNG, JPG");
        assertThatThrownBy(() -> UploadPolicy.check("fake.png", 1000, pdf)).hasMessageContaining("does not match");
        assertThatThrownBy(() -> UploadPolicy.check("big.pdf", UploadPolicy.MAX_BYTES + 1, pdf)).hasMessageContaining("20 MB");
    }
}
