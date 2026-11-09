package com.example.oldmusic;

public class Song {

    public long id;
    public String title;
    public String artist;
    public String path;
    public long duration;

    public Song() {
        this.title = "";
        this.artist = "";
        this.path = "";
    }

    public Song(long id, String title, String artist, String path, long duration) {
        this.id = id;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.path = path == null ? "" : path;
        this.duration = duration;
    }

    @Override
    public String toString() {
        return title + " - " + artist;
    }
}
