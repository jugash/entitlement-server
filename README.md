# Keycloak Entitlements Service

This is a Spring Boot application that acts as an OAuth2 resource server, validating JWT tokens issued by Keycloak, and retrieves user entitlements (groups) from an LDAP directory (e.g., Active Directory).

## Prerequisites

- OpenShift cluster access.
- oc CLI installed.
- Docker or OpenShift build for the application image.
- Keycloak Operator installed in OpenShift (recommended) or manual Keycloak deployment.

## Step 1: Deploy Keycloak in OpenShift

Use the Keycloak Operator for easy deployment:

1. Install the Operator:

   ```
   oc apply -f https://raw.githubusercontent.com/keycloak/keycloak-quickstarts/latest/kubernetes/keycloaks.yaml
   ```

2. Create a Keycloak realm and instance. Create a `keycloak-realm.yaml`:

   ```
   apiVersion: keycloak.org/v1alpha1
   kind: KeycloakRealm
   metadata:
     name: myrealm
     labels:
       app: keycloak
   spec:
     realm:
       realm: myrealm
       enabled: true
       displayName: "My Realm"
       # Add clients, users, etc. here
   ---
   apiVersion: keycloak.org/v1alpha1
   kind: Keycloak
   metadata:
     name: keycloak
     labels:
       app: keycloak
   spec:
     instances: 1
     hostname:
       hostname: keycloak.example.com  # Optional, for external access
     ingress:
       enabled: true
       className: openshift-default-route
     db:
       vendor: postgres
       # Configure PostgreSQL if needed
   ```

3. Apply:
   ```
   oc apply -f keycloak-realm.yaml
   ```

Wait for Keycloak to be ready: `oc get pods -l app=keycloak`.

The internal service URL will be `http://keycloak:8080` within the namespace.

## Step 2: Configure Keycloak Client for the Application

Access Keycloak admin console (via Route: `oc get route keycloak`).

1. Login as admin (default: admin/admin, change immediately).
2. Create a client:
   - Client ID: `entitlements-service`
   - Client Protocol: openid-connect
   - Access Type: confidential (for resource server)
   - Valid Redirect URIs: `*` (for testing; restrict in prod)
   - Enable "Service Accounts Enabled" if needed for client credentials.
3. Note the client secret (for confidential clients).

For resource server, the app validates tokens, so ensure the client is configured for audience or use introspection if needed. Update `src/main/resources/application.yml` issuer-uri to match the realm.

## Step 2.5: Configure Microsoft AD as LDAP Identity Provider in Keycloak

To federate authentication with Microsoft Active Directory (AD), configure Keycloak to use AD as an LDAP-based identity provider. This allows users to authenticate against AD through Keycloak, and optionally sync groups to the JWT token for entitlements.

Access the Keycloak admin console (via OpenShift Route).

1. Navigate to **Identity Providers** > **Add provider** > **ldap**.

2. Configure the basic settings:

   - **Alias**: `microsoft-ad`
   - **Display Name**: `Microsoft AD`
   - **Enabled**: `true`
   - **Provider**: `ldap`
   - **Bind Type**: `simple`
   - **Bind DN**: `CN=Keycloak Service Account,OU=Service Accounts,DC=example,DC=com` (replace with your service account DN)
   - **Bind Credential**: (enter the password for the bind account)
   - **Edit Mode**: `READONLY`
   - **Base DN**: `DC=example,DC=com` (your AD base DN)
   - **Users DN**: `OU=Users,DC=example,DC=com` (optional, subdirectory for users)
   - **User Object Classes**: `user, person`
   - **Username LDAP attribute**: `sAMAccountName`
   - **RDN LDAP attribute**: `cn`
   - **UUID LDAP attribute**: `objectGUID` (or `entryUUID` if using OpenLDAP, but for AD use objectGUID)
   - **Custom User LDAP Attributes**: Add any additional attributes you want to sync (e.g., email, givenName)

3. Configure connection:

   - **Connection URL**: `ldaps://ad-server.example.com:636` (use LDAPS for secure connection)
   - **Users Scope**: `one` or `subtree`
   - **Use Truststore SPI**: `always` (if using custom CA for LDAPS)
   - **Custom Truststore**: Upload or reference your truststore if needed

4. For group synchronization:

   - **Groups DN**: `OU=Groups,DC=example,DC=com` (subdirectory for groups)
   - **Group Object Classes**: `group`
   - **Group Name LDAP attribute**: `cn`
   - **Group Membership Resolver**: `attribute` or `uid`
   - **User Attribute**: `memberOf`
   - **Group Attribute Name**: `member`
   - **Sync Mode**: `FORCE` (to sync groups on login)

5. Save the provider and **Test connection**.

6. Create mappers to include groups in the token:
   - Go to **Mappers** tab for the IdP > **Create** > **By configuration** > **User Attribute to Claim** or specifically for roles: **SAML Attribute to Role**.
   - For roles: Create a mapper "groups-to-roles"
     - Mapper Type: `User LDAP Attribute to Role`
     - LDAP Attribute: `memberOf`
     - Role Attribute Path: `$.[?(@ =~ "CN=([^,]+),.*" )].matches[0].groups[1]` (regex to extract CN from memberOf)
     - Token Claim Name: `realm_access.roles` (multivalued)
     - Claim JSON Type: `String`
     - Add to ID token: true
     - Add to access token: true
   - This maps AD groups to Keycloak roles in the JWT's `realm_access.roles` claim.

Now, users can login using AD credentials via Keycloak. The app will receive JWT with groups in the token.

Optionally, to avoid direct LDAP queries from the app (better for scalability), update the code to extract entitlements from the JWT claims instead of querying AD. See updated instructions in Step 3.

## Step 3: Update Application Configuration

The application uses Spring Security OAuth2 for JWT validation. With AD federated through Keycloak, authentication is handled by Keycloak, and entitlements (groups) can be extracted from the JWT token claims instead of direct LDAP queries. This avoids exposing AD directly to the app and improves performance.

1. Update `src/main/resources/application.yml` to remove LDAP configuration, as it's now handled by Keycloak:

   The LDAP section can be removed or commented out, since group sync happens in Keycloak.

   Example updated `application.yml`:

   ```
   server:
     port: 8080

   spring:
     security:
       oauth2:
         resourceserver:
           jwt:
             issuer-uri: http://keycloak:8080/realms/myrealm

   management:
     endpoints:
       web:
         exposure:
           include: health,info
   ```

2. Since LDAP is now federated through Keycloak, the app no longer needs direct LDAP access or keystores. Update Keycloak's LDAP IdP configuration with the necessary credentials and truststore (Keycloak handles the connection).

## Step 4: Update Deployment for OpenShift

The existing `deployment.yaml` is compatible with OpenShift. For LDAP, include secrets for passwords and mount keystores.

1. Add ConfigMap for application.yml (non-sensitive parts):
   Create `configmap.yaml`:

   ```
   apiVersion: v1
   kind: ConfigMap
   metadata:
     name: app-config
   data:
     application.yml: |
       server:
         port: 8080
       spring:
         security:
           oauth2:
             resourceserver:
               jwt:
                 issuer-uri: http://keycloak:8080/realms/myrealm
         ldap:
           urls: ldaps://ad-server.example.com:636
           base: dc=example,dc=com
           context-source:
             ssl:
               enabled: true
               keystore: /app/keystores/keystore.p12
               keystore-password: ${keystore-password}
               truststore: /app/keystores/truststore.p12
               truststore-password: ${truststore-password}
       management:
         endpoints:
           web:
             exposure:
               include: health,info
   ```

2. Create Secret for LDAP passwords:

   ```
   apiVersion: v1
   kind: Secret
   metadata:
     name: ldap-secrets
   type: Opaque
   data:
     keystore-password: <base64-encoded-password>
     truststore-password: <base64-encoded-password>
   ```

3. Create Secret for keystores:

   ```
   apiVersion: v1
   kind: Secret
   metadata:
     name: ldap-keystore-secret
   type: Opaque
   data:
     keystore.p12: <base64-encoded-keystore>
     truststore.p12: <base64-encoded-truststore>
   ```

4. Update `deployment.yaml` to use ConfigMap and Secret:

   ```
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: keycloak-entitlements-service
   spec:
     replicas: 1
     selector:
       matchLabels:
         app: keycloak-entitlements-service
     template:
       metadata:
         labels:
           app: keycloak-entitlements-service
       spec:
         containers:
         - name: keycloak-entitlements-service
           image: keycloak-entitlements-service:latest  # Build with oc new-build or Dockerfile
           ports:
           - containerPort: 8080
           envFrom:
           - configMapRef:
               name: app-config
           - secretRef:
               name: ldap-secrets
           volumeMounts:
           - name: keystore-volume
             mountPath: /app/keystores
             readOnly: true
         volumes:
         - name: keystore-volume
           secret:
             secretName: ldap-keystore-secret
   ---
   apiVersion: v1
   kind: Service
   metadata:
     name: keycloak-entitlements-service
   spec:
     selector:
       app: keycloak-entitlements-service
     ports:
     - protocol: TCP
       port: 8080
       targetPort: 8080
     type: ClusterIP
   ---
   apiVersion: route.openshift.io/v1
   kind: Route
   metadata:
     name: keycloak-entitlements-service
   spec:
     to:
       kind: Service
       name: keycloak-entitlements-service
     port:
       targetPort: 8080
   ```

5. Create the secrets:

   ```
   oc create secret generic ldap-secrets --from-literal=keystore-password=changeit --from-literal=truststore-password=changeit
   oc create secret generic ldap-keystore-secret --from-file=keystore.p12 --from-file=truststore.p12
   ```

6. Build and deploy image using Jib (no Dockerfile needed):
   - Set QUAY_USERNAME env var for your quay.io username (or use OpenShift registry).
   - Build and push: `./gradlew jib`
   - For local Docker: `./gradlew jibDockerBuild`
   - Update image in deployment.yaml to match: `quay.io/$QUAY_USERNAME/keycloak-entitlements-service:0.0.1-SNAPSHOT`
   - For OpenShift internal registry: Configure jib.to.image = "image-registry.openshift-image-registry.svc:5000/myproject/keycloak-entitlements-service:${project.version}"
   - Optionally, remove the Dockerfile as it's no longer needed.

Apply: `oc apply -f deployment.yaml -f configmap.yaml`

## Step 5: Code for LDAP Entitlements

The code already uses direct LDAP queries for entitlements after JWT authentication. Ensure the EntitlementsService uses the userId from the JWT sub claim.

The ApiController fetches userId from JWT and calls getEntitlements(userId).

Rebuild the application: `./gradlew build`

## Step 6: Test Integration

1. Deploy Keycloak and the application in the same namespace: `oc project myproject`

2. Get token from Keycloak using AD credentials (federated login):

   - Use the Keycloak login page (via Route) with AD username/password to get an ID token.
   - Or use client credentials if service account, but for user entitlements, simulate user login.

   Example for client credentials (if using service accounts):

   ```
   curl -X POST http://keycloak:8080/realms/myrealm/protocol/openid-connect/token \
     -d "client_id=entitlements-service" \
     -d "client_secret=<secret>" \
     -d "grant_type=client_credentials"
   ```

3. Call API with user token:

   ```
   curl -H "Authorization: Bearer <token>" http://keycloak-entitlements-service:8080/api/entitlements
   ```

4. Check logs: `oc logs deployment/keycloak-entitlements-service`

5. Verify in Keycloak admin: Users > View > Attributes to see synced groups.

## Step 7: Troubleshooting

- JWT validation fails: Verify issuer-uri matches Keycloak realm URL exactly (including port).
- LDAP connection fails: Ensure network access to AD from OpenShift, check secrets for passwords and keystores in logs.
- Keycloak auth fails: Verify issuer-uri and client config.
- HTTPS for AD: Ensure keystores are correct and mounted.
- For advanced: Use Keycloak's token exchange or introspection endpoint if needed.

## Building the Project

To build the JAR: `./gradlew build`

To build and push the container image with Jib: `./gradlew jib`

See `build.gradle` for dependencies (spring-boot-starter-oauth2-resource-server). LDAP dependencies can be removed if not used in tests.

## Helm Chart Deployment

A Helm chart is provided in the `helm/` directory for easy deployment on OpenShift.

1. Customize `helm/values.yaml`:

   - Set `image.repository` and `image.tag` to your built image.
   - Update `configmap.data.application.yml` for your Keycloak issuer-uri and other configs.
   - Adjust replicas, resources, etc.

2. Deploy:

   ```
   helm install keycloak-entitlements-service helm/ --namespace myproject --create-namespace
   ```

3. Verify:

   ```
   oc get route keycloak-entitlements-service -n myproject
   ```

4. For templating: `helm template keycloak-entitlements-service helm/ --debug`

The chart includes Deployment, Service, ConfigMap, ServiceAccount, and OpenShift Route.
