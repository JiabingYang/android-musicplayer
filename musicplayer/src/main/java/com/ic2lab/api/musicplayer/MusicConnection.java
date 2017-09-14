package com.ic2lab.api.musicplayer;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.media.session.MediaControllerCompat;

public abstract class MusicConnection implements ServiceConnection {
    protected abstract void onMediaControllerConnected(MediaControllerCompat mediaController);

    protected abstract void onMusicConnectionError(Throwable e);

    protected MediaControllerCompat.Callback onSetMediaControllerCallback() {
        return null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {

    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
}
