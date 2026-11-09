package com.example.oldmusic;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements PlaybackService.Listener {

    private static final int REQ_PERM = 1001;

    private PlaybackService service;
    private boolean bound = false;

    private LyricView lyricView;
    private ListView listView;
    private TextView titleView;
    private TextView artistView;
    private SeekBar progressBar;
    private Button playBtn;
    private Button prevBtn;
    private Button nextBtn;
    private Button modeBtn;

    private ArrayAdapter<Song> adapter;
    private final List<Song> songs = new ArrayList<Song>();
    private final Handler handler = new Handler();

    private List<LrcParser.Line> lyrics = new ArrayList<LrcParser.Line>();
    private boolean userSeeking = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((PlaybackService.LocalBinder) binder).getService();
            service.setListener(MainActivity.this);
            bound = true;

            songs.clear();
            songs.addAll(service.getPlaylist());
            adapter.notifyDataSetChanged();

            if (songs.isEmpty()) {
                loadSongs();
                service.setPlaylist(new ArrayList<Song>(songs));
                if (!songs.isEmpty()) {
                    service.playIndex(0);
                }
            } else {
                onSongChanged(service.getCurrentSong());
            }

            updateModeButton();
            updatePlayButton(service.isPlaying());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UikitBridge.init(this);
        setContentView(R.layout.activity_main);
        initViews();
        startAndBindService();
        handler.post(progressTask);
    }

    private void initViews() {
        titleView = (TextView) findViewById(R.id.song_title);
        artistView = (TextView) findViewById(R.id.song_artist);
        lyricView = (LyricView) findViewById(R.id.lyric_view);
        progressBar = (SeekBar) findViewById(R.id.progress);
        playBtn = (Button) findViewById(R.id.btn_play);
        prevBtn = (Button) findViewById(R.id.btn_prev);
        nextBtn = (Button) findViewById(R.id.btn_next);
        modeBtn = (Button) findViewById(R.id.btn_mode);
        listView = (ListView) findViewById(R.id.song_list);

        adapter = new ArrayAdapter<Song>(this, 0, songs) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    v = getLayoutInflater().inflate(R.layout.item_song, parent, false);
                }
                Song s = getItem(position);
                TextView title = (TextView) v.findViewById(R.id.item_title);
                TextView artist = (TextView) v.findViewById(R.id.item_artist);
                title.setText(s.title);
                artist.setText(s.artist);
                return v;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (bound && service != null) {
                    service.playIndex(position);
                }
            }
        });

        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bound && service != null) {
                    service.toggle();
                }
            }
        });
        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bound && service != null) {
                    service.prev();
                }
            }
        });
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bound && service != null) {
                    service.next();
                }
            }
        });
        modeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bound && service != null) {
                    service.setMode((service.getMode() + 1) % 3);
                    updateModeButton();
                }
            }
        });

        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && bound && service != null) {
                    service.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
            }
        });
    }

    private void startAndBindService() {
        Intent intent = new Intent(this, PlaybackService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private final Runnable progressTask = new Runnable() {
        @Override
        public void run() {
            if (bound && service != null && !userSeeking) {
                int pos = service.getPosition();
                int dur = service.getDuration();
                if (dur > 0) {
                    progressBar.setMax(dur);
                    progressBar.setProgress(pos);
                } else {
                    progressBar.setMax(0);
                    progressBar.setProgress(0);
                }
                updateLyric(pos);
            }
            handler.postDelayed(this, 300L);
        }
    };

    private void updateLyric(int position) {
        if (lyrics == null || lyrics.isEmpty()) {
            return;
        }
        int idx = LrcParser.findIndex(lyrics, position);
        if (idx >= 0) {
            lyricView.setCurrentIndex(idx);
        }
    }

    private void loadSongs() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERM);
            return;
        }

        songs.clear();
        Cursor cursor = null;
        try {
            String[] projection = new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION
            };
            cursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    MediaStore.Audio.Media.IS_MUSIC + "!=0",
                    null,
                    MediaStore.Audio.Media.TITLE + " ASC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    songs.add(new Song(
                            cursor.getLong(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getString(3),
                            cursor.getLong(4)));
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "扫描本地音乐失败", Toast.LENGTH_SHORT).show();
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception ignored) {
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            loadSongs();
            if (bound && service != null) {
                service.setPlaylist(new ArrayList<Song>(songs));
                if (!songs.isEmpty()) {
                    service.playIndex(0);
                }
            }
        }
    }

    private void updateLyricsFile(Song song) {
        lyrics = new ArrayList<LrcParser.Line>();
        if (song != null && song.path != null && song.path.length() > 0) {
            String lrcPath = song.path;
            int dot = lrcPath.lastIndexOf('.');
            if (dot > 0) {
                lrcPath = lrcPath.substring(0, dot) + ".lrc";
            }
            lyrics = LrcParser.parse(lrcPath);
        }
        lyricView.setLines(lyrics);
    }

    private void updatePlayButton(boolean playing) {
        playBtn.setText(playing ? "暂停" : "播放");
    }

    private void updateModeButton() {
        if (bound && service != null) {
            int m = service.getMode();
            if (m == PlaybackService.MODE_REPEAT_ONE) {
                modeBtn.setText("单曲");
            } else if (m == PlaybackService.MODE_SHUFFLE) {
                modeBtn.setText("随机");
            } else {
                modeBtn.setText("顺序");
            }
        }
    }

    @Override
    public void onPlayStateChanged(boolean playing) {
        updatePlayButton(playing);
    }

    @Override
    public void onSongChanged(Song song) {
        if (song == null) {
            return;
        }
        titleView.setText(song.title);
        artistView.setText(song.artist);
        updateLyricsFile(song);
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).path != null && songs.get(i).path.equals(song.path)) {
                listView.setItemChecked(i, true);
                listView.setSelection(i);
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(progressTask);
        if (bound) {
            try {
                service.setListener(null);
            } catch (Exception ignored) {
            }
            unbindService(connection);
            bound = false;
        }
        super.onDestroy();
    }
}
