package com.ic2lab.api.musicplayer;

import android.support.annotation.NonNull;
import android.support.v4.media.session.MediaSessionCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * methods for queue operations.
 */
public class QueueHelper {

    static <T> List<MediaSessionCompat.QueueItem> targetsToQueue(
            @NonNull T[] origins,
            @NonNull BiFunction<T, Long, MediaSessionCompat.QueueItem> queueItemTransformer) {
        List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
        long count = 0;
        for (T origin : origins) {
            queue.add(queueItemTransformer.apply(origin, count++));
        }
        return queue;
    }

    static <T> List<MediaSessionCompat.QueueItem> targetsToQueue(
            @NonNull T[] origins,
            @NonNull BiFunction<T, Long, MediaSessionCompat.QueueItem> queueItemTransformer,
            long[] queueIds) {
        if (queueIds == null || origins.length != queueIds.length) {
            return targetsToQueue(origins, queueItemTransformer);
        }
        List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
        for (int i = 0; i < origins.length; i++) {
            queue.add(queueItemTransformer.apply(origins[i], queueIds[i]));
        }
        return queue;
    }

    static <T> List<MediaSessionCompat.QueueItem> targetsToQueue(
            @NonNull List<T> origins,
            @NonNull BiFunction<T, Long, MediaSessionCompat.QueueItem> queueItemTransformer) {
        List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
        long count = 0;
        for (T origin : origins) {
            queue.add(queueItemTransformer.apply(origin, count++));
        }
        return queue;
    }

    static <T> List<MediaSessionCompat.QueueItem> targetsToQueue(
            @NonNull List<T> origins,
            @NonNull BiFunction<T, Long, MediaSessionCompat.QueueItem> queueItemTransformer,
            List<Long> queueIds) {
        if (queueIds == null || origins.size() != queueIds.size()) {
            return targetsToQueue(origins, queueItemTransformer);
        }
        List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
        for (int i = 0; i < origins.size(); i++) {
            queue.add(queueItemTransformer.apply(origins.get(i), queueIds.get(i)));
        }
        return queue;
    }

    static <T> List<T> queueToTargets(List<MediaSessionCompat.QueueItem> queue,
                                      Function<MediaSessionCompat.QueueItem, T> targetTransformer) {
        return queueToTargets(queue, targetTransformer, false);
    }

    static <T> List<T> queueToTargets(List<MediaSessionCompat.QueueItem> queue,
                                      Function<MediaSessionCompat.QueueItem, T> targetTransformer,
                                      boolean ordered) {
        if (queue == null)
            return new ArrayList<>();
        List<MediaSessionCompat.QueueItem> tempQueue = ordered ? createSequenceQueue(queue) : queue;
        List<T> targets = new ArrayList<>();
        for (MediaSessionCompat.QueueItem item : tempQueue) {
            targets.add(targetTransformer.apply(item));
        }
        return targets;
    }

    static List<MediaSessionCompat.QueueItem> createSequenceQueue(List<MediaSessionCompat.QueueItem> queue) {
        if (queue == null)
            return null;
        List<MediaSessionCompat.QueueItem> newQueue = new ArrayList<>(queue);
        Collections.sort(newQueue, (o1, o2) -> (int) (o1.getQueueId() - o2.getQueueId()));
        return newQueue;
    }

    static List<MediaSessionCompat.QueueItem> createRandomQueue(List<MediaSessionCompat.QueueItem> queue) {
        if (queue == null)
            return null;
        List<MediaSessionCompat.QueueItem> newQueue = new ArrayList<>(queue);
        Collections.shuffle(newQueue);
        return newQueue;
    }

    static int getMusicIndexOnQueue(Iterable<MediaSessionCompat.QueueItem> queue, String mediaId) {
        int index = 0;
        for (MediaSessionCompat.QueueItem item : queue) {
            if (mediaId.equals(item.getDescription().getMediaId())) {
                return index;
            }
            index++;
        }
        return -1;
    }

    static int getMusicIndexOnQueue(Iterable<MediaSessionCompat.QueueItem> queue, long queueId) {
        int index = 0;
        for (MediaSessionCompat.QueueItem item : queue) {
            if (queueId == item.getQueueId()) {
                return index;
            }
            index++;
        }
        return -1;
    }

    static boolean isIndexPlayable(int index, List<MediaSessionCompat.QueueItem> queue) {
        return (queue != null && index >= 0 && index < queue.size());
    }

    public static List<Long> queueIds(List<MediaSessionCompat.QueueItem> queue) {
        if (queue == null)
            return null;
        List<Long> queueIds = new ArrayList<>();
        for (MediaSessionCompat.QueueItem item : queue) {
            queueIds.add(item.getQueueId());
        }
        return queueIds;
    }
}
