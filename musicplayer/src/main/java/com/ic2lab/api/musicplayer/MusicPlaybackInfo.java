package com.ic2lab.api.musicplayer;

import android.support.annotation.NonNull;
import android.support.v4.media.session.MediaSessionCompat;

import java.util.List;

public class MusicPlaybackInfo {

    private List<MediaSessionCompat.QueueItem> queue;
    private String mediaId;
    private long position;
    private String playbackMode;

    public MusicPlaybackInfo(@NonNull List<MediaSessionCompat.QueueItem> queue, @NonNull String mediaId, long position, String playbackMode) {
        this.queue = queue;
        this.mediaId = mediaId;
        this.position = position;
        this.playbackMode = playbackMode;
    }

    public List<MediaSessionCompat.QueueItem> getQueue() {
        return queue;
    }

    public void setQueue(List<MediaSessionCompat.QueueItem> queue) {
        this.queue = queue;
    }

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public String getPlaybackMode() {
        return playbackMode;
    }

    public void setPlaybackMode(String playbackMode) {
        this.playbackMode = playbackMode;
    }

}
