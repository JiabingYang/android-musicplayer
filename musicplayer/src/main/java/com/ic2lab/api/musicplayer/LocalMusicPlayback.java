package com.ic2lab.api.musicplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;

import static android.media.MediaPlayer.OnCompletionListener;
import static android.media.MediaPlayer.OnErrorListener;
import static android.media.MediaPlayer.OnPreparedListener;
import static android.media.MediaPlayer.OnSeekCompleteListener;

/**
 * A class that implements local audio media playback using {@link android.media.MediaPlayer}
 */

class LocalMusicPlayback implements MusicPlayback, AudioManager.OnAudioFocusChangeListener,
        OnCompletionListener, OnErrorListener, OnPreparedListener, OnSeekCompleteListener {

    // The volume we set the media player to when we lose audio focus, but are
    // allowed to reduce the volume instead of stopping playback.
    private static final float VOLUME_DUCK = 0.2f;
    // The volume we set the media player when we have audio focus.
    private static final float VOLUME_NORMAL = 1.0f;
    private static final String TAG = LocalMusicPlayback.class.getSimpleName();
    // we don't have audio focus, and can't duck (play at a low volume)
    private static final int AUDIO_NO_FOCUS_NO_DUCK = 0;
    // we don't have focus, but can duck (play at a low volume)
    private static final int AUDIO_NO_FOCUS_CAN_DUCK = 1;
    // we have full audio focus
    private static final int AUDIO_FOCUSED = 2;
    private final WifiManager.WifiLock mWifiLock;
    private final AudioManager mAudioManager;
    private final IntentFilter mAudioNoisyIntentFilter =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private final IntentFilter mPhoneStateIntentFilter =
            new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
    private Context mContext;
    private int mState;
    private boolean mPlayOnFocusGain;
    private Callback mCallback;
    private volatile boolean mAudioNoisyReceiverRegistered;
    private volatile boolean mPhoneStateReceiverRegistered;
    private volatile int mCurrentPosition;
    private volatile String mCurrentMediaId;
    // Type of audio focus we have:
    private int mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK;
    private MediaPlayer mMediaPlayer;
    private final BroadcastReceiver mAudioNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                Log.d(TAG, "Headphones disconnected.");
                sendPauseCmdIfPlaying(context);
            }
        }
    };

    private final BroadcastReceiver mPhoneStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE))
                    .listen(new PhoneStateListener() {
                        @Override
                        public void onCallStateChanged(int state, String incomingNumber) {
                            switch (state) {
                                case TelephonyManager.CALL_STATE_IDLE: // 挂断状态
                                    break;
                                case TelephonyManager.CALL_STATE_RINGING: // 等待接听状态
                                    sendPauseCmdIfPlaying(context);
                                    break;
                                case TelephonyManager.CALL_STATE_OFFHOOK: // 接听状态
                                    break;
                                default:
                                    break;
                            }
                            super.onCallStateChanged(state, incomingNumber);
                        }
                    }, PhoneStateListener.LISTEN_CALL_STATE);
        }
    };

    LocalMusicPlayback(Context context) {
        this.mContext = context;
        this.mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // Create the Wifi lock (this does not acquire the lock, this just creates it)
        this.mWifiLock = ((WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "elight_lock");
        this.mState = PlaybackStateCompat.STATE_NONE;
    }

    private void sendPauseCmdIfPlaying(Context context) {
        if (isPlaying()) {
            Intent i = new Intent(context, MusicService.class);
            i.setAction(MusicService.ACTION_CMD);
            i.putExtra(MusicService.CMD_NAME, MusicService.CMD_PAUSE);
            mContext.startService(i);
        }
    }

    /**
     * implements MusicPlayback
     */
    @Override
    public void start() {
    }

    @Override
    public void stop(boolean notifyListeners) {
        mState = PlaybackStateCompat.STATE_STOPPED;
        if (notifyListeners && mCallback != null) {
            mCallback.onPlaybackStatusChanged(mState);
        }
        mCurrentPosition = getCurrentStreamPosition();
        // Give up Audio focus
        giveUpAudioFocus();
        unregisterAudioNoisyReceiver();
        unregisterPhoneStateReceiver();
        // Relax all resources
        relaxResources(true);
    }

    @Override
    public int getState() {
        return mState;
    }

    @Override
    public void setState(int state) {
        this.mState = state;
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isPlaying() {
        return mPlayOnFocusGain || (mMediaPlayer != null && mMediaPlayer.isPlaying());
    }

    @Override
    public int getCurrentStreamPosition() {
        return mMediaPlayer != null ?
                mMediaPlayer.getCurrentPosition() : mCurrentPosition;
    }

    @Override
    public void setCurrentStreamPosition(int pos) {
        this.mCurrentPosition = pos;
    }

    @Override
    public void updateLastKnownStreamPosition() {
        if (mMediaPlayer != null) {
            mCurrentPosition = mMediaPlayer.getCurrentPosition();
        }
    }

    @Override
    public void play(@Nullable MediaSessionCompat.QueueItem item) {
        Log.d(TAG, "LocalMusicPlayback-play()");
        if (item == null)
            return;
        mPlayOnFocusGain = true;
        tryToGetAudioFocus();
        registerAudioNoisyReceiver();
        registerPhoneStateReceiver();

        MediaDescriptionCompat description = item.getDescription();
        String mediaId = description.getMediaId();
        boolean mediaHasChanged = !TextUtils.equals(mediaId, mCurrentMediaId);
        if (mediaHasChanged) {
            mCurrentPosition = 0;
            mCurrentMediaId = mediaId;
        }
        if (mState == PlaybackStateCompat.STATE_PAUSED && !mediaHasChanged && mMediaPlayer != null) {
            configMediaPlayerState();
            return;
        }

        mState = PlaybackStateCompat.STATE_STOPPED;
        relaxResources(false); // release everything except MediaPlayer

        String source;
        Function<MediaSessionCompat.QueueItem, String> urlTransformer = MusicConfig.get().getUrlTransformer();
        if (urlTransformer != null) {
            source = urlTransformer.apply(item);
        } else {
            Uri uri = description.getMediaUri();
            if (uri == null) {
                Exception ex = new Exception("Uri can not be null");
                Log.e(TAG, ex + " Exception playing song");
                if (mCallback != null) {
                    mCallback.onError(ex.getMessage());
                }
                return;
            }
            source = uri.toString();
        }
        Log.d(TAG, "source: " + source + ". uri: " + description.getMediaUri());

        try {
            createMediaPlayerIfNeeded();

            mState = PlaybackStateCompat.STATE_BUFFERING;

            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setDataSource(source);

            // Starts preparing the media player in the background. When
            // it's done, it will call our OnPreparedListener (that is,
            // the onPrepared() method on this class, since we set the
            // listener to 'this'). Until the media player is prepared,
            // we *cannot* call start() on it!
            mMediaPlayer.prepareAsync();

            // If we are streaming from the internet, we want to hold a
            // Wifi lock, which prevents the Wifi radio from going to
            // sleep while the song is playing.
            mWifiLock.acquire();

            if (mCallback != null) {
                mCallback.onPlaybackStatusChanged(mState);
            }

        } catch (IOException ex) {
            Log.e(TAG, ex + " Exception playing song");
            if (mCallback != null) {
                mCallback.onError(ex.getMessage());
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void pause() {
        if (mState == PlaybackStateCompat.STATE_PLAYING) {
            // Pause media player and cancel the 'foreground service' mState.
            if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentPosition = mMediaPlayer.getCurrentPosition();
            }
            // while paused, retain the MediaPlayer but give up audio focus
            relaxResources(false);
            giveUpAudioFocus();
        }
        mState = PlaybackStateCompat.STATE_PAUSED;
        if (mCallback != null) {
            mCallback.onPlaybackStatusChanged(mState);
        }
        unregisterAudioNoisyReceiver();
        unregisterPhoneStateReceiver();
    }

    @Override
    public void seekTo(int position) {
        Log.d(TAG, "seekTo called with " + position);

        if (mMediaPlayer == null) {
            // If we do not have a current media player, simply update the current position
            mCurrentPosition = position;
        } else {
            if (mMediaPlayer.isPlaying()) {
                mState = PlaybackStateCompat.STATE_BUFFERING;
            }
            mMediaPlayer.seekTo(position);
            if (mCallback != null) {
                mCallback.onPlaybackStatusChanged(mState);
            }
        }
    }

    @Override
    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    @Override
    public String getCurrentMediaId() {
        return mCurrentMediaId;
    }

    @Override
    public void setCurrentMediaId(String mediaId) {
        this.mCurrentMediaId = mediaId;
    }

    /**
     * Try to get the system audio focus.
     */
    private void tryToGetAudioFocus() {
        Log.d(TAG, "tryToGetAudioFocus");
        if (mAudioFocus != AUDIO_FOCUSED) {
            int result = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mAudioFocus = AUDIO_FOCUSED;
            }
        }
    }

    /**
     * Give up the audio focus.
     */
    private void giveUpAudioFocus() {
        Log.d(TAG, "giveUpAudioFocus");
        if (mAudioFocus == AUDIO_FOCUSED) {
            if (mAudioManager.abandonAudioFocus(this) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK;
            }
        }
    }

    /**
     * Reconfigures MediaPlayer according to audio focus settings and
     * starts/restarts it. This method starts/restarts the MediaPlayer
     * respecting the current audio focus mState. So if we have focus, it will
     * play normally; if we don't have focus, it will either leave the
     * MediaPlayer paused or set it to a low volume, depending on what is
     * allowed by the current focus settings. This method assumes mPlayer !=
     * null, so if you are calling it, you have to do so from a context where
     * you are sure this is the case.
     */
    private void configMediaPlayerState() {
        Log.d(TAG, "configMediaPlayerState. mAudioFocus=" + mAudioFocus);
        if (mAudioFocus == AUDIO_NO_FOCUS_NO_DUCK) {
            // If we don't have audio focus and can't duck, we have to pause,
            if (mState == PlaybackStateCompat.STATE_PLAYING) {
                pause();
            }
        } else {  // we have audio focus:
            if (mAudioFocus == AUDIO_NO_FOCUS_CAN_DUCK) {
                mMediaPlayer.setVolume(VOLUME_DUCK, VOLUME_DUCK); // we'll be relatively quiet
            } else {
                if (mMediaPlayer != null) {
                    mMediaPlayer.setVolume(VOLUME_NORMAL, VOLUME_NORMAL); // we can be loud again
                } // else do something for remote client.
            }
            // If we were playing when we lost focus, we need to resume playing.
            if (mPlayOnFocusGain) {
                if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
                    Log.d(TAG, "configMediaPlayerState startMediaPlayer. seeking to " +
                            mCurrentPosition);
                    if (mCurrentPosition == mMediaPlayer.getCurrentPosition()) {
                        mMediaPlayer.start();
                        mState = PlaybackStateCompat.STATE_PLAYING;
                    } else {
                        mMediaPlayer.seekTo(mCurrentPosition);
                        mState = PlaybackStateCompat.STATE_BUFFERING;
                    }
                }
                mPlayOnFocusGain = false;
            }
        }
        if (mCallback != null) {
            mCallback.onPlaybackStatusChanged(mState);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        Log.d(TAG, "onAudioFocusChange. focusChange=" + focusChange);
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            // We have gained focus:
            mAudioFocus = AUDIO_FOCUSED;

        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            // We have lost focus. If we can duck (low playback volume), we can keep playing.
            // Otherwise, we need to pause the playback.
            boolean canDuck = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
            mAudioFocus = canDuck ? AUDIO_NO_FOCUS_CAN_DUCK : AUDIO_NO_FOCUS_NO_DUCK;

            // If we are playing, we need to reset media player by calling configMediaPlayerState
            // with mAudioFocus properly set.
            if (mState == PlaybackStateCompat.STATE_PLAYING && !canDuck) {
                // If we don't have audio focus and can't duck, we save the information that
                // we were playing, so that we can resume playback once we get the focus back.
                mPlayOnFocusGain = true;
            }
        } else {
            Log.e(TAG, "onAudioFocusChange: Ignoring unsupported focusChange: " + focusChange);
        }
        configMediaPlayerState();
    }

    /**
     * Called when MediaPlayer has completed a seek
     *
     * @see OnSeekCompleteListener
     */
    @Override
    public void onSeekComplete(MediaPlayer mp) {
        Log.d(TAG, "onSeekComplete from MediaPlayer: " + mp.getCurrentPosition());
        mCurrentPosition = mp.getCurrentPosition();
        if (mState == PlaybackStateCompat.STATE_BUFFERING) {
            mMediaPlayer.start();
            mState = PlaybackStateCompat.STATE_PLAYING;
        }
        if (mCallback != null) {
            mCallback.onPlaybackStatusChanged(mState);
        }
    }

    /**
     * Called when media player is done playing current song.
     *
     * @see OnCompletionListener
     */
    @Override
    public void onCompletion(MediaPlayer mp) {
        Log.d(TAG, "onCompletion from MediaPlayer");
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        if (mCallback != null) {
            mCallback.onCompletion();
        }
    }

    /**
     * Called when media player is done preparing.
     *
     * @see OnPreparedListener
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
        Log.d(TAG, "onPrepared from MediaPlayer");
        // The media player is done preparing. That means we can start playing if we
        // have audio focus.
        configMediaPlayerState();
//        if (mState == PlaybackStateCompat.STATE_BUFFERING) {
//            mMediaPlayer.start();
//            mState = PlaybackStateCompat.STATE_PLAYING;
//            Log.e(TAG, "media player onPrepared");
//        }
//        if (mCallback != null) {
//            mCallback.onPlaybackStatusChanged(mState);
//        }
    }

    /**
     * Called when there's an error playing media. When this happens, the media
     * player goes to the Error state. We warn the user about the error and
     * reset the media player.
     *
     * @see OnErrorListener
     */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "Media player error: what=" + what + ", extra=" + extra);
        if (mCallback != null) {
            mCallback.onError("MediaPlayer error " + what + " (" + extra + ")");
        }
        return true; // true indicates we handled the error
//        mMediaPlayer.reset();
//        return false;
    }

    /**
     * Makes sure the media player exists and has been reset. This will create
     * the media player if needed, or reset the existing media player if one
     * already exists.
     */
    private void createMediaPlayerIfNeeded() {
        Log.d(TAG, "createMediaPlayerIfNeeded. needed? " + (mMediaPlayer == null));
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();

            // Make sure the media player will acquire a wake-lock while
            // playing. If we don't do that, the CPU might go to sleep while the
            // song is playing, causing playback to stop.
            mMediaPlayer.setWakeMode(mContext.getApplicationContext(),
                    PowerManager.PARTIAL_WAKE_LOCK);

            // we want the media player to notify us when it's ready preparing,
            // and when it's done playing:
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnCompletionListener(this);
            mMediaPlayer.setOnErrorListener(this);
            mMediaPlayer.setOnSeekCompleteListener(this);
        } else {
            mMediaPlayer.reset();
            Log.d(TAG, "media player resets!");
        }
    }

    /**
     * MediaPlayer很消耗资源，使用完不要等系统回收，主动回收资源
     * Releases resources used by the service for playback. This includes the
     * "foreground service" status, the wake locks and possibly the MediaPlayer.
     *
     * @param releaseMediaPlayer Indicates whether the Media Player should also
     *                           be released or not
     */
    private void relaxResources(boolean releaseMediaPlayer) {
        Log.d(TAG, "relaxResources. releaseMediaPlayer=" + releaseMediaPlayer);

        // stop and release the Media Player, if it's available
        if (releaseMediaPlayer && mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        // we can also release the Wifi lock, if we're holding it
        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        }
    }

    private void registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            mContext.registerReceiver(mAudioNoisyReceiver, mAudioNoisyIntentFilter);
            mAudioNoisyReceiverRegistered = true;
        }
    }

    private void unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            mContext.unregisterReceiver(mAudioNoisyReceiver);
            mAudioNoisyReceiverRegistered = false;
        }
    }

    private void registerPhoneStateReceiver() {
        if (!mPhoneStateReceiverRegistered) {
            mContext.registerReceiver(mPhoneStateReceiver, mPhoneStateIntentFilter);
            mPhoneStateReceiverRegistered = true;
        }
    }

    private void unregisterPhoneStateReceiver() {
        if (mPhoneStateReceiverRegistered) {
            mContext.unregisterReceiver(mPhoneStateReceiver);
            mPhoneStateReceiverRegistered = false;
        }
    }
}
