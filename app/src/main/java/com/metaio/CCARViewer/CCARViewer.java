package com.metaio.CCARViewer;

import java.io.File;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import com.metaio.sdk.ARViewActivity;
import com.metaio.sdk.MetaioDebug;
import com.metaio.sdk.jni.IGeometry;
import com.metaio.sdk.jni.IMetaioSDKCallback;
import com.metaio.sdk.jni.Rotation;
import com.metaio.sdk.jni.TrackingValues;
import com.metaio.sdk.jni.TrackingValuesVector;
import com.metaio.sdk.jni.Vector3d;
import com.metaio.tools.io.AssetsManager;

public class CCARViewer extends ARViewActivity {
    private IGeometry CCModel;
    private MetaioSDKCallbackHandler mSDKCallback;
    boolean result;
    private String QRname;
    String Model = null;
    boolean flag = false;
    Vibrator vibrator = null;
    boolean mode = false;
    MediaPlayer mediaPlayer = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mSDKCallback = new MetaioSDKCallbackHandler();

        // Enable see through mode (e.g. on glasses)
        metaioSDK.setStereoRendering(true);
        metaioSDK.setSeeThrough(false);
        metaioSDK.setSeeThroughColor(0, 0, 0, 255);
        // Advanced Rendering features
        metaioSDK.autoEnableAdvancedRenderingFeatures();
        metaioSDK.setDepthOfFieldParameters(0.1f, 0.6f, 2.0f);
        metaioSDK.setMotionBlurIntensity(0.5f);

        File trackingConfigFile = AssetsManager.getAssetPathAsFile(getApplicationContext(), "TrackingData_MarkerlessFast.xml");
        result = metaioSDK.setTrackingConfiguration("QRCODE");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSDKCallback.delete();
        mSDKCallback = null;
    }

    @Override
    protected int getGUILayout() {
        // Attaching layout to the activity
        return R.layout.template;
    }

    @Override
    protected void loadContents() {

        final File calibrationFile = AssetsManager.getAssetPathAsFile(this, "TutorialStereoRendering/Assets/hec.xml");

        if ((calibrationFile == null || !metaioSDK.setHandEyeCalibrationFromFile(calibrationFile)) &&
                !metaioSDK.setHandEyeCalibrationFromFile()) {
            metaioSDK.setHandEyeCalibrationByDevice();
        }
        MetaioDebug.log("Tracking data loaded: " + result);
    }

    @Override
    protected void onGeometryTouched(IGeometry geometry) {
        MetaioDebug.log("CCARViewer.onGeometryTouched: " + geometry);
    }

    @Override
    protected IMetaioSDKCallback getMetaioSDKCallbackHandler() {
        return mSDKCallback;
    }

    final class MetaioSDKCallbackHandler extends IMetaioSDKCallback {
        TrackingValues v = null;

        @Override
        public void onTrackingEvent(TrackingValuesVector trackingValues) {
            for (int i = 0; i < trackingValues.size(); i++) {
                v = trackingValues.get(i);
            }
            if (TrackingValues.isTrackingState(v.getState())) {
                QRname = v.getAdditionalValues().replaceFirst("QR_CODE::", "");
            }
            if (QRname != null && !flag) {
                downloadModel();
            }
        }
    }

    @Override //Override per la pressione del pulsante Klicky
    public boolean onKeyDown(int keycode, KeyEvent e) {
        switch (keycode) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
                return switchMode();
            case KeyEvent.KEYCODE_BACK:
                File file = new File("/storage/emulated/0/Download/model.zip");
                boolean deleted = file.delete();
        }
        return super.onKeyDown(keycode, e);
    }


    //Funzioni di utilità
    void downloadModel() {
        mSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                vibrator.vibrate(80);
                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(QRname)); // <- Da sostituire con QRNAME
                request.setDescription("Downloading from " + QRname); // <- precedentemente c'era url
                request.setTitle("Downloading model");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    request.allowScanningByMediaScanner();
                    //request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                }
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "model.zip");
                // get download service and enqueue file
                manager.enqueue(request);
                flag = true;
                mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.downloadiniziato);
                mediaPlayer.start();
            }
        });
    }

    //Receiver per il download completato
    BroadcastReceiver onComplete = new BroadcastReceiver() {
        public void onReceive(Context ctxt, Intent intent) {
            mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.downloadcompletato);
            mediaPlayer.start();
        }
    };

    //Switch mode tra QRCODE MODE e RENDERING MODE
    boolean switchMode() {
        if (!mode) {
            mSurfaceView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    // load the items here
                    mode = true;
                    vibrator.vibrate(100);
                    File trackingConfigFile = AssetsManager.getAssetPathAsFile(getApplicationContext(), "TrackingData_MarkerlessFast.xml");
                    boolean result = metaioSDK.setTrackingConfiguration(trackingConfigFile);
                    System.err.println("TrackingConfigFile impostato");
                    mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.renderingmode);
                    mediaPlayer.start();
                    Model = "/storage/emulated/0/Download/model.zip";
                    System.err.println(Model);
                    final float scale = 11.f;
                    final Rotation rotation = new Rotation(new Vector3d(0, 0.0f, 0.0f));
                    if (Model != null) {
                        // Loading 3D geometry
                        CCModel = metaioSDK.createGeometry(Model);
                        if (CCModel != null) {
                            //Proprietà geometriche
                            CCModel.setScale(scale / 5);
                            CCModel.setRotation(rotation);
                        } else
                            MetaioDebug.log(Log.ERROR, "Errore nella lettura del modello geometrico: " + CCModel);
                    }
                }
            });
            return true;
        }
        if (mode) {
            mSurfaceView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    // load the items here
                    mode = false;
                    vibrator.vibrate(100);
                    File trackingConfigFile = AssetsManager.getAssetPathAsFile(getApplicationContext(), "TrackingData_MarkerlessFast.xml");
                    boolean result = metaioSDK.setTrackingConfiguration("QRCODE");
                    mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.qrcodemode);
                    mediaPlayer.start();
                    flag = false;
                    QRname = null;
                    metaioSDK.unloadGeometry(CCModel);
                    File file = new File("/storage/emulated/0/Download/model.zip");
                    boolean deleted = file.delete();
                }
            });
            return true;
        } else return false;
    }
}
