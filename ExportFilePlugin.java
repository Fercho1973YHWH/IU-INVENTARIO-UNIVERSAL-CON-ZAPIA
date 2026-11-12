package com.iuinventario.universal.estable;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;

import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.OutputStream;

@CapacitorPlugin(name = "ExportFile")
public class ExportFilePlugin extends Plugin {
    @PluginMethod
    public void saveFile(PluginCall call) {
        String name = call.getString("name");
        String mime = call.getString("mimeType", "application/octet-stream");
        String data = call.getString("data");
        if (name == null || name.trim().isEmpty() || data == null) {
            call.reject("Faltan el nombre o el contenido del archivo.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(call, intent, "handleSaveResult");
    }

    @ActivityCallback
    private void handleSaveResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.reject("Se canceló el guardado.");
            return;
        }
        String encoded = call.getString("data");
        Uri uri = result.getData().getData();
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            OutputStream out = getContext().getContentResolver().openOutputStream(uri, "w");
            if (out == null) throw new IllegalStateException("Android no pudo abrir el archivo seleccionado.");
            try (OutputStream stream = out) {
                stream.write(bytes);
                stream.flush();
            }
            JSObject ret = new JSObject();
            ret.put("uri", uri.toString());
            ret.put("saved", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("No se pudo escribir el archivo seleccionado: " + e.getMessage(), e);
        }
    }
}
