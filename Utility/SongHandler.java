package Utility;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import MyObj.Song;

public class SongHandler {
    // Copy all songs from the DB to a HashMap
    public HashMap<Integer, Song> allAvailableSongsOnTheDB(Connection conn) {
        HashMap<Integer, Song> songsMap = new HashMap<>();

        String query = "SELECT id_song, title, extension, artist, album, genre, yearOfRelease FROM Stream_songs";
        try (PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int id_song = rs.getInt("id_song");
                String title = rs.getString("title");
                String extension = rs.getString("extension");
                String artist = rs.getString("artist");
                String album = rs.getString("album");
                String genre = rs.getString("genre");
                int yearOfRelease = rs.getInt("yearOfRelease");

                Song song = new Song(id_song, title, extension, artist, album, genre, yearOfRelease);

                songsMap.put(id_song, song);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return songsMap;
    }

    // More preferable if there is songs of same name
    public Song findSongById(HashMap<Integer, Song> songsMap, int songId) {
        return songsMap.get(songId);
    }
        
    // UNUSED: same job as above but the old way (search on DB)
    public String getSongnameById(Connection conn, int songId) {
        String query = "SELECT title FROM Stream_songs WHERE id_song = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, songId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("title");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
