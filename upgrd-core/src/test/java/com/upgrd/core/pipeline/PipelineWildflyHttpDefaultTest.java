package com.upgrd.core.pipeline;

import com.upgrd.core.model.ProjectProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineWildflyHttpDefaultTest {

    @Test
    void enablesWildflyHttpForLegacyWebWhenRequested() {
        var request = new PipelineOrchestrator.PipelineRequest(
                null, null, null, null, null, "java21", "weblogic-14c",
                false, true, false, false, false, false, true,
                false, false, null, false);

        assertTrue(shouldRunWildflyHttp(request, ProjectProfile.LEGACY_WEB));
        assertFalse(shouldRunWildflyHttp(request, ProjectProfile.LEGACY_BACKEND));
    }

    @Test
    void explicitWildflyHttpOverridesDefault() {
        var request = new PipelineOrchestrator.PipelineRequest(
                null, null, null, null, null, "java21", "weblogic-14c",
                false, true, false, false, false, true, false,
                false, false, null, false);

        assertTrue(shouldRunWildflyHttp(request, ProjectProfile.LEGACY_BACKEND));
    }

    private static boolean shouldRunWildflyHttp(
            PipelineOrchestrator.PipelineRequest request,
            ProjectProfile profile) {
        return request.wildflyHttp()
                || (request.wildflyHttpWhenWebProfile() && profile == ProjectProfile.LEGACY_WEB);
    }
}
