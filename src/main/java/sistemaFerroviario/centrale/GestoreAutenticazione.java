package sistemaFerroviario.centrale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Integrazione Keycloak per ambiente dimostrativo locale. */
public class GestoreAutenticazione {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ISSUER = System.getenv().getOrDefault("KEYCLOAK_ISSUER", "https://localhost:8443/realms/ferrovia");
    private static final String CLIENT = System.getenv().getOrDefault("KEYCLOAK_CLIENT_ID", "ferrovia-web");
    private static final HttpClient HTTP = creaClient();

    /** Client HTTPS che si fida della CA locale del progetto (infra/mosquitto/certs/ca.crt). */
    private static HttpClient creaClient() {
        try {
            java.nio.file.Path ca = java.nio.file.Path.of(
                    System.getenv().getOrDefault("TLS_CA_FILE", "infra/mosquitto/certs/ca.crt"));
            if (!java.nio.file.Files.exists(ca)) return HttpClient.newHttpClient();
            java.security.cert.Certificate cert;
            try (var in = java.nio.file.Files.newInputStream(ca)) {
                cert = java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in);
            }
            var ks = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("ferrovia-ca", cert);
            var tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                    javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            var ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return HttpClient.newBuilder().sslContext(ctx).build();
        } catch (Exception e) {
            throw new IllegalStateException("CA TLS non caricabile: " + e.getMessage(), e);
        }
    }
    private volatile JWKSet keys;
    private volatile long keysFetchedAt;

    /** Usa Direct Access Grants: abilitarlo SOLO per la demo locale. */
    public String autentica(String username, String password) {
        if (username == null || password == null) return null;
        try {
            String body = "grant_type=password&client_id=" + encode(CLIENT)
                + "&username=" + encode(username) + "&password=" + encode(password);
            HttpRequest req = HttpRequest.newBuilder(URI.create(ISSUER + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            String token = JSON.readTree(response.body()).path("access_token").asText(null);
            return getRuolo(token) == null ? null : token;
        } catch (Exception e) {
            System.err.println("[Keycloak] Login non disponibile: " + e.getMessage());
            return null;
        }
    }

    /** Valida il token e ricava il ruolo applicativo associato all utente. */
    public String getRuolo(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) return null;
            JWK jwk = jwks().getKeyByKeyId(jwt.getHeader().getKeyID());
            if (!(jwk instanceof RSAKey rsa) || !jwt.verify(new RSASSAVerifier(rsa.toRSAPublicKey()))) return null;
            var claims = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(claims.getIssuer())) return null;
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.after(new Date())) return null;
            Date notBefore = claims.getNotBeforeTime();
            if (notBefore != null && notBefore.after(new Date())) return null;
            if (!CLIENT.equals(claims.getStringClaim("azp"))) return null;
            JsonNode payload = JSON.readTree(jwt.getPayload().toString());
            JsonNode roles = payload.path("realm_access").path("roles");
            if (roles.isArray()) {
                for (JsonNode role : roles) {
                    if ("AMMINISTRATORE".equals(role.asText())) return "AMMINISTRATORE";
                    if ("TECNICO".equals(role.asText())) return "TECNICO";
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    public boolean haRuolo(String token, String role) { return role.equals(getRuolo(token)); }
    public void disconnetti(String token) { /* JWT stateless: il browser elimina il token. */ }

    private synchronized JWKSet jwks() throws Exception {
        if (keys != null && Instant.now().getEpochSecond() - keysFetchedAt < 60) return keys;
        HttpRequest request = HttpRequest.newBuilder(
            URI.create(ISSUER + "/protocol/openid-connect/certs")).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("JWKS non disponibile");
        keys = JWKSet.parse(response.body());
        keysFetchedAt = Instant.now().getEpochSecond();
        return keys;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
