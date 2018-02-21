package com.ic2lab.api.sample.musicplayer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.annotation.MainThread;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.ic2lab.api.musicplayer.MusicConnection;
import com.ic2lab.api.musicplayer.MusicConnectionManager;
import com.ic2lab.api.musicplayer.MusicPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.ic2lab.api.musicplayer.MusicConsts.MODE_RANDOM;
import static com.ic2lab.api.musicplayer.MusicConsts.MODE_SEQUENCE;
import static com.ic2lab.api.musicplayer.MusicConsts.MODE_SINGLE;
import static com.ic2lab.api.musicplayer.MusicConsts.PLAYBACK_MODE;

public class PlaybackActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "PlaybackActivity";

    private static final String SONGS = "songs";
    private static final String INDEX = "index";

    private List<Song> mSongsWaitForPlay;
    private Integer mIndexWaitForPlay;

    private ImageView mPlayPauseIv, mRandomIv;
    private TextView mTitleTv, mPlayTimeTv, mDurationTv;
    private SeekBar mSb;
    private BaseQuickAdapter<Song, BaseViewHolder> mAdapter;
    private List<Song> mSongs = new ArrayList<>();

    //因为更新进度条频率比较高所以对PlaybackState进行了缓存，避免频繁getPlaybackState()
    private PlaybackStateCompat mCurrentState;
    private Future<?> mSeekBarUpdateFuture;

    private ScheduledExecutorService mSeekBarUpdateScheduler = Executors.newScheduledThreadPool(1);

    public static void start(Context context) {
        start(context, null, null);
    }

    public static void start(Context context, List<Song> songs, Integer index) {
        Log.e(TAG, "start:" + songs + ", index:" + index);
        if (index == null)
            index = 0;
        Intent starter = new Intent(context, PlaybackActivity.class);
        if (songs == null || songs.isEmpty() || index < 0 || index >= songs.size()) {
            context.startActivity(starter);
            return;
        }
        starter.putExtra(SONGS, songs.toArray(new Song[songs.size()]));
        starter.putExtra(INDEX, index);
        context.startActivity(starter);
    }

    private void refresh() {
        //主动请求更新PlaybackState
        MusicPlayer.updatePlaybackState(this);

        refreshByMediaMetadata(MusicPlayer.getMetadata(this));
        refreshByPlaybackState(mCurrentState = MusicPlayer.getPlaybackState(this));
        refreshByExtras(MusicPlayer.getExtras(this));
        refreshByQueue(MusicPlayer.getQueue(this));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playback);

        if (null != getIntent()) {
            Parcelable[] parcelables = getIntent().getParcelableArrayExtra(SONGS);
            if (parcelables != null) {
                mSongsWaitForPlay = new ArrayList<>();
                for (Parcelable parcelable : parcelables) {
                    mSongsWaitForPlay.add((Song) parcelable);
                }
            }
            mIndexWaitForPlay = getIntent().getIntExtra(INDEX, -1);
        }

        mTitleTv = findViewById(R.id.tv_title);
        mPlayPauseIv = findViewById(R.id.iv_phone_play_pause);
        mRandomIv = findViewById(R.id.iv_phone_playback_mode);
        mPlayTimeTv = findViewById(R.id.tv_start_time);
        mDurationTv = findViewById(R.id.tv_end_time);
        mSb = findViewById(R.id.sb_play);
        mSb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mPlayTimeTv.setText(SongUtils.secondsToHms(seekBar.getProgress()));
                MusicPlayer.seekTo(PlaybackActivity.this, seekBar.getProgress() * 1000);
            }
        });

        RecyclerView queueRv = findViewById(R.id.rv_queue);
        queueRv.setLayoutManager(new LinearLayoutManager(this));
        queueRv.setAdapter(mAdapter = new BaseQuickAdapter<Song, BaseViewHolder>(R.layout.item_song, mSongs) {
            @Override
            protected void convert(BaseViewHolder helper, Song item) {
                helper.setText(R.id.tv_item_song_title, item.getTitle())
                        .setText(R.id.tv_item_song_artist, item.getArtist())
                        .setText(R.id.tv_item_song_duration, SongUtils.millisecondsToHms(item.getDuration()))
                        .setGone(R.id.cb_item_song, false);
            }
        });
        mAdapter.setOnItemClickListener((adapter, view, position) -> MusicPlayer.play(PlaybackActivity.this, SongQueueHelper.get().targetsToQueue(mSongs), position, null));

        findViewById(R.id.iv_phone_queue).setOnClickListener(this);
        findViewById(R.id.iv_phone_previous).setOnClickListener(this);
        mPlayPauseIv.setOnClickListener(this);
        findViewById(R.id.iv_phone_next).setOnClickListener(this);
        mRandomIv.setOnClickListener(this);
        refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        MusicConnectionManager.connect(this, new MusicConnection() {
            @Override
            protected void onMediaControllerConnected(MediaControllerCompat mediaController) {
                refresh();
                if (mSongsWaitForPlay == null || mIndexWaitForPlay == null || mIndexWaitForPlay >= mSongsWaitForPlay.size() || mIndexWaitForPlay < 0)
                    return;
                play(mSongsWaitForPlay, mIndexWaitForPlay);
                mSongsWaitForPlay = null;
                mIndexWaitForPlay = null;
            }

            @Override
            protected MediaControllerCompat.Callback onSetMediaControllerCallback() {
                return new MediaControllerCompat.Callback() {
                    @Override
                    public void onPlaybackStateChanged(PlaybackStateCompat state) {
                        refreshByPlaybackState(mCurrentState = state);
                        super.onPlaybackStateChanged(state);
                    }

                    @Override
                    public void onMetadataChanged(MediaMetadataCompat metadata) {
                        refreshByMediaMetadata(metadata);
                        refreshByPlaybackState(mCurrentState = MusicPlayer.getPlaybackState(PlaybackActivity.this));
                        super.onMetadataChanged(metadata);
                    }

                    @Override
                    public void onExtrasChanged(Bundle extras) {
                        refreshByExtras(extras);
                        super.onExtrasChanged(extras);
                    }

                    @Override
                    public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
                        super.onQueueChanged(queue);
                        refreshByQueue(queue);
                    }
                };
            }

            @Override
            protected void onMusicConnectionError(Throwable e) {
                e.printStackTrace();
            }
        });
    }

    private void refreshByQueue(List<MediaSessionCompat.QueueItem> queue) {
        if (queue == null) {
            return;
        }
        mSongs.clear();
        mSongs.addAll(SongQueueHelper.get().queueToTargets(queue));
        if (mAdapter != null)
            mAdapter.notifyDataSetChanged();
    }

    private void refreshByMediaMetadata(MediaMetadataCompat metadata) {
        if (metadata == null)
            return;
        Log.e(TAG, "refreshByMediaMetadata: " + metadata);
        if (mTitleTv != null) {
            // 更新title
            mTitleTv.setText(metadata.getDescription().getTitle());
        }
        //更新歌曲时长及SeekBar的max值
        int duration = (int) metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        if (mDurationTv != null)
            mDurationTv.setText(SongUtils.millisecondsToHms(duration));
        if (mSb != null)
            mSb.setMax(duration / 1000);
    }

    private void refreshByPlaybackState(PlaybackStateCompat playbackState) {
        if (playbackState == null)
            return;
        updateProgress();
        switch (playbackState.getState()) {
            case PlaybackStateCompat.STATE_PLAYING:
                if (mPlayPauseIv != null)
                    mPlayPauseIv.setImageResource(R.drawable.play_pause);
                scheduleSeekBarUpdate();
                break;
            case PlaybackStateCompat.STATE_ERROR:
            case PlaybackStateCompat.STATE_NONE:
            case PlaybackStateCompat.STATE_PAUSED:
            case PlaybackStateCompat.STATE_STOPPED:
                if (mPlayPauseIv != null)
                    mPlayPauseIv.setImageResource(R.drawable.play_play);
                stopSeekBarUpdate();
                break;
            case PlaybackStateCompat.STATE_FAST_FORWARDING:
            case PlaybackStateCompat.STATE_REWINDING:
            case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
            case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
            case PlaybackStateCompat.STATE_CONNECTING:
            case PlaybackStateCompat.STATE_BUFFERING:
                if (mPlayPauseIv != null)
                    mPlayPauseIv.setImageResource(R.drawable.play_pause);
                stopSeekBarUpdate();
                break;
        }
    }

    @MainThread
    private void refreshByExtras(Bundle extras) {
        if (extras == null)
            return;
        String playbackMode = extras.getString(PLAYBACK_MODE);
        if (playbackMode == null)
            return;
        switch (playbackMode) {
            case MODE_SEQUENCE:
                mRandomIv.setImageResource(R.drawable.recycle);
                break;
            case MODE_SINGLE:
                mRandomIv.setImageResource(R.drawable.single);
                break;
            case MODE_RANDOM:
                mRandomIv.setImageResource(R.drawable.random);
                break;
        }
    }

    private void scheduleSeekBarUpdate() {
        stopSeekBarUpdate();
        mSeekBarUpdateFuture = mSeekBarUpdateScheduler.scheduleAtFixedRate(() -> runOnUiThread(PlaybackActivity.this::updateProgress), 100, 1000, TimeUnit.MILLISECONDS);
    }

    private void stopSeekBarUpdate() {
        if (mSeekBarUpdateFuture != null) {
            mSeekBarUpdateFuture.cancel(true);
            mSeekBarUpdateFuture = null;
        }
    }

    @MainThread
    private void updateProgress() {
        if (mCurrentState == null)
            return;
        long currentPosition = mCurrentState.getPosition();
        if (mCurrentState.getState() == PlaybackStateCompat.STATE_PLAYING) {
            currentPosition += (int) (SystemClock.elapsedRealtime() - mCurrentState.getLastPositionUpdateTime()) * mCurrentState.getPlaybackSpeed();
        }
        if (mPlayTimeTv != null)
            mPlayTimeTv.setText(SongUtils.millisecondsToHms(currentPosition));
        if (mSb != null)
            mSb.setProgress((int) (currentPosition / 1000));
    }

    private void play(List<Song> songs, Integer index) {
        MusicPlayer.play(this, SongQueueHelper.get().targetsToQueue(songs), index, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MusicConnectionManager.disconnect(this);
        mSeekBarUpdateScheduler.shutdownNow();
        mSeekBarUpdateScheduler = null;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.iv_phone_previous:
                MusicPlayer.skipToPrevious(this);
                break;
            case R.id.iv_phone_play_pause:
                MusicPlayer.pauseOrPlay(this);
                break;
            case R.id.iv_phone_next:
                MusicPlayer.skipToNext(this);
                break;
            case R.id.iv_phone_playback_mode:
                MusicPlayer.swapPlaybackMode(this);
                break;
            default:
        }
    }
}