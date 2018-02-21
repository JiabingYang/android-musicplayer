package com.ic2lab.api.sample.musicplayer;

import android.app.Application;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import com.ic2lab.api.musicplayer.Function;
import com.ic2lab.api.musicplayer.MusicConfig;

public class SampleApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        MusicConfig.get()
                .setMetadataTransformer(queueItem -> SongQueueHelper.get().queueItemToMetadata(queueItem))
                .setNotificationEnabled(false);
    }
}
