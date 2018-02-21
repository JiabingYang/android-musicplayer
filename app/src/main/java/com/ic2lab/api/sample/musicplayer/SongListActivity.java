package com.ic2lab.api.sample.musicplayer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;

import java.util.ArrayList;
import java.util.List;

public class SongListActivity extends AppCompatActivity {

    private static final int MY_PERMISSIONS_MUSIC = 0;

    private BaseQuickAdapter<BooleanObject<Song>, BaseViewHolder> mAdapter;
    private List<BooleanObject<Song>> mSongs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_list);

        initViews();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS_MUSIC);
            }
        } else {
            loadLocalSongs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MY_PERMISSIONS_MUSIC)
            return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadLocalSongs();
        }
    }

    private void loadLocalSongs() {
        List<Song> songs = SongUtils.getLocalSongs(this);
        for (Song song : songs) {
            mSongs.add(new BooleanObject<>(song, false));
        }
        mAdapter.notifyDataSetChanged();
    }

    private void initViews() {
        RecyclerView rv = findViewById(R.id.rv_song_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(mAdapter = new BaseQuickAdapter<BooleanObject<Song>, BaseViewHolder>(R.layout.item_song, mSongs) {
            @Override
            protected void convert(BaseViewHolder helper, final BooleanObject<Song> item) {
                helper.setText(R.id.tv_item_song_title, item.getObject().getTitle())
                        .setText(R.id.tv_item_song_artist, item.getObject().getArtist())
                        .setText(R.id.tv_item_song_duration, SongUtils.millisecondsToHms(item.getObject().getDuration()))
                        .setChecked(R.id.cb_item_song, item.isBool())
                        .setOnCheckedChangeListener(R.id.cb_item_song, (compoundButton, b) -> {
                            if (!compoundButton.isPressed())
                                return;
                            item.setBool(compoundButton.isChecked());
                            mAdapter.notifyDataSetChanged();
                        });
            }
        });
        findViewById(R.id.tv_song_list_play).setOnClickListener(view -> {
            List<Song> selectedSongs = new ArrayList<>();
            for (BooleanObject<Song> song : mSongs) {
                if (song.isBool())
                    selectedSongs.add(song.getObject());
            }
            if (selectedSongs.isEmpty()) {
                Toast.makeText(SongListActivity.this, "未选择任何歌曲", Toast.LENGTH_SHORT).show();
                return;
            }
            PlaybackActivity.start(SongListActivity.this, selectedSongs, 0);
        });
        findViewById(R.id.tv_song_list_status).setOnClickListener(view -> PlaybackActivity.start(SongListActivity.this));
        findViewById(R.id.tv_song_list_clear).setOnClickListener(view -> {
            for (BooleanObject<Song> song : mSongs) {
                song.setBool(false);
            }
            mAdapter.notifyDataSetChanged();
        });
    }
}
