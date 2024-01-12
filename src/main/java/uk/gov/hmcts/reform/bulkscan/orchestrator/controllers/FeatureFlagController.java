package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.launchdarkly.LaunchDarklyClient;

import static org.springframework.http.ResponseEntity.ok;

@RestController
public class FeatureFlagController {
    private final LaunchDarklyClient featureToggleService;

    public FeatureFlagController(LaunchDarklyClient featureToggleService) {
        this.featureToggleService = featureToggleService;
    }

    @GetMapping("/feature-flags/{flag}")
    public ResponseEntity<String> flagStatus(@PathVariable String flag) {
        boolean isEnabled = featureToggleService.isFeatureEnabled(flag);
        return ok(flag + " : " + isEnabled);
    }
}
