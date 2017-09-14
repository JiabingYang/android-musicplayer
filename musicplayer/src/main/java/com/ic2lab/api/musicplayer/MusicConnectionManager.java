package com.ic2lab.api.musicplayer;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.session.MediaControllerCompat;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author Jiabing Yang
 */
public class MusicConnectionManager {

    private static final int MY_PERMISSIONS_MUSIC = 0;

    private static final WeakHashMap<Activity, SimpleMusicConnection> mConnectionMap = new WeakHashMap<>();

    public static void connect(@NonNull Activity activity) {
        connect(activity, null);
    }

    public static void connect(@NonNull Activity activity, @Nullable MusicConnection musicConnection) {
        // get realActivity
        Activity realActivity = activity.getParent();
        if (realActivity == null)
            realActivity = activity;

        // check permissions
        if (ContextCompat.checkSelfPermission(realActivity, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(realActivity, new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.INTERNET,
                    Manifest.permission.WAKE_LOCK,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.ACCESS_NETWORK_STATE}, MY_PERMISSIONS_MUSIC);
        }

        // put realActivity into mConnectionMap
        SimpleMusicConnection mc = new SimpleMusicConnection(musicConnection, realActivity);
        boolean binded;
        if (binded = realActivity.bindService(new Intent(realActivity, MusicService.class), mc, Context.BIND_AUTO_CREATE)) {
            mConnectionMap.put(realActivity, mc);
        }
    }

    public static void unregisterCallback(Activity activity) {
        if (activity == null) {
            return;
        }
        final SimpleMusicConnection connection = mConnectionMap.get(activity);
        if (connection == null) {
            return;
        }
        _unregisterCallback(connection);
    }

    public static void disconnect(Activity activity) {
        if (activity == null) {
            return;
        }
        final SimpleMusicConnection connection = mConnectionMap.remove(activity);
        if (connection == null) {
            return;
        }
        _unregisterCallback(connection);
        activity.unbindService(connection);
    }

    public static MediaControllerCompat findMediaController() {
        for (SimpleMusicConnection connection : mConnectionMap.values()) {
            if (connection != null && connection.mController != null)
                return connection.mController;
        }
        return null;
    }

    public static Activity findControlActivity() {
        for (Map.Entry<Activity, SimpleMusicConnection> entry : mConnectionMap.entrySet()) {
            SimpleMusicConnection connection = entry.getValue();
            if (connection != null && connection.mController != null)
                return entry.getKey();
        }
        return null;
    }

    private static void _unregisterCallback(SimpleMusicConnection connection) {
        MediaControllerCompat.Callback callback = connection.mCallback;
        MediaControllerCompat controller = connection.mController;
        if (callback != null && controller != null)
            controller.unregisterCallback(callback);
    }

    private static class SimpleMusicConnection extends MusicConnection {
        private MusicConnection mConnection;
        private Activity mActivity;
        private MediaControllerCompat mController;
        private MediaControllerCompat.Callback mCallback;

        public SimpleMusicConnection(@Nullable MusicConnection connection, Activity activity) {
            this.mConnection = connection;
            this.mActivity = activity;
        }

        @Override
        public void onMediaControllerConnected(MediaControllerCompat mediaController) {
            if (mConnection != null)
                mConnection.onMediaControllerConnected(mediaController);
        }

        @Override
        public void onMusicConnectionError(Throwable e) {
            if (mConnection != null)
                mConnection.onMusicConnectionError(e);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (mConnection != null)
                mConnection.onServiceConnected(name, service);
            try {
                mController = new MediaControllerCompat(mActivity, ((MusicService.MusicServiceBinder) service).getToken());
                MediaControllerCompat.setMediaController(mActivity, mController);
                if (mCallback == null)
                    mCallback = onSetMediaControllerCallback();
                if (mCallback != null)
                    mController.registerCallback(mCallback);
                onMediaControllerConnected(mController);
            } catch (RemoteException e) {
                onMusicConnectionError(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (mConnection != null)
                mConnection.onServiceDisconnected(name);
        }

        @Override
        protected MediaControllerCompat.Callback onSetMediaControllerCallback() {
            return mConnection != null ? mConnection.onSetMediaControllerCallback() :
                    super.onSetMediaControllerCallback();
        }
    }
}
