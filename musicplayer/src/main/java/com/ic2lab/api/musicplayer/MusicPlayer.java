package com.ic2lab.api.musicplayer;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.util.List;

public class MusicPlayer {

    public static MediaMetadataCompat getMetadata(Activity activity) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController != null)
            return mediaController.getMetadata();
        return null;
    }

    public static PlaybackStateCompat getPlaybackState(Activity activity) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController != null)
            return mediaController.getPlaybackState();
        return null;
    }

    public static Bundle getExtras(Activity activity) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController != null)
            return mediaController.getExtras();
        return null;
    }

    public static List<MediaSessionCompat.QueueItem> getQueue(Activity activity) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController != null)
            return mediaController.getQueue();
        return null;
    }

    public static Integer getIndex(Activity activity) {
        MediaMetadataCompat metadata = getMetadata(activity);
        List<MediaSessionCompat.QueueItem> queue = getQueue(activity);
        if (metadata == null || queue == null)
            return null;
        String mediaId = metadata.getDescription().getMediaId();
        if (mediaId == null)
            return null;
        for (int index = 0; index < queue.size(); index++) {
            if (mediaId.equals(queue.get(index).getDescription().getMediaId()))
                return index;
        }
        return null;
    }

    public static void play(Activity activity, List<MediaSessionCompat.QueueItem> queue, int index, Bundle extras) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController != null && queue != null && index < queue.size() && index >= 0) {
            Bundle _extras = (extras == null) ? new Bundle() : extras;
            _extras.setClassLoader(MediaSessionCompat.QueueItem.class.getClassLoader());
            _extras.putParcelableArray(MusicConsts.QUEUE_ITEM_LIST, queue.toArray(new MediaSessionCompat.QueueItem[queue.size()]));
            mediaController.getTransportControls().playFromMediaId(queue.get(index).getDescription().getMediaId(), _extras);
        }
    }

    public static void pauseOrPlay(Activity activity) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController != null && mediaController.getMetadata() != null) {
            if (mediaController.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING)
                mediaController.getTransportControls().pause();
            else if (mediaController.getPlaybackState().getState() == PlaybackStateCompat.STATE_PAUSED)
                mediaController.getTransportControls().play();
        }
    }

    public static void pause(Activity activity) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController != null && mediaController.getMetadata() != null)
            mediaController.getTransportControls().pause();
    }

    public static void stop(Activity activity) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController != null && mediaController.getMetadata() != null)
            mediaController.getTransportControls().stop();
    }

    public static void skipToPrevious(Activity activity) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController != null && mediaController.getMetadata() != null)
            mediaController.getTransportControls().skipToPrevious();
    }

    public static void skipToNext(Activity activity) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController != null && mediaController.getMetadata() != null)
            mediaController.getTransportControls().skipToNext();
    }

    public static void seekTo(Activity activity, long pos) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController != null)
            mediaController.getTransportControls().seekTo(pos);
    }

    public static void swapPlaybackMode(Activity activity) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController != null)
            mediaController.getTransportControls().sendCustomAction(MusicConsts.ACTION_SWAP_PLAYBACK_MODE, null);
    }

    public static void updatePlaybackState(Activity activity) {
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController != null)
            mediaController.getTransportControls().sendCustomAction(MusicConsts.ACTION_UPDATE_PLAYBACK_STATE, null);
    }

    public static void loadMusicPlaybackInfo(Activity activity, MusicPlaybackInfo musicPlaybackInfo) {
        if (musicPlaybackInfo == null)
            return;
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(activity);
        if (mediaController == null)
            return;
        Bundle extras = new Bundle();
        extras.putParcelableArray(MusicConsts.QUEUE_ITEM_LIST, musicPlaybackInfo.getQueue().toArray(
                new MediaSessionCompat.QueueItem[musicPlaybackInfo.getQueue().size()]));
        extras.putString(MusicConsts.MEDIA_ID, musicPlaybackInfo.getMediaId());
        extras.putLong(MusicConsts.POSITION, musicPlaybackInfo.getPosition());
        extras.putString(MusicConsts.PLAYBACK_MODE, musicPlaybackInfo.getPlaybackMode());
        mediaController.getTransportControls().sendCustomAction(MusicConsts.ACTION_LOAD_PLAYBACK_INFO, extras);
    }

    public static MusicPlaybackInfo getMusicPlaybackInfo(Activity activity) {
//        updatePlaybackState();
        MediaMetadataCompat metadata = getMetadata(activity);
        if (metadata == null)
            return null;
        String mediaId = metadata.getDescription().getMediaId();
        List<MediaSessionCompat.QueueItem> queue = getQueue(activity);
        PlaybackStateCompat playbackState = getPlaybackState(activity);
        Bundle extras = getExtras(activity);
        if (mediaId == null || queue == null || playbackState == null || extras == null) {
            return null;
        }
        return new MusicPlaybackInfo(
                queue,
                mediaId,
                playbackState.getState() == PlaybackStateCompat.STATE_PAUSED ? playbackState.getPosition() :
                        (playbackState.getPosition() + (long) ((int) (SystemClock.elapsedRealtime() -
                                playbackState.getLastPositionUpdateTime()) * playbackState.getPlaybackSpeed())),
                extras.getString(MusicConsts.PLAYBACK_MODE));
    }
}
