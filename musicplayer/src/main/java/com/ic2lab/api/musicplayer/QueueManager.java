package com.ic2lab.api.musicplayer;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

class QueueManager {

    private final String TAG = getClass().getSimpleName();

    private MetadataUpdateListener mListener;

    // Now playing queue
    private List<MediaSessionCompat.QueueItem> mPlayingQueue;
    private int mCurrentIndex;

    public QueueManager(@NonNull MetadataUpdateListener metadataUpdateListener) {
        this.mListener = metadataUpdateListener;
        mCurrentIndex = 0;
    }

    private void setCurrentQueueIndex(int index) {
        if (index >= 0 && index < mPlayingQueue.size()) {
            mCurrentIndex = index;
            mListener.onCurrentQueueIndexUpdated(mCurrentIndex);
        }
    }

    boolean setCurrentQueueItem(long queueId) {
        // Set the current index on queue from the queue id.
        int index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, queueId);
        setCurrentQueueIndex(index);
        return index >= 0;
    }

    boolean setCurrentQueueItem(String mediaId) {
        // set the current index on queue from the music Id:
        int index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, mediaId);
        setCurrentQueueIndex(index);
        return index >= 0;
    }

    boolean skipQueuePosition(int amount) {
        int index = mCurrentIndex + amount;
        if (index < 0) {
            // Skip backwards the first song and keep you on the first song.
            index = 0;
        } else {
            // Skip forwards the last song and cycle back to the start of the queue.
            index %= mPlayingQueue.size();
        }
        if (!QueueHelper.isIndexPlayable(index, mPlayingQueue)) {
            Log.d(TAG, "Index is out of bounds, " + "playQueue.size: " + mPlayingQueue.size());
            return false;
        }
        mCurrentIndex = index;
        return true;
    }

    boolean setQueueFromMusic(String mediaId, Bundle bundle) {
        Log.d(TAG, "setQueueFromMusic");
        Parcelable[] parcelables = bundle.getParcelableArray(MusicConsts.QUEUE_ITEM_LIST);
        bundle.setClassLoader(MediaSessionCompat.QueueItem.class.getClassLoader());
        if (parcelables != null) {
            List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
            for (Parcelable parcelable : parcelables) {
                queue.add((MediaSessionCompat.QueueItem) parcelable);
            }
            setCurrentQueue(null, queue, mediaId);
            updateMetadata();
            return true;
        }
        return false;
    }

    MediaSessionCompat.QueueItem getCurrentMusic() {
        if (!QueueHelper.isIndexPlayable(mCurrentIndex, mPlayingQueue)) {
            return null;
        }
        return mPlayingQueue.get(mCurrentIndex);
    }

    boolean setRandomQueue() {
        if (mPlayingQueue == null)
            return false;
        MediaSessionCompat.QueueItem currentQueueItem = getCurrentMusic();
        List<MediaSessionCompat.QueueItem> newQueue = QueueHelper.createRandomQueue(mPlayingQueue);
        if (currentQueueItem == null) {
            setCurrentQueue(null, newQueue);
            updateMetadata();
            return true;
        }
        setCurrentQueue(null, newQueue, currentQueueItem.getDescription().getMediaId());
        updateMetadata();
        return true;
    }

    boolean setSequenceQueue() {
        if (mPlayingQueue == null)
            return false;
        MediaSessionCompat.QueueItem currentQueueItem = getCurrentMusic();
        List<MediaSessionCompat.QueueItem> newQueue = QueueHelper.createSequenceQueue(mPlayingQueue);
        if (currentQueueItem == null) {
            setCurrentQueue(null, newQueue);
            updateMetadata();
            return true;
        }
        setCurrentQueue(null, newQueue, currentQueueItem.getDescription().getMediaId());
        updateMetadata();
        return true;
    }

    private void setCurrentQueue(String title, List<MediaSessionCompat.QueueItem> newQueue) {
        setCurrentQueue(title, newQueue, null);
    }

    private void setCurrentQueue(String title, List<MediaSessionCompat.QueueItem> newQueue,
                                 String initialMediaId) {
        mPlayingQueue = newQueue;
        int index = 0;
        if (initialMediaId != null) {
            index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, initialMediaId);
        }
        mCurrentIndex = Math.max(index, 0);
        mListener.onQueueUpdated(title, newQueue);
    }

    /**
     * All operation are done about QueueManager, this method will be called.
     */
    void updateMetadata() {
        MediaSessionCompat.QueueItem currentMusic = getCurrentMusic();
        Function<MediaSessionCompat.QueueItem, MediaMetadataCompat> transformer =
                MusicConfig.get().getMetadataTransformer();
        if (currentMusic == null || transformer == null) {
            mListener.onMetadataRetrieveError();
            return;
        }
        MediaMetadataCompat metadata = transformer.apply(currentMusic);
        mListener.onMetadataUpdate(metadata);
    }

    interface MetadataUpdateListener {
        void onMetadataUpdate(MediaMetadataCompat metadata); //MediaSession.setMetadata

        void onQueueUpdated(String title, List<MediaSessionCompat.QueueItem> queue); //MediaSession.setQueue/setQueueItem

        void onMetadataRetrieveError();

        void onCurrentQueueIndexUpdated(int queueIndex);
    }
}
