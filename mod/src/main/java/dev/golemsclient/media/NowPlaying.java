package dev.golemsclient.media;

import com.sun.jna.Platform;
import dev.golemsclient.GolemsClient;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * What is playing on this PC right now (Spotify, YouTube in a browser, any app that shows up in Windows' media
 * controls), read on one background thread once a second; and play/pause/next/previous for it. Windows only; on
 * other systems nothing is ever playing.
 */
public final class NowPlaying {
    public enum Command { TOGGLE, NEXT, PREVIOUS }

    private static final ConcurrentLinkedQueue<Command> COMMANDS = new ConcurrentLinkedQueue<>();
    private static volatile MediaSession.Snapshot current;
    private static volatile boolean started;
    private static volatile long wantedUntil;

    private NowPlaying() {
    }

    /** The newest reading, or null when nothing is playing (or it can't be read). Starts the reader if needed. */
    public static MediaSession.Snapshot get() {
        wantedUntil = System.currentTimeMillis() + 5000L;
        start();
        return current;
    }

    public static void send(Command command) {
        COMMANDS.add(command);
    }

    /** Where the song is now, in milliseconds, counting on from the last reading while it plays. */
    public static long position(MediaSession.Snapshot s) {
        if (s == null) return 0L;
        long pos = s.positionMs();
        if (s.state() == MediaSession.PlaybackState.PLAYING) {
            pos += (long) ((System.currentTimeMillis() - s.sampledAtMs()) * (s.rate() > 0 ? s.rate() : 1.0));
        }
        return Math.max(0L, s.durationMs() > 0 ? Math.min(pos, s.durationMs()) : pos);
    }

    private static synchronized void start() {
        if (started || !Platform.isWindows()) return;
        started = true;
        Thread t = new Thread(NowPlaying::run, "Golem's Client media");
        t.setDaemon(true);
        t.start();
    }

    private static void run() {
        MediaSession session = new MediaSession(null);
        int failures = 0;
        while (true) {
            try {
                Command c;
                boolean acted = false;
                while ((c = COMMANDS.poll()) != null) {
                    switch (c) {
                        case TOGGLE -> session.togglePlayPause();
                        case NEXT -> session.skipNext();
                        case PREVIOUS -> session.skipPrevious();
                    }
                    acted = true;
                }
                if (session.isUnsupported()) {
                    current = null;
                    return;
                }
                // read only while something on screen wants it
                if (acted || System.currentTimeMillis() < wantedUntil) {
                    MediaSession.Snapshot s = session.poll();
                    current = s != null && s.title() != null && !s.title().isBlank() ? s : null;
                }
                failures = 0;
                Thread.sleep(acted ? 150L : 1000L);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                if (++failures == 1) GolemsClient.LOGGER.warn("Couldn't read what's playing", t);
                if (failures > 20) {
                    current = null;
                    return;
                }
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
