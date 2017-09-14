package com.ic2lab.api.musicplayer;

import android.app.Service;
import android.support.v4.media.session.MediaSessionCompat;

public interface IMusicService {

    Service getServiceContext();

    MediaSessionCompat.Token getSessionToken();
}
