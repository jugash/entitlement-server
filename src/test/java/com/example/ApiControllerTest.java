package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiController.class)
class ApiControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private EntitlementsService entitlementsService;

        @Test
        void getIdentity_returnsJwtClaims() throws Exception {
                Jwt jwt = Jwt.withTokenValue("token")
                                .header("alg", "RS256")
                                .claim("sub", "testuser")
                                .claim("name", "Test User")
                                .build();

                mockMvc.perform(get("/identity").with(jwt().jwt(jwt)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.sub").value("testuser"))
                                .andExpect(jsonPath("$.name").value("Test User"));
        }

        @Test
        void getEntitlements_returnsEntitlementsList() throws Exception {
                when(entitlementsService.getEntitlements(anyString())).thenReturn(List.of("Group1", "Group2"));

                Jwt jwt = Jwt.withTokenValue("token")
                                .header("alg", "RS256")
                                .claim("sub", "testuser")
                                .build();

                mockMvc.perform(get("/entitlements").with(jwt().jwt(jwt)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0]").value("Group1"))
                                .andExpect(jsonPath("$[1]").value("Group2"));
        }

        @Test
        void getEntitlements_returnsEmptyList() throws Exception {
                when(entitlementsService.getEntitlements(anyString())).thenReturn(List.of());

                Jwt jwt = Jwt.withTokenValue("token")
                                .header("alg", "RS256")
                                .claim("sub", "testuser")
                                .build();

                mockMvc.perform(get("/entitlements").with(jwt().jwt(jwt)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isEmpty());
        }
}