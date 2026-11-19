package com.iuinventario.universal.estable;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

@CapacitorPlugin(name = "BackupPasswordVault")
public class BackupPasswordVaultPlugin extends Plugin {
    private static final String STORE = "iu_backup_vault";
    private static final String KEY_ALIAS = "iu_backup_password_aes_gcm_v1";

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        java.security.Key existing = ks.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());
        return generator.generateKey();
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(STORE, 0);
    }

    @PluginMethod
    public void hasPassword(PluginCall call) {
        JSObject out = new JSObject();
        out.put("configured", prefs().contains("iv") && prefs().contains("ciphertext"));
        call.resolve(out);
    }

    @PluginMethod
    public void storePassword(PluginCall call) {
        String password = call.getString("password");
        if (password == null || password.isEmpty()) {
            call.reject("La contraseña de cifrado está vacía.");
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            boolean saved = prefs().edit()
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .commit();
            if (!saved) throw new IllegalStateException("No se pudo guardar la contraseña protegida.");
            call.resolve();
        } catch (Exception e) {
            call.reject("No se pudo proteger la contraseña en Android: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void getPassword(PluginCall call) {
        try {
            String ivValue = prefs().getString("iv", null);
            String cipherValue = prefs().getString("ciphertext", null);
            if (ivValue == null || cipherValue == null) {
                call.resolve(new JSObject().put("configured", false));
                return;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, Base64.decode(ivValue, Base64.NO_WRAP)));
            byte[] plain = cipher.doFinal(Base64.decode(cipherValue, Base64.NO_WRAP));
            JSObject out = new JSObject();
            out.put("configured", true);
            out.put("password", new String(plain, StandardCharsets.UTF_8));
            call.resolve(out);
        } catch (Exception e) {
            call.reject("No se pudo recuperar la contraseña protegida de este dispositivo.", e);
        }
    }

    @PluginMethod
    public void clearPassword(PluginCall call) {
        try {
            boolean cleared = prefs().edit().clear().commit();
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS);
            if (!cleared) throw new IllegalStateException("No se pudo borrar la contraseña protegida.");
            call.resolve();
        } catch (Exception e) {
            call.reject("No se pudo borrar la contraseña protegida.", e);
        }
    }
}
