package com.ic2lab.api.musicplayer;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.List;

public class MusicService extends Service implements IMusicService, MusicPlaybackManager.PlaybackServiceCallback {

    // The action of the incoming Intent indicating that it contains a command
    // to be executed (see {@link #onStartCommand})
    public static final String ACTION_CMD = "ACTION_CMD";
    // The key in the extras of the incoming Intent indicating the command that
    // should be executed (see {@link #onStartCommand})
    public static final String CMD_NAME = "CMD_NAME";
    // A value of a CMD_NAME key in the extras of the incoming Intent that
    // indicates that the music playback should be paused (see {@link #onStartCommand})
    public static final String CMD_PAUSE = "CMD_PAUSE";
    private static final String TAG = MusicService.class.getSimpleName();
    // Delay stopSelf by using a handler.
    private static final int STOP_DELAY = 2000;

    private final DelayedStopHandler mDelayedStopHandler = new DelayedStopHandler(this);
    private MusicServiceBinder mBinder = new MusicServiceBinder();
    private MediaSessionCompat mSession;
    private MusicNotificationManager mMusicNotificationManager;
    private MediaSessionCompat.Token mSessionToken;
    private MusicPlaybackManager mMusicPlaybackManager;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mMusicPlaybackManager = new MusicPlaybackManager(this, new QueueManager(new QueueManager.MetadataUpdateListener() {
            @Override
            public void onMetadataUpdate(MediaMetadataCompat metadata) {
                // update the playing item
                mSession.setMetadata(metadata);
            }

            @Override
            public void onQueueUpdated(String title, List<MediaSessionCompat.QueueItem> queue) {
                // update the playing queue
                mSession.setQueue(queue);
                mSession.setQueueTitle(title);
            }

            @Override
            public void onMetadataRetrieveError() {
                mMusicPlaybackManager.updatePlaybackState(getString(R.string.error_no_metadata));
            }

            @Override
            public void onCurrentQueueIndexUpdated(int queueIndex) {
                mMusicPlaybackManager.handlePlayRequest();
            }
        }), new LocalMusicPlayback(this));

        // Init MediaSession.
        mSession = new MediaSessionCompat(this, TAG);
        mSessionToken = mSession.getSessionToken();
        mSession.setCallback(mMusicPlaybackManager.getMediaSessionCallback());
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

//        Context context = getApplicationContext();
//        Intent intent = new Intent(context, PlaybackActivity.class);
//        PendingIntent pi = PendingIntent.getActivity(context, 99 /*request code to distinguish between different intent*/,
//                intent, PendingIntent.FLAG_UPDATE_CURRENT);
//        mSession.setSessionActivity(pi);

        mMusicPlaybackManager.updatePlaybackState(null);

        if (MusicConfig.get().isNotificationEnabled()) {
            MusicConfig.get().getNotificationManagerCreator().accept(this, new MusicConfig.MusicNotificationManagerCallback() {
                @Override
                public void onMusicNotificationManager(MusicNotificationManager musicNotificationManager) {
                    mMusicNotificationManager = musicNotificationManager;
                }

                @Override
                public void onError(Throwable e) {
                    throw new IllegalStateException("Could not create a ElightNotificationManager", e);
                }
            });
        }
    }

    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        if (startIntent != null) {
            String action = startIntent.getAction();
            String command = startIntent.getStringExtra(CMD_NAME);
            if (ACTION_CMD.equals(action)) {
                if (CMD_PAUSE.equals(command)) {
                    mMusicPlaybackManager.handlePauseRequest();
                }
            } else {
                // Try to handle the intent as a media button event wrapped by MediaButtonReceiver
                MediaButtonReceiver.handleIntent(mSession, startIntent);
            }
        }
        // Reset the delay handler to enqueue a message to stop the service if nothing is playing.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Log.w(TAG, "onStartCommand: mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);");
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "onDestroy");
        Log.w(TAG, "handleStopRequest(null) called from onDestroy()");
        mMusicPlaybackManager.handleStopRequest(null);
        if (mMusicNotificationManager != null)
            mMusicNotificationManager.stopNotification();
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mSession.release();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind for MusicService");
        return super.onUnbind(intent);
    }

    /**
     * Callback method called from MusicPlaybackManager whenever the music is about to play.
     * start MusicService
     */
    @Override
    public void onPlaybackStart() {
        Log.d(TAG, "onPlaybackStart()");
        if (!mSession.isActive()) {
            mSession.setActive(true);
        }
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        startService(new Intent(getApplicationContext(), MusicService.class));
    }

    @Override
    public void onPlaybackStop() {
        Log.w(TAG, "onPlaybackStop()");
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Log.w(TAG, "onPlaybackStop: mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);");
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
        stopForeground(true);
    }

    @Override
    public void onNotificationRequired() {
        if (mMusicNotificationManager != null)
            mMusicNotificationManager.startNotification();
    }

    @Override
    public void onPlaybackStateUpdated(PlaybackStateCompat newState) {
        mSession.setPlaybackState(newState);
    }

    @Override
    public void onPlaybackModeUpdated(String playbackMode) {
        Bundle bundle = new Bundle();
        bundle.putString(MusicConsts.PLAYBACK_MODE, playbackMode);
        mSession.setExtras(bundle);
    }

    @Override
    public MediaSessionCompat.Token getSessionToken() {
        return mSessionToken;
    }

    @Override
    public Service getServiceContext() {
        return this;
    }

    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private static class DelayedStopHandler extends Handler {
        private final WeakReference<MusicService> mWeakReference;

        private DelayedStopHandler(MusicService service) {
            mWeakReference = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MusicService service = mWeakReference.get();
            if (service != null && service.mMusicPlaybackManager.getPlayback() != null) {
                if (service.mMusicPlaybackManager.getPlayback().getState() != PlaybackStateCompat.STATE_STOPPED) {
                    Log.d(TAG, "Ignoring delayed stop since the media player is in use.");
                    return;
                }
                Log.d(TAG, "Stopping service with delay handler.");
                service.stopSelf();
            }
        }
    }

    class MusicServiceBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }

        MediaSessionCompat.Token getToken() {
            return mSessionToken;
        }
    }

}
