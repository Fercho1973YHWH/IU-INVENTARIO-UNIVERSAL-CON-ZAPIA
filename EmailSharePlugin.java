package com.iuinventario.universal.estable;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;

@CapacitorPlugin(name = "EmailShare")
public class EmailSharePlugin extends Plugin {
    @PluginMethod
    public void shareEncryptedBackup(PluginCall call) {
        String recipient = call.getString("recipient");
        String subject = call.getString("subject");
        String body = call.getString("body");
        String fileUri = call.getString("fileUri");
        String fileName = call.getString("fileName");
        if (recipient == null || recipient.trim().isEmpty() || subject == null || body == null || fileUri == null || fileName == null) {
            call.reject("Faltan datos para preparar el correo con la copia cifrada.");
            return;
        }
        try {
            Uri source = Uri.parse(fileUri);
            if (!"file".equalsIgnoreCase(source.getScheme())) {
                call.reject("La copia temporal segura no está disponible como archivo local.");
                return;
            }
            File file = new File(source.getPath());
            if (!file.isFile() || !file.getCanonicalPath().startsWith(getContext().getCacheDir().getCanonicalPath() + File.separator)) {
                call.reject("El archivo cifrado no está dentro del almacenamiento temporal privado.");
                return;
            }
            Uri contentUri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_EMAIL, new String[] { recipient.trim() });
            send.putExtra(Intent.EXTRA_SUBJECT, subject);
            send.putExtra(Intent.EXTRA_TEXT, body);
            send.putExtra(Intent.EXTRA_STREAM, contentUri);
            send.setClipData(ClipData.newUri(getContext().getContentResolver(), fileName, contentUri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent launch;
            Intent gmail = new Intent(Intent.ACTION_SEND);
            gmail.setPackage("com.google.android.gm");
            gmail.setType("application/json");
            if (getContext().getPackageManager().resolveActivity(gmail, 0) != null) {
                send.setPackage("com.google.android.gm");
                launch = send;
            } else {
                launch = Intent.createChooser(send, "Elegir correo para revisar y enviar la copia cifrada");
                launch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            getActivity().startActivity(launch);
            call.resolve();
        } catch (Exception e) {
            call.reject("No se pudo abrir el correo con el adjunto cifrado: " + e.getMessage(), e);
        }
    }
}
