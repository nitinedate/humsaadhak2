package org.humsaadhak.documents;

import org.humsaadhak.documents.util.DocumentAccessPolicy;
import org.humsaadhak.documents.util.UploadPolicy;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DocumentPoliciesTest {
    @Test void donorSeesOnlyShareableDocumentsWithApprovedShare() {
        assertThat(DocumentAccessPolicy.canView(false, 9L, 5, true, true, true)).isTrue();
        assertThat(DocumentAccessPolicy.canView(false, 9L, 5, true, false, true)).isFalse();   // S2: no share, no access
        assertThat(DocumentAccessPolicy.canView(false, 9L, 5, false, true, true)).isFalse();   // PAN is never shared
        assertThat(DocumentAccessPolicy.canView(false, 5L, 5, false, false, false)).isTrue();  // owner: any version, any state
        assertThat(DocumentAccessPolicy.canView(true, null, 5, false, false, false)).isTrue(); // staff
        // a donor with an approved share: not a rejected file or one replaced by a newer upload, only the current approved one
        assertThat(DocumentAccessPolicy.canView(false, 9L, 5, true, true, false)).isFalse();
    }

    @Test void uploadChecksExtensionAndContent() {
        byte[] pdf = {'%', 'P', 'D', 'F', '-', '1'};
        assertThat(UploadPolicy.check("trust.PDF", 1000, pdf)).isEqualTo("application/pdf");
        assertThatThrownBy(() -> UploadPolicy.check("trust.exe", 1000, pdf)).hasMessageContaining("PDF, PNG, JPG");
        assertThatThrownBy(() -> UploadPolicy.check("fake.png", 1000, pdf)).hasMessageContaining("does not match");
        assertThatThrownBy(() -> UploadPolicy.check("big.pdf", UploadPolicy.MAX_BYTES + 1, pdf)).hasMessageContaining("20 MB");
    }
}
