package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ApiController {

    @Autowired
    private EntitlementsService entitlementsService;

    @GetMapping("/identity")
    public ResponseEntity<Map<String, Object>> getIdentity(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(jwt.getClaims());
    }

    @GetMapping("/entitlements")
    public ResponseEntity<List<String>> getEntitlements(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        List<String> entitlements = entitlementsService.getEntitlements(userId);
        return ResponseEntity.ok(entitlements);
    }

}