package com.ic2lab.api.sample.musicplayer;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import com.ic2lab.api.musicplayer.TypeQueueHelper;

public class SongQueueHelper extends TypeQueueHelper<Song> {

    private static final SongQueueHelper sInstance = new SongQueueHelper();

    private static final String ARTIST = "artist";
    private static final String DURATION = "duration";


    public static SongQueueHelper get() {
        return sInstance;
    }

    @Override
    public Song queueItemToTarget(MediaSessionCompat.QueueItem queueItem) {
        if (queueItem == null)
            return null;
        Song song = new Song();
        Bundle extras = queueItem.getDescription().getExtras();
        if (extras != null) {
            song.setArtist(extras.getString(ARTIST));
            song.setDuration(extras.getInt(DURATION));
        }
        song.setPath(String.valueOf(queueItem.getDescription().getMediaUri()));
        song.setId(queueItem.getDescription().getMediaId());
        song.setTitle(String.valueOf(queueItem.getDescription().getTitle()));
        return song;
    }

    @Override
    public MediaSessionCompat.QueueItem targetToQueueItem(Song song, Long queueId) {
        if (song == null || queueId == null)
            return null;
        Bundle extra = new Bundle();
        extra.putString(ARTIST, song.getArtist());
        extra.putInt(DURATION, song.getDuration());
        MediaDescriptionCompat mediaDescription = new MediaDescriptionCompat.Builder()
                .setTitle(song.getTitle())
                .setMediaId(song.getId())
                .setMediaUri(Uri.parse(song.getPath()))
                .setExtras(extra)
                .build();
        return new MediaSessionCompat.QueueItem(mediaDescription, queueId);
    }

    @Override
    public MediaMetadataCompat targetToMediaMetadata(Song song) {
        if (song == null)
            return null;
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, song.getId())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, song.getPath())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getArtist())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.getDuration());
        return builder.build();
    }
}
