package com.iuinventario.universal.estable;

import android.Manifest;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Size;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@CapacitorPlugin(name = "BarcodeScanner", permissions = {@Permission(alias = "camera", strings = {Manifest.permission.CAMERA})})
public class BarcodeScannerPlugin extends Plugin {
    private ExecutorService analysisExecutor;
    private ProcessCameraProvider provider;
    private Camera camera;
    private android.app.Dialog dialog;
    private PreviewView previewView;
    private TextView statusView;
    private boolean delivered = false;
    private boolean torchOn = false;
    private String recentCode = "";
    private int repeatedCodeFrames = 0;

    @PluginMethod
    public void scan(PluginCall call) {
        if (getPermissionState("camera") != PermissionState.GRANTED) {
            requestPermissionForAlias("camera", call, "cameraPermissionResult");
            return;
        }
        beginScan(call);
    }

    @PermissionCallback
    private void cameraPermissionResult(PluginCall call) {
        if (getPermissionState("camera") != PermissionState.GRANTED) {
            call.reject("Se necesita permiso de cámara para escanear.");
            return;
        }
        beginScan(call);
    }

    private int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }

    private TextView text(Context c, String value, int size, int color) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER); return t;
    }

    private void beginScan(PluginCall call) {
        delivered = false; torchOn = false; recentCode = ""; repeatedCodeFrames = 0;
        Context context = getContext();
        dialog = new android.app.Dialog(getActivity());
        LinearLayout root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_HORIZONTAL); root.setPadding(dp(context,16),dp(context,14),dp(context,16),dp(context,12)); root.setBackgroundColor(Color.rgb(14,18,20));
        TextView title = text(context,"Escanear código de barras",20,Color.WHITE); title.setTypeface(null, Typeface.BOLD); root.addView(title,new LinearLayout.LayoutParams(-1,dp(context,42)));
        TextView hint = text(context,"Centra el código en el recuadro. Toca sobre él para enfocar.",14,Color.LTGRAY); root.addView(hint,new LinearLayout.LayoutParams(-1,dp(context,48)));
        FrameLayout frame = new FrameLayout(context); previewView = new PreviewView(context); previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE); frame.addView(previewView,new FrameLayout.LayoutParams(-1,-1));
        View guide = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas); float d=getResources().getDisplayMetrics().density; float left=dp(context,24),right=getWidth()-left,top=getHeight()*.36f,bottom=getHeight()*.64f,corner=dp(context,18);
                paint.setColor(Color.argb(220,255,255,255));paint.setStrokeWidth(dp(context,2));paint.setStyle(Paint.Style.STROKE);
                canvas.drawRoundRect(new RectF(left,top,right,bottom),dp(context,10),dp(context,10),paint);
                canvas.drawLine(left,top,left+corner,top,paint);canvas.drawLine(left,top,left,top+corner,paint);canvas.drawLine(right,top,right-corner,top,paint);canvas.drawLine(right,top,right,top+corner,paint);
                canvas.drawLine(left,bottom,left+corner,bottom,paint);canvas.drawLine(left,bottom,left,bottom-corner,paint);canvas.drawLine(right,bottom,right-corner,bottom,paint);canvas.drawLine(right,bottom,right,bottom-corner,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setColor(Color.argb(130,255,40,40));paint.setStrokeWidth(dp(context,5));canvas.drawLine(left+dp(context,3),getHeight()/2f,right-dp(context,3),getHeight()/2f,paint);
                paint.setColor(Color.rgb(255,32,32));paint.setStrokeWidth(dp(context,1));canvas.drawLine(left+dp(context,3),getHeight()/2f,right-dp(context,3),getHeight()/2f,paint);
            }
        };
        guide.setBackgroundColor(Color.TRANSPARENT);frame.addView(guide,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,0,1);fp.topMargin=dp(context,8);fp.bottomMargin=dp(context,8);root.addView(frame,fp);
        SeekBar zoom=new SeekBar(context);zoom.setMax(100);zoom.setProgress(0);root.addView(zoom,new LinearLayout.LayoutParams(-1,dp(context,42)));
        LinearLayout controls=new LinearLayout(context);controls.setGravity(Gravity.CENTER);controls.setOrientation(LinearLayout.HORIZONTAL);
        Button torch=new Button(context);torch.setText("Linterna");Button cancel=new Button(context);cancel.setText("Cancelar");controls.addView(torch,new LinearLayout.LayoutParams(0,dp(context,48),1));controls.addView(cancel,new LinearLayout.LayoutParams(0,dp(context,48),1));root.addView(controls,new LinearLayout.LayoutParams(-1,dp(context,52)));
        statusView=text(context,"Iniciando cámara…",14,Color.LTGRAY);root.addView(statusView,new LinearLayout.LayoutParams(-1,dp(context,38)));
        dialog.setContentView(root);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-1);w.setStatusBarColor(Color.BLACK);w.setNavigationBarColor(Color.BLACK);}dialog.setCancelable(true);dialog.setOnCancelListener(d->{finishCancelled(call);});
        cancel.setOnClickListener(v->{finishCancelled(call);});torch.setOnClickListener(v->{if(camera!=null&&camera.getCameraInfo().hasFlashUnit()){torchOn=!torchOn;camera.getCameraControl().enableTorch(torchOn);torch.setText(torchOn?"Linterna encendida":"Linterna");}});
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){if(user&&camera!=null){androidx.camera.core.ZoomState z=camera.getCameraInfo().getZoomState().getValue();if(z!=null){float ratio=z.getMinZoomRatio()+(z.getMaxZoomRatio()-z.getMinZoomRatio())*p/100f;camera.getCameraControl().setZoomRatio(ratio);}}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        previewView.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP&&camera!=null){MeteringPoint point=previewView.getMeteringPointFactory().createPoint(e.getX(),e.getY());FocusMeteringAction action=new FocusMeteringAction.Builder(point,FocusMeteringAction.FLAG_AF|FocusMeteringAction.FLAG_AE).setAutoCancelDuration(3,TimeUnit.SECONDS).build();camera.getCameraControl().startFocusAndMetering(action);return true;}return true;});
        dialog.show();if(w!=null)w.setLayout(-1,-1);
        analysisExecutor=Executors.newSingleThreadExecutor();
        BarcodeScannerOptions options=new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13,Barcode.FORMAT_EAN_8,Barcode.FORMAT_UPC_A,Barcode.FORMAT_UPC_E,Barcode.FORMAT_CODE_128,Barcode.FORMAT_CODE_39,Barcode.FORMAT_ITF,Barcode.FORMAT_CODABAR).setZoomSuggestionOptions(new ZoomSuggestionOptions.Builder(ratio->{if(camera!=null){androidx.camera.core.ZoomState z=camera.getCameraInfo().getZoomState().getValue();if(z!=null){camera.getCameraControl().setZoomRatio(Math.max(z.getMinZoomRatio(),Math.min(z.getMaxZoomRatio(),ratio)));return true;}}return false;}).setMaxSupportedZoomRatio(8f).build()).build();
        com.google.mlkit.vision.barcode.BarcodeScanner detector=BarcodeScanning.getClient(options);
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(context);
        future.addListener(()->{try{if(delivered||dialog==null||!dialog.isShowing())return;provider=future.get();Preview preview=new Preview.Builder().build();preview.setSurfaceProvider(previewView.getSurfaceProvider());ImageAnalysis analysis=new ImageAnalysis.Builder().setTargetResolution(new Size(1280,720)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();analysis.setAnalyzer(analysisExecutor,image->{android.media.Image media=image.getImage();if(media==null){image.close();return;}InputImage input=InputImage.fromMediaImage(media,image.getImageInfo().getRotationDegrees());detector.process(input).addOnSuccessListener(barcodes->{if(delivered||barcodes.isEmpty())return;Barcode found=barcodes.get(0);String value=found.getRawValue();if(value!=null&&!value.isEmpty()){if(value.equals(recentCode))repeatedCodeFrames++;else{recentCode=value;repeatedCodeFrames=1;}if(repeatedCodeFrames>=2){delivered=true;JSObject result=new JSObject();result.put("barcode",value);result.put("format",found.getFormat());call.resolve(result);closeCamera();}}}).addOnFailureListener(err->{if(statusView!=null)statusView.setText("No se pudo leer; prueba acercar o enfocar.");}).addOnCompleteListener(task->image.close());});camera=provider.bindToLifecycle((LifecycleOwner)getActivity(),CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis);androidx.camera.core.ZoomState state=camera.getCameraInfo().getZoomState().getValue();if(state!=null)zoom.setEnabled(state.getMaxZoomRatio()>1.01f);statusView.setText("Acerca el código, ajusta el zoom o toca para enfocar.");}catch(Exception ex){statusView.setText("No se pudo iniciar la cámara. Cierra y vuelve a abrir el lector.");}},ContextCompat.getMainExecutor(context));
    }

    private void finishCancelled(PluginCall call){if(!delivered){delivered=true;JSObject result=new JSObject();result.put("barcode","");call.resolve(result);}closeCamera();}
    private void closeCamera(){try{if(provider!=null)provider.unbindAll();}catch(Exception ignored){}camera=null;if(analysisExecutor!=null){analysisExecutor.shutdownNow();analysisExecutor=null;}if(dialog!=null&&dialog.isShowing())dialog.dismiss();dialog=null;previewView=null;statusView=null;}
    @Override protected void handleOnDestroy(){closeCamera();super.handleOnDestroy();}
}
