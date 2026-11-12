package com.example.oldmusic;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PlaybackService extends Service {

    public static final int MODE_SEQUENCE = 0;
    public static final int MODE_REPEAT_ONE = 1;
    public static final int MODE_SHUFFLE = 2;

    private static final String PREFS = "oldmusic_state";
    private static final String FIELD_SEP = "\u0001";
    private static final String RECORD_SEP = "\u0002";

    public interface Listener {
        void onPlayStateChanged(boolean playing);
        void onSongChanged(Song song);
    }

    public class LocalBinder extends Binder {
        public PlaybackService getService() { return PlaybackService.this; }
    }

    private final IBinder binder = new LocalBinder();
    private MediaPlayer player;
    private final List<Song> playlist = new ArrayList<Song>();
    private int currentIndex = -1;
    private int mode = MODE_SEQUENCE;
    private boolean prepared = false;
    private int pendingSeek = -1;

    private PowerManager.WakeLock wakeLock;
    private AudioManager audioManager;
    private Equalizer equalizer;
    private final Handler handler = new Handler();

    private long sleepEndTime = 0L;
    private Listener listener;
    private BroadcastReceiver mediaButtonReceiver;
    private BroadcastReceiver noisyReceiver;

    @Override
    public void onCreate() {
        super.onCreate();

        player = new MediaPlayer();
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                prepared = true;
                if (pendingSeek >= 0) {
                    try { player.seekTo(pendingSeek); } catch (Exception ignored) {}
                    pendingSeek = -1;
                }
                player.start();
                acquireWakeLock();
                notifyState(true);
                initEqualizer();
            }
        });
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) { next(); }
        });
        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                prepared = false;
                next();
                return true;
            }
        });

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "oldmusic:Playback");
        wakeLock.setReferenceCounted(false);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        restoreState();
        registerMediaButton();
        registerNoisyReceiver();
    }

    private void registerNoisyReceiver() {
        noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                    pause(); // 拔出耳机自动暂停
                }
            }
        };
        registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    private void initEqualizer() {
        try {
            if (equalizer == null && player != null) {
                equalizer = new Equalizer(0, player.getAudioSessionId());
                equalizer.setEnabled(true);
            }
        } catch (Exception ignored) {}
    }

    public void setEqualizerPreset(int preset) {
        if (equalizer != null) {
            try {
                equalizer.usePreset((short) preset);
            } catch (Exception ignored) {}
        }
    }

    public void setSleepTimer(int minutes) {
        handler.removeCallbacks(sleepRunnable);
        if (minutes <= 0) {
            sleepEndTime = 0L;
            return;
        }
        sleepEndTime = System.currentTimeMillis() + minutes * 60L * 1000L;
        handler.postDelayed(sleepRunnable, minutes * 60L * 1000L);
    }

    private final Runnable sleepRunnable = new Runnable() {
        @Override
        public void run() {
            if (sleepEndTime > 0L && System.currentTimeMillis() >= sleepEndTime) {
                // 音量淡出效果 (简易版)
                for (int i = 0; i < 20; i++) {
                    try {
                        Thread.sleep(100);
                        float vol = 1.0f - (i / 20.0f);
                        player.setVolume(vol, vol);
                    } catch (Exception ignored) {}
                }
                pause();
                player.setVolume(1.0f, 1.0f);
                sleepEndTime = 0L;
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    public void setListener(Listener l) { listener = l; }

    public List<Song> getPlaylist() { return playlist; }

    public void setPlaylist(List<Song> songs) {
        playlist.clear();
        if (songs != null) playlist.addAll(songs);
        saveState();
        if (listener != null) listener.onSongChanged(getCurrentSong());
    }

    public Song getCurrentSong() {
        if (currentIndex < 0 || currentIndex >= playlist.size()) return null;
        return playlist.get(currentIndex);
    }

    public int getCurrentIndex() { return currentIndex; }
    public int getMode() { return mode; }
    public void setMode(int m) { mode = m; saveState(); }
    public boolean isPlaying() { return player != null && prepared && player.isPlaying(); }

    public int getPosition() {
        if (player != null && prepared) {
            try { return player.getCurrentPosition(); } catch (Exception ignored) {}
        }
        return 0;
    }

    public int getDuration() {
        if (player != null && prepared) {
            try { return player.getDuration(); } catch (Exception ignored) {}
        }
        return 0;
    }

    public void playIndex(int index) {
        if (index < 0 || index >= playlist.size()) return;
        currentIndex = index;
        playCurrent();
    }

    private void playCurrent() {
        Song song = getCurrentSong();
        if (song == null) return;
        try {
            prepared = false;
            player.reset();
            player.setDataSource(song.path);
            player.prepareAsync();
            acquireWakeLock();
            requestAudioFocus();
            saveState();
            if (listener != null) listener.onSongChanged(song);
            updateNotification();
        } catch (Exception e) {
            prepared = false;
            next();
        }
    }

    public void play() {
        if (currentIndex < 0 && !playlist.isEmpty()) {
            playIndex(0);
            return;
        }
        if (prepared) {
            player.start();
            acquireWakeLock();
            notifyState(true);
        } else if (getCurrentSong() != null) {
            playCurrent();
        }
    }

    public void pause() {
        if (prepared && player.isPlaying()) {
            player.pause();
            notifyState(false);
        }
        releaseWakeLock();
    }

    public void toggle() {
        if (isPlaying()) pause();
        else play();
    }

    public void next() {
        if (playlist.isEmpty()) return;
        int nextIndex;
        if (mode == MODE_REPEAT_ONE) nextIndex = currentIndex;
        else if (mode == MODE_SHUFFLE && playlist.size() > 1) {
            Random r = new Random();
            do { nextIndex = r.nextInt(playlist.size()); } while (nextIndex == currentIndex);
        } else {
            nextIndex = (currentIndex + 1) % playlist.size();
        }
        playIndex(nextIndex);
    }

    public void prev() {
        if (playlist.isEmpty()) return;
        int prevIndex = (mode == MODE_REPEAT_ONE) ? currentIndex : (currentIndex - 1 + playlist.size()) % playlist.size();
        playIndex(prevIndex);
    }

    public void seekTo(int ms) {
        if (prepared) {
            try { player.seekTo(ms); } catch (Exception ignored) {}
        }
    }

    private void notifyState(boolean playing) {
        if (listener != null) listener.onPlayStateChanged(playing);
        updateNotification();
    }

    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private void requestAudioFocus() {
        if (audioManager != null && Build.VERSION.SDK_INT >= 8) {
            try { audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN); } catch (Exception ignored) {}
        }
    }

    @SuppressWarnings("deprecation")
    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Song song = getCurrentSong();
        if (song == null) { nm.cancel(1); return; }

        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

        Notification notification;
        if (Build.VERSION.SDK_INT >= 11) {
            notification = new Notification.Builder(this)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle(song.title)
                    .setContentText(song.artist)
                    .setOngoing(isPlaying())
                    .setContentIntent(pi).getNotification();
        } else {
            notification = new Notification(android.R.drawable.ic_media_play, song.title, System.currentTimeMillis());
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            try {
                java.lang.reflect.Method method = Notification.class.getMethod("setLatestEventInfo", Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
                method.invoke(notification, this, song.title, song.artist, pi);
            } catch (Exception ignored) {}
        }
        nm.notify(1, notification);
    }

    private void registerMediaButton() {
        mediaButtonReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) return;
                KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return;
                int code = event.getKeyCode();
                if (code == KeyEvent.KEYCODE_HEADSETHOOK || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) toggle();
                else if (code == KeyEvent.KEYCODE_MEDIA_NEXT) next();
                else if (code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) prev();
            }
        };
        registerReceiver(mediaButtonReceiver, new IntentFilter(Intent.ACTION_MEDIA_BUTTON));
    }

    private void saveState() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < playlist.size(); i++) {
            Song s = playlist.get(i);
            if (i > 0) sb.append(RECORD_SEP);
            sb.append(s.id).append(FIELD_SEP).append(s.title).append(FIELD_SEP)
              .append(s.artist).append(FIELD_SEP).append(s.path).append(FIELD_SEP).append(s.duration);
        }
        editor.putString("playlist", sb.toString());
        editor.putInt("index", currentIndex);
        editor.putInt("mode", mode);
        editor.putInt("position", getPosition());
        editor.commit();
    }

    private void restoreState() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        mode = sp.getInt("mode", MODE_SEQUENCE);
        pendingSeek = sp.getInt("position", -1);
        String raw = sp.getString("playlist", "");
        playlist.clear();
        if (raw != null && raw.length() > 0) {
            String[] records = raw.split(RECORD_SEP, -1);
            for (String record : records) {
                if (record.length() == 0) continue;
                String[] parts = record.split(FIELD_SEP, -1);
                if (parts.length < 5) continue;
                try {
                    Song s = new Song();
                    s.id = Long.parseLong(parts[0]);
                    s.title = parts[1];
                    s.artist = parts[2];
                    s.path = parts[3];
                    s.duration = Long.parseLong(parts[4]);
                    playlist.add(s);
                } catch (Exception ignored) {}
            }
        }
        int idx = sp.getInt("index", -1);
        if (idx >= 0 && idx < playlist.size()) currentIndex = idx;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(sleepRunnable);
        if (mediaButtonReceiver != null) try { unregisterReceiver(mediaButtonReceiver); } catch (Exception ignored) {}
        if (noisyReceiver != null) try { unregisterReceiver(noisyReceiver); } catch (Exception ignored) {}
        if (equalizer != null) try { equalizer.release(); } catch (Exception ignored) {}
        if (player != null) try { player.release(); } catch (Exception ignored) {}
        releaseWakeLock();
        super.onDestroy();
    }
}
