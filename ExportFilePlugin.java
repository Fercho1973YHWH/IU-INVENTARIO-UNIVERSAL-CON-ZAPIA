package com.iuinventario.universal.estable;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
    public void pickFile(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel", "text/html", "application/json", "text/csv", "text/*"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(call, intent, "handlePickResult");
    }

    @ActivityCallback
    private void handlePickResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            JSObject ret = new JSObject();
            ret.put("cancelled", true);
            call.resolve(ret);
            return;
        }
        Uri uri = result.getData().getData();
        try {
            String name = "productos.xlsx";
            try (Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0 && cursor.getString(index) != null) name = cursor.getString(index);
                }
            }
            String mime = getContext().getContentResolver().getType(uri);
            if (mime == null) mime = "application/octet-stream";
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("Android no pudo abrir el archivo seleccionado.");
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
            }
            JSObject ret = new JSObject();
            ret.put("name", name);
            ret.put("mimeType", mime);
            ret.put("data", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
            ret.put("cancelled", false);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("No se pudo leer el archivo seleccionado: " + e.getMessage(), e);
        }
    }

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
