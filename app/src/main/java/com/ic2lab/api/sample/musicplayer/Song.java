package com.ic2lab.api.sample.musicplayer;

import android.os.Parcel;
import android.os.Parcelable;

public class Song implements Parcelable {

    private String id;
    private String artist;
    private String title;
    private String path;
    private int duration;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.id);
        dest.writeString(this.artist);
        dest.writeString(this.title);
        dest.writeString(this.path);
        dest.writeInt(this.duration);
    }

    public Song() {
    }

    protected Song(Parcel in) {
        this.id = in.readString();
        this.artist = in.readString();
        this.title = in.readString();
        this.path = in.readString();
        this.duration = in.readInt();
    }

    public static final Parcelable.Creator<Song> CREATOR = new Parcelable.Creator<Song>() {
        @Override
        public Song createFromParcel(Parcel source) {
            return new Song(source);
        }

        @Override
        public Song[] newArray(int size) {
            return new Song[size];
        }
    };

    @Override
    public String toString() {
        return "Song{" +
                "id='" + id + '\'' +
                ", artist='" + artist + '\'' +
                ", title='" + title + '\'' +
                ", path='" + path + '\'' +
                ", duration=" + duration +
                '}';
    }
}
