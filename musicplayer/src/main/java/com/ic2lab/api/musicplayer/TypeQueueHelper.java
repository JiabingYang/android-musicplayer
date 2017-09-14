package com.ic2lab.api.musicplayer;

import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import java.util.List;

public abstract class TypeQueueHelper<T> implements MusicTypeConverter<T> {

    // ----------------------------- targetsToQueue -----------------------------
    public List<MediaSessionCompat.QueueItem> targetsToQueue(@NonNull T[] ts) {
        return QueueHelper.targetsToQueue(ts, this::targetToQueueItem);
    }

    public List<MediaSessionCompat.QueueItem> targetsToQueue(@NonNull T[] ts, long[] queueIds) {
        return QueueHelper.targetsToQueue(ts, this::targetToQueueItem, queueIds);
    }

    public List<MediaSessionCompat.QueueItem> targetsToQueue(@NonNull List<T> ts) {
        return QueueHelper.targetsToQueue(ts, this::targetToQueueItem);
    }

    public List<MediaSessionCompat.QueueItem> targetsToQueue(@NonNull List<T> ts, List<Long> queueIds) {
        return QueueHelper.targetsToQueue(ts, this::targetToQueueItem, queueIds);
    }

    // ----------------------------- queueToTargets -----------------------------
    public List<T> queueToTargets(List<MediaSessionCompat.QueueItem> queue) {
        return queueToTargets(queue, false);
    }

    public List<T> queueToTargets(List<MediaSessionCompat.QueueItem> queue, boolean ordered) {
        return QueueHelper.queueToTargets(queue, this::queueItemToTarget, ordered);
    }

    // ----------------------------- queueToMetadatas -----------------------------
    public List<MediaMetadataCompat> queueToMetadatas(List<MediaSessionCompat.QueueItem> queue) {
        return queueToMetadatas(queue, false);
    }

    public List<MediaMetadataCompat> queueToMetadatas(List<MediaSessionCompat.QueueItem> queue, boolean ordered) {
        return QueueHelper.queueToTargets(queue, this::queueItemToMetadata, ordered);
    }

    // ----------------------------- targetsToMetadatas -----------------------------
    public List<MediaMetadataCompat> targetsToMetadatas(@NonNull T[] ts) {
        return queueToMetadatas(targetsToQueue(ts));
    }

    public List<MediaMetadataCompat> targetsToMetadatas(@NonNull T[] ts, long[] queueIds) {
        return queueToMetadatas(targetsToQueue(ts, queueIds));
    }

    public List<MediaMetadataCompat> targetsToMetadatas(@NonNull List<T> ts) {
        return queueToMetadatas(targetsToQueue(ts));
    }

    public List<MediaMetadataCompat> targetsToMetadatas(@NonNull List<T> ts, List<Long> queueIds) {
        return queueToMetadatas(targetsToQueue(ts, queueIds));
    }

    // ----------------------------- queueItemToMetadata -----------------------------
    public MediaMetadataCompat queueItemToMetadata(MediaSessionCompat.QueueItem queueItem) {
        T t = queueItemToTarget(queueItem);
        if (t == null)
            return null;
        return targetToMediaMetadata(t);
    }
}
