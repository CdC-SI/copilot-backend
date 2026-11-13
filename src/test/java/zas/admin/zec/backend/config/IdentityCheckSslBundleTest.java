package zas.admin.zec.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zas.admin.zec.backend.config.properties.IdentityCheckProperties;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.*;

class IdentityCheckSslBundleTest {

    @Test
    @DisplayName("Sans location, le keystore n'est pas configuré et le type par défaut est PKCS12")
    void keystoreNotConfiguredByDefault() {
        var properties = new IdentityCheckProperties("https://api", "http://callback", null);

        assertFalse(properties.keystore().isConfigured());
        assertEquals("PKCS12", properties.keystore().type());
        assertFalse(new IdentityCheckProperties.Keystore("", "secret", null).isConfigured());
    }

    @Test
    @DisplayName("Le bundle SSL est construit depuis un keystore PKCS12 hors classpath (file:)")
    void buildsSslBundleFromFileKeystore(@TempDir Path dir) throws Exception {
        Path p12 = dir.resolve("identity-check.p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        try (OutputStream out = Files.newOutputStream(p12)) {
            store.store(out, "secret".toCharArray());
        }
        var keystore = new IdentityCheckProperties.Keystore(p12.toUri().toString(), "secret", "PKCS12");

        var bundle = WebClientConfig.identityCheckSslBundle(keystore);

        assertTrue(keystore.isConfigured());
        assertNotNull(bundle.getStores().getKeyStore());
        assertEquals("PKCS12", bundle.getStores().getKeyStore().getType());
        assertNotNull(bundle.createSslContext());
    }
}
