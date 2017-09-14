package com.ic2lab.api.musicplayer;


import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

interface MusicTypeConverter<T> {

    T queueItemToTarget(MediaSessionCompat.QueueItem queueItem);

    MediaSessionCompat.QueueItem targetToQueueItem(T t, Long queueId);

    MediaMetadataCompat targetToMediaMetadata(T t);
}
