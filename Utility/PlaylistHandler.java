package Utility;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;

import MyObj.Playlist;
import MyObj.Song;

public class PlaylistHandler {
    SongHandler sh = new SongHandler();
    MusicUtil mp = new MusicUtil();

    private int getMaxOrderIndexForPlaylist(Connection conn, int playlistId) {
        String query = "SELECT MAX(order_index) FROM Stream_playlists_songs WHERE id_playlist = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playlistId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting max order_index: " + e.getMessage());
        }
        return 1;
    }

    //?-----ADDING SONG TO PLAYLIST + Table on DB
    public void addSongToExistingPlaylist(Connection conn, HashMap<Integer, Playlist> playlists, int playlistId, Song song) {
        String playlistName = playlists.get(playlistId).getPlaylistName();
        playlists.get(playlistId).addSong(song);
        
        int songId = song.getId_song();
        int orderIndex = getMaxOrderIndexForPlaylist(conn, playlistId) + 1;
        String insertQuery = "INSERT INTO Stream_playlists_songs (id_playSongs, id_playlist, name, id_song, order_index) VALUES (Stream_playlists_songs_SEQ.nextval, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
            stmt.setInt(1, playlistId);
            stmt.setString(2, playlistName);
            stmt.setInt(3, songId);
            stmt.setInt(4, orderIndex);
            stmt.executeUpdate();
            System.out.println("Added song to playlist in database: " + sh.getSongnameById(conn, songId));
        } catch (SQLException e) {
            System.err.println("Error saving song to database: " + e.getMessage());
        }
    }

    //?-----REMOVING SONG FROM PLAYLIST + Table on DB
    public void removeSongFromPlaylist(Connection conn, HashMap<Integer, Playlist> playlists, int playlistId, Song song) {   
        playlists.get(playlistId).removeSong(song);
        
        int songId = song.getId_song();
        String songName = sh.getSongnameById(conn, songId);
        String deleteQuery = "DELETE FROM Stream_playlists_songs WHERE id_playlist = ? AND id_song = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteQuery)) {
            stmt.setInt(1, playlistId);
            stmt.setInt(2, songId);
            stmt.executeUpdate();
            System.out.println("Removed song from playlist in database: " + songName);
        } catch (SQLException e) {
            System.err.println("Error removing song from database: " + e.getMessage());
        }
    }

    //?-----CREATING NEW PLAYLIST and add to AllPlaylists
    public void createNewPlaylist(HashMap<Integer, Playlist> playlists, String playlistName, int playlistID) {
        Playlist newPlaylist = new Playlist(playlistID, playlistName);
        playlists.put(playlistID, newPlaylist);
    }

    //?-----DELETING PLAYLIST + on the database
    public void deletePlaylistWithDataOnDB(Connection conn, HashMap<Integer, Playlist> playlists, int playlistId) {
        playlists.remove(playlistId);
        String sql = "Delete from Stream_playlists_songs where id_playlist = ?";
        try(PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playlistId);
            stmt.executeUpdate();
            System.out.println("Playlist ID("+ playlistId + ")'s data removed from database");
        } catch (SQLException e) {
            System.err.println("Error removing playlist's data from database: " + e.getMessage());
        }
    }

    //?-----COPY PLAYLISTS FROM DB
    public int getMaxPlaylistID(Connection conn) {
        String query = "SELECT MAX(id_playlist) FROM Stream_playlists_songs";
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
    
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error getting max id_playlist: " + e.getMessage());
        }
        return -1;
    }

    public boolean hasDataInPlaylistSongs(Connection conn) {
        String query = "SELECT COUNT(*) FROM Stream_playlists_songs";
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
    
            if (rs.next()) {
                int rowCount = rs.getInt(1);
                return rowCount > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error checking table data: " + e.getMessage());
        }
        return false; // Return false if no rows exist
    }
    
    public HashMap<Integer, Playlist> retrievePlaylistsFromDB(Connection conn, HashMap<Integer, Song> allSongs) {
        HashMap<Integer, Playlist> playlistMap = new HashMap<>();

        String query = "SELECT p.id_playlist, p.name AS playlist_name, s.id_song, s.title, s.extension, s.artist, s.album, s.genre, s.yearOfRelease FROM Stream_playlists_songs p JOIN Stream_songs s ON p.id_song = s.id_song ORDER BY p.id_playlist ASC, p.order_index ASC";

        try (PreparedStatement stmt = conn.prepareStatement(query); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int playlistID = rs.getInt("id_playlist");
                String playlistName = rs.getString("playlist_name");
                int songID = rs.getInt("id_song");

                Playlist playlist = playlistMap.get(playlistID);
                if (playlist == null) {
                    playlist = new Playlist(playlistID, playlistName);
                    playlistMap.put(playlistID, playlist);
                }

                // Fetch or create the Song object
                Song song = allSongs.get(songID);
                if (song == null) {
                    String title = rs.getString("title");
                    String extension = rs.getString("extension");
                    String artist = rs.getString("artist");
                    String album = rs.getString("album");
                    String genre = rs.getString("genre");
                    int yearOfRelease = rs.getInt("yearOfRelease");

                    song = new Song(songID, title, extension, artist, album, genre, yearOfRelease);
                    allSongs.put(songID, song);
                }

                playlist.addSong(song);
            }
        } catch (SQLException e) {
            System.err.println("Error loading playlists: " + e.getMessage());
        }

        return playlistMap;
    }

    //?-----SENDING CURRENT PLAYING PLAYLIST
    public void sendCurrentPlaylist(HashMap<Integer, Playlist> allPlaylists, int currentPlaylistIndex, PrintWriter writer) {
        Playlist currentPlaylist = allPlaylists.get(currentPlaylistIndex);
        writer.println("CURRENT_PLAYLIST: ID(" + currentPlaylistIndex + ") " + currentPlaylist.getPlaylistName());
    }

    //?-----SEND LIST OF PLAYLIST
    public void sendListOfPlaylist(HashMap<Integer, Playlist> allPlaylists, PrintWriter writer) {
        String playlistList = mp.listPlaylists(allPlaylists);
        writer.println(playlistList);
        writer.println("END_RESULTS");
    }
    
    public void sendListOfPlaylistSongs(Playlist p, PrintWriter writer) {
        String playlistSongs = mp.listAllSongsInPlaylist(p);
        writer.println(playlistSongs);
        writer.println("END_RESULTS");
    }

    //?-----MOVE SONG IN PL AND ON DB
    private void moveSongInPlaylistAndDB(Connection conn, Playlist playlist, int playlistId, Song song, int newIndex) {
        if(newIndex >= playlist.getSongQueueSize()) {
            newIndex = playlist.getSongQueueSize() -1;
        }
        playlist.moveSong(song, newIndex);
        try {
            String updateQuery = "UPDATE Stream_playlists_songs SET order_index = ? WHERE id_playlist = ? AND id_song = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
                stmt.setInt(1, newIndex);
                stmt.setInt(2, playlistId);
                stmt.setInt(3, song.getId_song());
                stmt.executeUpdate();
            }
            System.out.println("Updated song position in database: " + song.getTitle());
        } catch (SQLException e) {
            System.err.println("Error updating song position in database: " + e.getMessage());
        }
    }
    
    private void syncPlaylistOrderWithDB(Connection conn, Playlist playlist, int playlistId) {
        List<Song> songQueue = playlist.getQueue();
        String updateQuery = "UPDATE Stream_playlists_songs SET order_index = ? WHERE id_playlist = ? AND id_song = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
            int index = 0;
            for (Song song : songQueue) {
                stmt.setInt(1, index++); // Increment index for each song
                stmt.setInt(2, playlistId);
                stmt.setInt(3, song.getId_song());
                stmt.executeUpdate();
            }
            System.out.println("Synchronized playlist order with database.");
        } catch (SQLException e) {
            System.err.println("Error synchronizing playlist order with database: " + e.getMessage());
        }
    }

    public void moveSongInPlaylistAndSyncOrder(Connection conn, Playlist playlist, int playlistId, Song song, int newIndex) {
        moveSongInPlaylistAndDB(conn, playlist, playlistId, song, newIndex);
        syncPlaylistOrderWithDB(conn, playlist, playlistId);
    }
    

}
