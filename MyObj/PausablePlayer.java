package MyObj;

import javazoom.jl.player.*;
import javazoom.jl.decoder.*;

import java.io.InputStream;

public class PausablePlayer {
    private final static int NOTSTARTED = 0;
    private final static int PLAYING = 1;
    private final static int PAUSED = 2;
    private final static int FINISHED = 3;
    
    private final Player player;
    private final Object playerLock = new Object(); // locking object used to communicate with player thread
    
    private int playerStatus = NOTSTARTED; // status variable what player thread is doing/supposed to do
    private long elapsedTimeMillis = 0; // Elapsed playback time
    private long lastPlayTimestamp = 0; // Timestamp when playback starts/resumes


    public PausablePlayer(final InputStream inputStream) throws JavaLayerException {
        this.player = new Player(inputStream);
    }

    //Starts playback (resumes if paused)
    public void play() throws JavaLayerException {
        synchronized (playerLock) {
            switch (playerStatus) {
                case NOTSTARTED:
                    final Runnable r = new Runnable() {
                        public void run() {
                            lastPlayTimestamp = System.currentTimeMillis();
                            playInternal();
                        }
                    };
                    final Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setPriority(Thread.MAX_PRIORITY);
                    playerStatus = PLAYING;
                    t.start();
                    break;
                case PAUSED:
                    resume();
                    break;
                default:
                    break;
            }
        }
    }

    //Pauses playback. Returns true if new state is PAUSED.
    public boolean pause() {
        synchronized (playerLock) {
            if (playerStatus == PLAYING) {
                playerStatus = PAUSED;
                elapsedTimeMillis += System.currentTimeMillis() - lastPlayTimestamp;
            }
            return playerStatus == PAUSED;
        }
    }

    //Resumes playback. Returns true if the new state is PLAYING.
    public boolean resume() {
        synchronized (playerLock) {
            if (playerStatus == PAUSED) {
                playerStatus = PLAYING;
                lastPlayTimestamp = System.currentTimeMillis();
                playerLock.notifyAll();
            }
            
            return playerStatus == PLAYING;
        }
    }

    //Stops playback. If not playing, does nothing
    public void stop() {
        synchronized (playerLock) {
            elapsedTimeMillis = 0; // Reset elapsed time
            lastPlayTimestamp = 0; // Reset the timestamp
            playerStatus = FINISHED;
            playerLock.notifyAll();
        }
    }

    //Closes the player, regardless of current state.
    public void close() {
        synchronized (playerLock) {
            playerStatus = FINISHED;
        }
        try {
            player.close();
        } catch (final Exception e) {
            // ignore, we are terminating anyway
        }
    }

    //Playback happens in this method, which is run in a separate thread
    private void playInternal() {
        // Playback finsihes when FINISHED is set or player.play(1) = false
        while (playerStatus != FINISHED) {
            try {
                if (!player.play(1)) {
                    break; // song finished playing
                }
            } catch (final JavaLayerException e) {
                break;
            }
            // check if paused or terminated
            synchronized (playerLock) {
                while (playerStatus == PAUSED) {
                    try {
                        playerLock.wait(); //THIS IS THE PAUSE
                    } catch (final InterruptedException e) {
                        // terminate player
                        break;
                    }
                }
            }
        }

        close(); // close when playback ends
    }

    public long getElapsedTimeMillis() {
        synchronized (playerLock) {
            if (playerStatus == PLAYING) {
                return elapsedTimeMillis + (System.currentTimeMillis() - lastPlayTimestamp);
            }
            return elapsedTimeMillis;
        }
    }    
}

