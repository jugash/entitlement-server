package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementsServiceTest {

    @Mock
    private LdapTemplate ldapTemplate;

    private EntitlementsService entitlementsService;

    @BeforeEach
    void setUp() {
        entitlementsService = new EntitlementsService(ldapTemplate);
    }

    @Test
    void getEntitlements_returnsGroupsFromMemberOf() {
        String userId = "testuser";

        when(ldapTemplate.search(anyString(), eq("(sAMAccountName=" + userId + ")"), any(AttributesMapper.class)))
                .thenReturn(List.of(List.of("Group1", "Group2")));

        List<String> result = entitlementsService.getEntitlements(userId);

        assertEquals(List.of("Group1", "Group2"), result);
    }

    @Test
    void getEntitlements_returnsEmptyListIfNoMemberOf() {
        String userId = "testuser";

        when(ldapTemplate.search(anyString(), eq("(sAMAccountName=" + userId + ")"), any(AttributesMapper.class)))
                .thenReturn(List.of(List.of()));

        List<String> result = entitlementsService.getEntitlements(userId);

        assertEquals(List.of(), result);
    }
}