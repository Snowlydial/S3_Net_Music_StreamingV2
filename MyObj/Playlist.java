package MyObj;

import java.util.ArrayList;
import java.util.List;

public class Playlist {
    private int id_playlist;
    private String name;
    private List<Song> songQueue;
    private int currentIndex;

    public Playlist(int _playlistID, String _playlistName) {
        this.id_playlist = _playlistID;
        setPlaylistName(_playlistName);
        this.songQueue = new ArrayList<>();
        setCurrentIndex(-1); // No song is playing initially
    }

    //*--------SETTERS
    public void setCurrentIndex(int index) {
        currentIndex = index;
    }

    public void setPlaylistName(String _playlistName) {
        this.name = _playlistName;
    }

    //*--------GETTERS
    // get latest added song
    public Song getLatestSong() {
        if (!songQueue.isEmpty()) {
            return songQueue.get(songQueue.size()-1);
        }
        return null; // No song is selected
    }

    public Song getSongAtIndex(int index) {
        return songQueue.get(index);
    } 

    // Retrieve all songs in the playlist
    public List<Song> getQueue() {
        return songQueue;
    }

    public String getPlaylistName() {
        return name;
    }

    public int getPlaylistID() {
        return id_playlist;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getSongQueueSize() {
        return songQueue.size();
    }

    //*--------UTILITY
    // Add a song to the playlist
    public void addSong(Song song) {
        songQueue.add(song);
    }

    public void removeSong(Song song) {
        songQueue.remove(song);
    }

    public void moveSong(Song song, int index) {
        songQueue.remove(song);
        songQueue.add(index, song);
    }

    // Check if song is in the playlist
    public boolean containsSong(Song song) {
        return songQueue.contains(song);
    }

    // Get what song is currently playing for next and previous
    public int getSongIndexInPlaylist(Song song) {
        return songQueue.indexOf(song);
    }

    public Song nextSong(int curretlyPlaying) {
        if(curretlyPlaying + 1 >= songQueue.size()) {
            return songQueue.get(0);
        }
        return songQueue.get(curretlyPlaying + 1);
    }

    public Song previousSong(int curretlyPlaying) {
        if(curretlyPlaying - 1 < 0) {
            return songQueue.get(songQueue.size() - 1);
        }
        return songQueue.get(curretlyPlaying - 1);
    }

    public boolean isSongQueueEmpty() {
        return songQueue.isEmpty();
    }

    public void clearSongQueue() {
        songQueue.clear();
    }



}

