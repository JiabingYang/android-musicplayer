package com.ic2lab.api.musicplayer;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

public class MusicConfig {

    private static final MusicConfig ourInstance = new MusicConfig();

    private Function<MediaSessionCompat.QueueItem, String> mUrlTransformer;
    private Function<MediaSessionCompat.QueueItem, MediaMetadataCompat> mMetadataTransformer;
    private BiConsumer<IMusicService, MusicNotificationManagerCallback> mNotificationManagerCreator;

    private boolean mNotificationEnabled = false;

    private MusicConfig() {
    }

    public static MusicConfig get() {
        return ourInstance;
    }

    public Function<MediaSessionCompat.QueueItem, String> getUrlTransformer() {
        return mUrlTransformer;
    }

    public MusicConfig setUrlTransformer(Function<MediaSessionCompat.QueueItem, String> urlTransformer) {
        this.mUrlTransformer = urlTransformer;
        return this;
    }

    public Function<MediaSessionCompat.QueueItem, MediaMetadataCompat> getMetadataTransformer() {
        return mMetadataTransformer;
    }

    public MusicConfig setMetadataTransformer(Function<MediaSessionCompat.QueueItem, MediaMetadataCompat> metadataTransformer) {
        this.mMetadataTransformer = metadataTransformer;
        return this;
    }

    public boolean isNotificationEnabled() {
        return mNotificationEnabled;
    }

    public MusicConfig setNotificationEnabled(boolean enabled) {
        this.mNotificationEnabled = enabled;
        return this;
    }

    public BiConsumer<IMusicService, MusicNotificationManagerCallback> getNotificationManagerCreator() {
        return mNotificationManagerCreator;
    }

    public MusicConfig setNotificationManagerCreator(BiConsumer<IMusicService, MusicNotificationManagerCallback> notificationManagerCreator) {
        this.mNotificationManagerCreator = notificationManagerCreator;
        return this;
    }

    public interface MusicNotificationManagerCallback {
        void onMusicNotificationManager(MusicNotificationManager musicNotificationManager);

        void onError(Throwable e);
    }
}
