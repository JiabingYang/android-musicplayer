package com.ic2lab.api.musicplayer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

class MusicPlaybackManager implements MusicPlayback.Callback {

    private final String TAG = getClass().getSimpleName();

    private String mCurrentPlaybackMode;
    private PlaybackServiceCallback mServiceCallback;
    private MediaSessionCallback mMediaSessionCallback;
    private QueueManager mQueueManager;
    private MusicPlayback mMusicPlayback;

    MusicPlaybackManager(PlaybackServiceCallback serviceCallback,
                         QueueManager queueManager,
                         MusicPlayback musicPlayback) {
        this.mServiceCallback = serviceCallback;
        this.mQueueManager = queueManager;
        this.mMusicPlayback = musicPlayback;
        mMediaSessionCallback = new MediaSessionCallback();
        mMusicPlayback.setCallback(this);
    }

    MusicPlayback getPlayback() {
        return mMusicPlayback;
    }

    MediaSessionCompat.Callback getMediaSessionCallback() {
        return mMediaSessionCallback;
    }

    /**
     * call MusicPlayback.start(）, and notify MusicService
     */
    void handlePlayRequest() {
        Log.d(TAG, "handlePlayRequest, mState= " + mMusicPlayback.getState());
        MediaSessionCompat.QueueItem currentMusic = mQueueManager.getCurrentMusic();
        if (currentMusic != null) {
            mServiceCallback.onPlaybackStart();
            mMusicPlayback.play(currentMusic);
            Log.d(TAG, "currentMusic.path: " + currentMusic.getDescription().getMediaUri());
        }
    }

    /**
     * call MusicPlayback.pause(）, and notify MusicService
     */
    void handlePauseRequest() {
        Log.d(TAG, "handlePauseRequest, mState+ " + mMusicPlayback.getState());
        if (mMusicPlayback.isPlaying()) {
            mMusicPlayback.pause();
            mServiceCallback.onPlaybackStop();
        }
    }

    /**
     * call MusicPlayback.stop(）, and notify MusicService
     *
     * @param withError Error message in case the stop has an unexpected cause. The error
     *                  message will be set in the PlaybackState and will be visible to
     *                  MediaController clients.
     */
    void handleStopRequest(String withError) {
        Log.w(TAG, "handleStopRequest(" + withError + "), mState= " + mMusicPlayback.getState());
        mMusicPlayback.stop(true);
        mServiceCallback.onPlaybackStop();
        updatePlaybackState(withError);
    }

    /**
     * Update the current media player state, optionally showing an error message.
     *
     * @param error if not null, error message to present to the user
     */
    void updatePlaybackState(String error) {
        long position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        if (mMusicPlayback != null && mMusicPlayback.isConnected()) {
            position = mMusicPlayback.getCurrentStreamPosition();
        }

        //noinspection ResourceType
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(getAvailableActions());

        //setCustomAction(stateBuilder);
        int state = mMusicPlayback.getState();

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(0, error);
            state = PlaybackStateCompat.STATE_ERROR;
        }
        //noinspection ResourceType
        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime());

        // Set the activeQueueItemId if the current index is valid.
        MediaSessionCompat.QueueItem currentMusic = mQueueManager.getCurrentMusic();
        if (currentMusic != null) {
            stateBuilder.setActiveQueueItemId(currentMusic.getQueueId());
        }

        mServiceCallback.onPlaybackStateUpdated(stateBuilder.build());

        if (state == PlaybackStateCompat.STATE_PLAYING ||
                state == PlaybackStateCompat.STATE_PAUSED) {
            mServiceCallback.onNotificationRequired();
        }

    }

    private long getAvailableActions() {
        long actions =
                PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
        if (mMusicPlayback.isPlaying()) {
            actions |= PlaybackStateCompat.ACTION_PAUSE;
        }
        return actions;
    }

    /**
     * implements MusicPlayback.Callback
     * When current music has finished playing, skip to the next one.
     */
    @Override
    public void onCompletion() {
        if (MusicConsts.MODE_SINGLE.equals(mCurrentPlaybackMode)) {
            playFromStart();
            return;
        }
        if (mQueueManager.skipQueuePosition(1)) {
            String newMediaId = mQueueManager.getCurrentMusic().getDescription().getMediaId();
            if (newMediaId != null && newMediaId.equals(mMusicPlayback.getCurrentMediaId())) {
                playFromStart();
            } else {
                handlePlayRequest();
                mQueueManager.updateMetadata();
            }
        } else {
            Log.w(TAG, "handleStopRequest(null) called from onCompletion()");
            handleStopRequest(null);
        }
    }

    private void playFromStart() {
        mMusicPlayback.pause();
        handlePlayRequest();
        mMusicPlayback.seekTo(0);
        mQueueManager.updateMetadata();
    }

    @Override
    public void onPlaybackStatusChanged(int state) {
        updatePlaybackState(null);
    }

    @Override
    public void onError(String error) {
        updatePlaybackState(error);
    }

    @Override
    public void setCurrentMediaId(String mediaId) {
        Log.d(TAG, "setCurrentMediaId: " + mediaId);
        mQueueManager.setCurrentQueueItem(mediaId);
    }

    interface PlaybackServiceCallback {

        void onPlaybackStart();

        void onNotificationRequired();

        void onPlaybackStop();

        void onPlaybackStateUpdated(PlaybackStateCompat newState);

        void onPlaybackModeUpdated(String playbackMode);
    }

    /**
     * MediaSession.Callback
     */
    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            Log.d(TAG, "MediaSessionCallback.onPlay");
            handlePlayRequest();
        }

        @Override
        public void onPause() {
            Log.d(TAG, "MediaSessionCallback.onPause");
            handlePauseRequest();
        }

        @Override
        public void onStop() {
            Log.w(TAG, "MediaSessionCallback.onStop");
            handleStopRequest(null);
        }

        @Override
        public void onSeekTo(long pos) {
            Log.d(TAG, "MediaSessionCallback.onSeekTo(" + pos + ")");
            mMusicPlayback.seekTo((int) pos);
        }

        @Override
        public void onSkipToNext() {
            Log.d(TAG, "MediaSessionCallback.onSkipToNext");
            if (mQueueManager.skipQueuePosition(1)) {
                handlePlayRequest();
            } else {
                handleStopRequest("Cannot skip to next.");
            }
            mQueueManager.updateMetadata();
        }

        @Override
        public void onSkipToPrevious() {
            Log.d(TAG, "MediaSessionCallback.onSkipToPrevious");
            if (mQueueManager.skipQueuePosition(-1)) {
                handlePlayRequest();
            } else {
                handleStopRequest("Cannot skip to previous.");
            }
            mQueueManager.updateMetadata();
        }

        @Override
        public void onSkipToQueueItem(long queueId) {
            Log.d(TAG, "MediaSessionCallback.onSkipToQueueItem( " + queueId + ")");
            mQueueManager.setCurrentQueueItem(queueId);
            handlePlayRequest();
            mQueueManager.updateMetadata();
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle bundle) {
            Log.d(TAG, "MediaSessionCallback.onPlayFromMediaId( " + mediaId + ", " + bundle + ")");
            bundle.setClassLoader(MediaSessionCompat.QueueItem.class.getClassLoader());
            if (mQueueManager.setQueueFromMusic(mediaId, bundle)) {
                Parcelable[] parcelables = bundle.getParcelableArray(MusicConsts.QUEUE_ITEM_LIST);
                if (parcelables != null) {
                    List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
                    for (Parcelable parcelable : parcelables) {
                        queue.add((MediaSessionCompat.QueueItem) parcelable);
                    }
                    List<Long> queueIdList = QueueHelper.queueIds(queue);
                    long[] queueIds = ArrayUtils.toPrimitive(queueIdList.toArray(new Long[queueIdList.size()]));
                    mCurrentPlaybackMode = (queueIds != null && !isAscending(queueIds)) ? MusicConsts.MODE_RANDOM : MusicConsts.MODE_SEQUENCE;
                    mServiceCallback.onPlaybackModeUpdated(mCurrentPlaybackMode);
                }
                handlePlayRequest();
            }
        }

        private boolean isAscending(long[] array) {
            for (int i = 0; i < array.length - 1; i++) {
                if (array[i] > array[i + 1]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            Log.d(TAG, "MediaSessionCallback.onCustomAction( " + action + ", " + extras + ")");
            switch (action) {
                case MusicConsts.ACTION_UPDATE_PLAYBACK_STATE:
                    updatePlaybackState(null);
                    break;
                case MusicConsts.ACTION_SWAP_PLAYBACK_MODE:
                    if (null == mCurrentPlaybackMode) {
                        Log.d(TAG, "null == mCurrentPlaybackMode");
                        break;
                    }
                    if (MusicConsts.MODE_SEQUENCE.equals(mCurrentPlaybackMode)) {
                        Log.d(TAG, "MODE_SEQUENCE.equals(mCurrentPlaybackMode)");
                        mCurrentPlaybackMode = MusicConsts.MODE_SINGLE;
                        mServiceCallback.onPlaybackModeUpdated(MusicConsts.MODE_SINGLE);
                        break;
                    }
                    if (MusicConsts.MODE_SINGLE.equals(mCurrentPlaybackMode) && mQueueManager.setRandomQueue()) {
                        Log.d(TAG, "MODE_SINGLE.equals(mCurrentPlaybackMode)");
                        mCurrentPlaybackMode = MusicConsts.MODE_RANDOM;
                        mServiceCallback.onPlaybackModeUpdated(MusicConsts.MODE_RANDOM);
                        break;
                    }
                    if (MusicConsts.MODE_RANDOM.equals(mCurrentPlaybackMode) && mQueueManager.setSequenceQueue()) {
                        Log.d(TAG, "MODE_RANDOM.equals(mCurrentPlaybackMode)");
                        mCurrentPlaybackMode = MusicConsts.MODE_SEQUENCE;
                        mServiceCallback.onPlaybackModeUpdated(MusicConsts.MODE_SEQUENCE);
                        break;
                    }
                    break;
                case MusicConsts.ACTION_LOAD_PLAYBACK_INFO:
                    extras.setClassLoader(MediaSessionCompat.QueueItem.class.getClassLoader());
                    if (mQueueManager.setQueueFromMusic(extras.getString(MusicConsts.MEDIA_ID), extras)) {
                        mCurrentPlaybackMode = extras.getString(MusicConsts.PLAYBACK_MODE, MusicConsts.MODE_SEQUENCE);
                        mServiceCallback.onPlaybackModeUpdated(mCurrentPlaybackMode);
                        mMusicPlayback.setCurrentMediaId(extras.getString(MusicConsts.MEDIA_ID));
                        mMusicPlayback.pause();
                        mMusicPlayback.seekTo((int) extras.getLong(MusicConsts.POSITION));
                    }
                    break;
                default:
                    break;
            }
        }

        //耳机按键控制
        @Override
        public boolean onMediaButtonEvent(Intent intent) {
            // TODO: 2016/12/13

            return super.onMediaButtonEvent(intent);
        }
    }

}
