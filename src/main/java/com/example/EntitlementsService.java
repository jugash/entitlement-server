package com.example;

import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Service;

import javax.naming.directory.Attribute;
import java.util.ArrayList;
import java.util.List;

@Service
public class EntitlementsService {

    private final LdapTemplate ldapTemplate;

    public EntitlementsService(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }

    public List<String> getEntitlements(String userId) {
        // Search for user and extract memberOf groups (entitlements)
        String searchFilter = "(sAMAccountName=" + userId + ")";

        try {
            List<List<String>> results = ldapTemplate.search("", searchFilter,
                    (AttributesMapper<List<String>>) attrs -> {
                        List<String> groups = new ArrayList<>();
                        Attribute memberOf = attrs.get("memberOf");
                        if (memberOf != null) {
                            for (int i = 0; i < memberOf.size(); i++) {
                                String groupDn = (String) memberOf.get(i);
                                // Extract group name from DN, e.g., CN=group,OU=...
                                String groupName = groupDn.split(",")[0].replace("CN=", "");
                                groups.add(groupName);
                            }
                        }
                        return groups;
                    });
            if (!results.isEmpty()) {
                return results.get(0);
            }
        } catch (Exception e) {
            // Log error, return empty for now
            e.printStackTrace();
        }
        return List.of();
    }

}