package Utility;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.*;
import java.net.Socket;

import com.mpatric.mp3agic.*;
import javazoom.jl.decoder.JavaLayerException;
import MyObj.PausablePlayer;
import MyObj.Playlist;
import MyObj.Song;

public class MusicUtil {
    static Map<String, Object> confGetter = ConfigUtil.getConfig("config.json");
    private PausablePlayer pausablePlayer;
    private String musicFolder = (String)confGetter.get("musicFolder"); // a mettre dans un conf
    private SongHandler sh = new SongHandler();

    //*---------------------------ESSENTIALS---------------------------
    //?-----RECHERCHE NON FILTRE DE MUSIQUE dans database
    public String searchMusic(Connection conn, String searchTerm) {
        StringBuilder results = new StringBuilder();
        try {
            String sql = "SELECT id_song, title, artist, genre FROM Stream_songs WHERE LOWER(title) LIKE ? OR LOWER(artist) LIKE ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, "%" + searchTerm.toLowerCase() + "%");
                pstmt.setString(2, "%" + searchTerm.toLowerCase() + "%");
    
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.isBeforeFirst()) { // empty case
                        return "No results found.";
                    }
                    List<String> rows = new ArrayList<>();
                    while (rs.next()) {
                        int id = rs.getInt("id_song");
                        String title = rs.getString("title");
                        String artist = rs.getString("artist");
                        String genre = rs.getString("genre");
                    
                        rows.add(String.format("ID(%d) - %s - Artist:%s [Genre:%s]", id, title, artist, genre));
                    }
                    results.append(String.join(System.lineSeparator(), rows));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results.toString();
    }

    public String listAllSongs(Connection conn) {
        StringBuilder results = new StringBuilder();
        try {
            String sql = "SELECT id_song, title, artist, genre FROM Stream_songs";
            try (PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {
                if (!rs.isBeforeFirst()) { 
                    return "No songs available.";
                }
    
                List<String> rows = new ArrayList<>();
                while (rs.next()) {
                    int id = rs.getInt("id_song");
                    String title = rs.getString("title");
                    String artist = rs.getString("artist");
                    String genre = rs.getString("genre");

                    rows.add(String.format("ID(%d) - %s - Artist:%s [Genre:%s]", id, title, artist, genre));
                }
                results.append(String.join(System.lineSeparator(), rows));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results.toString();
    }

    public boolean checkIfSongExistsInDB(Connection conn, int songId) {
        try {
            String sql = "SELECT id_song FROM Stream_songs WHERE id_song = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, songId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    //?-----DISPLAY RESULT RECHERCHE
    public void displaySearchResults(PrintWriter writer, BufferedReader reader) throws IOException {    
        String response;
        boolean hasResults = false;
        while ((response = reader.readLine()) != null) {
            if (response.equals("END_RESULTS")) {
                break;
            }
            System.out.println(response);
            hasResults = true;
        }
    
        if (!hasResults) {
            System.out.println("=> No results found.");
        }
    }    

    //?-----BUILD PATH TO SONG
    public String findSongFilePath(Connection conn, int songId) {
        try {
            String sql = "SELECT title, extension FROM Stream_songs WHERE id_song = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, songId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String title = rs.getString("title");
                        String extension = rs.getString("extension");
                        return  musicFolder + title + extension;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Song not found
    }

    public String getCorrectSongTitle(Connection conn, String songName) {
        try {
            String sql = "SELECT title FROM Stream_songs WHERE LOWER(title) = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, songName.toLowerCase());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String title = rs.getString("title");
                        return title;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public int getSongId(Connection conn, String songName) { //a modifier dans le cas ou two songs have same tilte
        try {
            String sql = "SELECT id_song FROM Stream_songs WHERE LOWER(title) = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, songName.toLowerCase());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id_song");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    //*---------------------------CORE LOGIC---------------------------
    //?-----STREAMING (server side)
    public void sendStream(Connection conn, Socket audioSocket, int songId) {
        String songName = sh.getSongnameById(conn, songId);
        String filePath = findSongFilePath(conn, songId);
        //Si path n'existe pas
        if (filePath == null) {
            System.out.println("Song not found: " + songName);
            try {
                PrintWriter writer = new PrintWriter(audioSocket.getOutputStream(), true);
                writer.println("ERROR: File not found");
            } catch (IOException e) {
                System.err.println("Error sending error message: " + e.getMessage());
            }
            return;
        }
        
        //Algo byte by byte:
        Thread streamThread = new Thread(() -> {
            boolean finishedSending = true;
            try (FileInputStream fileInputStream = new FileInputStream(filePath);
                BufferedOutputStream outputStream = new BufferedOutputStream(audioSocket.getOutputStream())) {

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                }
            } catch (IOException e) {
                if (audioSocket.isClosed()) { // if the socket is closed mid-stream
                    finishedSending = false;
                    System.out.println("Audio socket closed by the client. Stopping stream");
                } else {
                    System.err.println("Error streaming the file: " + e.getMessage());
                }
                return;
            } finally {
                if (finishedSending) {
                    System.out.println("Finished sending data to the client: " + songName);
                } else {
                    System.out.println("Uncompleted sending data to the client: " + songName);
                }
            }
        });
        streamThread.start();
    }
    
    //?-----RECEIVE STREAM (client side)
    public void receiveStreamAndPlay(PrintWriter writer, InputStream inputStream) {
        try {
            System.out.println("Before playing, available bytes: " + inputStream.available());
            // InputStream nonClosingStream = new NonClosingInputStream(inputStream);
            pausablePlayer = new PausablePlayer(inputStream);
            pausablePlayer.play(); // Start playback
            
            System.out.println("After playback, available bytes: " + inputStream.available());
        } catch (JavaLayerException | IOException e) {
            System.err.println("Error during playback: " + e.getMessage());
        }
    }

    //?-----PLAYER MANIPULATION
    public void pausePlayback() {
        if (pausablePlayer != null) {
            pausablePlayer.pause();
        }
    }

    public void resumePlayback() {
        if (pausablePlayer != null) {
            pausablePlayer.resume();
        }
    }
    
    public void stopPlayback() {
        if (pausablePlayer != null) {
            pausablePlayer.stop();
        }
    }

    //*---------------------------PLAYLIST MANIPULATION---------------------------
    public String listPlaylists(HashMap<Integer, Playlist> allPlaylists) {
        StringBuilder results = new StringBuilder();
        int i = 0;
        for (Playlist play : allPlaylists.values()) {
            results.append(String.format("ID(%d) - %s", play.getPlaylistID(), play.getPlaylistName()));
            i++;
            if(i < allPlaylists.size()) {
                results.append(System.lineSeparator()); // Add newline
            }
        }
        return results.toString();
    }

    public String listAllSongsInPlaylist(Playlist playlist) {
        List<Song> q = playlist.getQueue();
        if (!q.isEmpty()) {
            StringBuilder results = new StringBuilder();
            int i = 0;
            for (Song song : q) {
                results.append(String.format("ID(%d) - %s - Pos:%d", song.getId_song(), song.getTitle(), i));
                i++;
                if (i < q.size()) {
                    results.append(System.lineSeparator());
                }
            }
            return results.toString();
        }
        return "No songs in that playlist yet";
    }


    //*---------------------------MP3AGIG---------------------------
    public int getSongLengthById(Connection conn, int songId) {
        String filePath = findSongFilePath(conn, songId); // Retrieve file path from the database
        if (filePath == null) {
            System.err.println("Error: File path for song ID " + songId + " not found.");
            return -1; // Return -1 to indicate an error
        }
        try {
            Mp3File mp3File = new Mp3File(filePath);
            return (int) mp3File.getLengthInSeconds();
        } catch (IOException | UnsupportedTagException | InvalidDataException e) {
            System.err.println("Error reading MP3 file for song ID " + songId + ": " + e.getMessage());
            return -1;
        }
    }

    public long getElapsedTimeMillis() {
        return pausablePlayer != null ? pausablePlayer.getElapsedTimeMillis() : 0;
    }
}
