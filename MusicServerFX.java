import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.io.*;
import java.net.*;

import Utility.*;
import MyObj.*;

public class MusicServerFX {
    private static Map<String, Object> confGetter = ConfigUtil.getConfig("config.json");
    private static HashMap<Integer, Playlist> allPlaylists = new HashMap<>();
    private static MusicUtil mp = new MusicUtil();
    private static PlaylistHandler plh = new PlaylistHandler();
    private static SongHandler sh = new SongHandler();
    private static int port = (int)confGetter.get("port");
    
    public static void main(String[] args) throws SQLException {
        
        try (ServerSocket serverSocket = new ServerSocket(port);
             ServerSocket serverAudioSocket = new ServerSocket(port + 1)) {
             
            System.out.println("Server is running on port " + port);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected for commands.");
    
                Socket audioSocket = serverAudioSocket.accept();
                System.out.println("Client connected for audio.");
                
                Thread clientThread = new Thread(() -> {
                    Connection conn = ConnectJDBC.startConnection();
                    handleClient(clientSocket, audioSocket, conn, serverAudioSocket);
                });
                
                clientThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    private static void handleClient(Socket clientSocket, Socket audioSocket, Connection conn, ServerSocket serverAudioSocket) {
        boolean loop = false;
        HashMap<Integer, Song> allSongs = sh.allAvailableSongsOnTheDB(conn);
        int playlistIdCount = 0;
        if(plh.hasDataInPlaylistSongs(conn)) {
            allPlaylists = plh.retrievePlaylistsFromDB(conn, allSongs);
            playlistIdCount = plh.getMaxPlaylistID(conn);
        } else {
            Playlist All_Songs = new Playlist(playlistIdCount,"All_Songs");
            allPlaylists.put(0, All_Songs);
        }

        int currentPlaylistIndex = 0; //use 0 == file de lecture initiale (contains all song)
        Playlist currentPlaylist = allPlaylists.get(currentPlaylistIndex);
        int currentSongIndex = currentPlaylist.getSongQueueSize();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); 
              PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
            String clientRequest;
            
            while ((clientRequest = reader.readLine()) != null) {
                System.out.println("Received client request: " + clientRequest);
    
                if (clientRequest.startsWith("SEARCH:")) {
                    String searchTerm = clientRequest.substring(7).trim();
                    System.out.println(searchTerm);
                    String searchResults = "";
                    if(searchTerm.compareTo("listAll") == 0) {
                        searchResults = mp.listAllSongs(conn);
                    } else {
                        searchResults = mp.searchMusic(conn, searchTerm);
                    }
                    writer.println(searchResults);
                    writer.println("END_RESULTS");
                    System.out.println("Search results sent for: " + searchTerm);
    
                } else if (clientRequest.startsWith("STREAM:")) {
                    int songId = Integer.parseInt(clientRequest.substring(7).trim());
                    if(!mp.checkIfSongExistsInDB(conn, songId)) {
                        writer.println("STREAM_NOT");
                    } else {
                        String songName = sh.getSongnameById(conn, songId);
                        System.out.println("STREAM request received for: " + songName);
        
                        if (mp.findSongFilePath(conn, songId) == null) { // if no song file for that name is found
                            writer.println("ERROR: Song not found");
                            System.out.println("Song not found: " + songName);
                        } else {
                            Song correctSong = sh.findSongById(allSongs, songId);
                            
                            if(!currentPlaylist.containsSong(correctSong) && currentPlaylistIndex == 0) {
                                //?-----new songs always added at the end
                                plh.addSongToExistingPlaylist(conn, allPlaylists, currentPlaylistIndex, correctSong); // Add song to playlist on DB
                                currentSongIndex = currentPlaylist.getSongQueueSize()-1;
                            } else if(currentPlaylist.containsSong(correctSong)) {
                                String correctSongTitle = mp.getCorrectSongTitle(conn, songName);
                                System.out.println("Song '" + correctSongTitle + "' already in currentPlaylist. Playing it from its current position.");
                                currentSongIndex = currentPlaylist.getSongIndexInPlaylist(correctSong);
                            }
                            
                            writer.println("STREAM_OK");
                            mp.sendStream(conn, audioSocket, songId);
                        }
                    } 
                } else if (clientRequest.startsWith("SONG_DURATION:")){
                    int songId = Integer.parseInt(clientRequest.substring(14).trim());
                    int songDuration = mp.getSongLengthById(conn, songId);
                    writer.println(songDuration);
                } else if (clientRequest.startsWith("RECONNECT_AUDIO")) {
                    // System.out.println("Reconnecting audio socket...");
                    audioSocket = serverAudioSocket.accept(); // Re-accept a new audioSocket connection
                    // System.out.println("Audio socket reconnected.");
                    writer.println("AUDIO_RECONNECTED");
    
                } else if (clientRequest.equals("NEXT")) {
                    boolean isEmptyQueue = currentPlaylist.isSongQueueEmpty();
                    if(!isEmptyQueue) {
                        // System.out.println("CURRENT SONG INDEX (in next) = " + currentSongIndex);
                        Song nextSong = currentPlaylist.nextSong(currentSongIndex);
                        currentSongIndex = currentPlaylist.getSongIndexInPlaylist(nextSong);
                        
                        System.out.println("Next song = " + nextSong.getTitle());
                        if(audioSocket.isClosed()) {
                            writer.println("CLOSED");
                        } else {
                            writer.println("STREAM_OK:" + nextSong.getId_song());
                            mp.sendStream(conn, audioSocket, nextSong.getId_song());
                        }
                    } else {
                        writer.println("QUEUE_EMPTY");
                    }
                
                } else if (clientRequest.equals("PREVIOUS")) {
                    boolean isEmptyQueue = currentPlaylist.isSongQueueEmpty();
                    if(!isEmptyQueue) {
                        // System.out.println("CURRENT SONG INDEX (in prev) = " + currentSongIndex);
                        Song previousSong = currentPlaylist.previousSong(currentSongIndex);
                        currentSongIndex = currentPlaylist.getSongIndexInPlaylist(previousSong);
                        
                        System.out.println("Previous song = " + previousSong.getTitle());
                        if(audioSocket.isClosed()) {
                            writer.println("CLOSED");
                        } else {
                            writer.println("STREAM_OK:" + previousSong.getId_song());
                            mp.sendStream(conn, audioSocket, previousSong.getId_song());
                        }
                    } else {
                        writer.println("QUEUE_EMPTY");
                    }
                
                } else if (clientRequest.equals("LOOP")) { //doesn't work yet, need to implement auto play first
                    if(loop) {
                        loop = false;
                        writer.println("LOOP_OFF");
                    } else {
                        loop = true;
                        writer.println("LOOP_ON");
                    }

                } else if (clientRequest.equals("EXIT")) {
                    System.out.println("Client disconnected");
                    break;
                } else if (clientRequest.equals("LIST_PLAYLISTS")) {
                    // Send the current playlist's ID and name
                    plh.sendCurrentPlaylist(allPlaylists, currentPlaylistIndex, writer);
                    // Send the list of all playlists
                    plh.sendListOfPlaylist(allPlaylists, writer);
                } else if (clientRequest.startsWith("LIST_PLSONGS:")) {
                    int playlistId = Integer.parseInt(clientRequest.substring(13).trim());
                    if(allPlaylists.containsKey(playlistId)) {
                        Playlist selectedPlaylist = allPlaylists.get(playlistId);
                        if (selectedPlaylist != null) {
                            plh.sendListOfPlaylistSongs(selectedPlaylist, writer);
                        } else {
                            writer.println("ERROR: playlist does not exist.");
                            writer.println("END_RESULTS");
                        }
                    } else {
                        writer.println("ERROR_INDEX");
                    }
                
                } else if (clientRequest.startsWith("CREATE_PLAY:")) {
                    String playlistName = clientRequest.substring(12).trim();
                    playlistIdCount += 1;
                    plh.createNewPlaylist(allPlaylists, playlistName, playlistIdCount);
                    writer.println("PLAYLIST_CREATED");
                } else if (clientRequest.startsWith("DELETE_PLAY:")) {
                    int playlistIdToDelete = Integer.parseInt(clientRequest.substring(12).trim());
                    if (playlistIdToDelete < 0 || playlistIdToDelete >= allPlaylists.size()) {
                        writer.println("ERROR:1");
                    } else {
                        plh.deletePlaylistWithDataOnDB(conn, allPlaylists, playlistIdToDelete);
                        writer.println("PLAYLIST_DELETED");
                    }
                } else if (clientRequest.startsWith("ADD_SONG_IN_PLAYLIST:")) {
                    String[] parts = clientRequest.substring(21).split("-");
                    int playlistId = Integer.parseInt(parts[0].trim());
                    int songId = Integer.parseInt(parts[1].trim());
                    
                    if(sh.getSongnameById(conn, songId) != null) {
                        if (allPlaylists.containsKey(playlistId)) {
                            Playlist selectedPlaylist = allPlaylists.get(playlistId);
                            if (selectedPlaylist != null) {
                                Song songToAdd = sh.findSongById(allSongs, songId);

                                if(songToAdd != null && !selectedPlaylist.containsSong(songToAdd)) {
                                    plh.addSongToExistingPlaylist(conn, allPlaylists, playlistId, songToAdd);
                                    writer.println("Song successfully added to playlist.");
                                } else if(selectedPlaylist.containsSong(songToAdd)) {
                                    writer.println("EXCEPTION: Song already in playlist.");
                                } else {
                                    writer.println("ERROR: Song not found.");
                                }
                                
                            } else {
                                writer.println("ERROR: Playlist not found.");
                            }
                        } else {
                            writer.println("ERROR: Invalid playlist ID.");
                        }
                    } else {
                        writer.println("ERROR: Invalid song ID");
                    }
                    
                } else if (clientRequest.startsWith("DEL_SONG_IN_PLAYLIST:")) {
                    String[] parts = clientRequest.substring(21).split("-");
                    int playlistId = Integer.parseInt(parts[0].trim());
                    int songId = Integer.parseInt(parts[1].trim());
                    
                    if (sh.getSongnameById(conn, songId) != null) {
                        if (allPlaylists.containsKey(playlistId)) {
                            Playlist selectedPlaylist = allPlaylists.get(playlistId);
                            if (selectedPlaylist != null) {
                                Song songToRemove = sh.findSongById(allSongs, songId);
                                if (songToRemove != null && selectedPlaylist.containsSong(songToRemove)) {
                                    plh.removeSongFromPlaylist(conn, allPlaylists, playlistId, songToRemove);
                                    writer.println("Song successfully removed from playlist.");
                                } else {
                                    writer.println("EXCEPTION: Song not found in playlist.");
                                }
                            } else {
                                writer.println("ERROR: Playlist not found.");
                            }
                        } else {
                            writer.println("ERROR: Invalid playlist ID.");
                        }
                    } else {
                        writer.println("ERROR: Invalid song ID.");
                    }

                } else if (clientRequest.startsWith("REORDER:")) {
                    String[] parts = clientRequest.substring(8).split("-");
                    int playlistId = Integer.parseInt(parts[0].trim());
                    int songId = Integer.parseInt(parts[1].trim());
                    int newPosition = Integer.parseInt(parts[2].trim());
                
                    if (allPlaylists.containsKey(playlistId)) {
                        Playlist selectedPlaylist = allPlaylists.get(playlistId);
                        if (selectedPlaylist != null) {
                            Song songToMove = sh.findSongById(allSongs, songId);
                            if (songToMove != null && selectedPlaylist.containsSong(songToMove)) {                
                                plh.moveSongInPlaylistAndSyncOrder(conn, selectedPlaylist, playlistId, songToMove, newPosition);
                                writer.println("SUCCESS: Song reordered successfully.");
                            } else {
                                writer.println("ERROR: Song not found in playlist.");
                            }
                        } else {
                            writer.println("ERROR: Playlist not found.");
                        }
                    } else {
                        writer.println("ERROR: Invalid playlist ID.");
                    }

                } else if (clientRequest.startsWith("CHANGE_PLAYLIST:")) {
                    int playlistId = Integer.parseInt(clientRequest.substring(16).trim());
                    if (allPlaylists.containsKey(playlistId)) {
                        currentSongIndex = -1;
                        currentPlaylistIndex = playlistId;
                        currentPlaylist = allPlaylists.get(currentPlaylistIndex);
                        writer.println("CHANGE_OK:" + currentPlaylist.getPlaylistName());
                    } else {
                        writer.println("CHANGE_ERROR: Playlist not found.");
                    }
                } else if (clientRequest.startsWith("LAST_POSITION")) {
                    String[] parts = clientRequest.substring(14).split("-");
                    int playlistId = Integer.parseInt(parts[0].trim());
                    int songIndex = Integer.parseInt(parts[1].trim());  
                    if (allPlaylists.containsKey(playlistId)) {
                        Playlist selectedPlaylist = allPlaylists.get(playlistId);
                        if (selectedPlaylist != null) {
                            int songPos = selectedPlaylist.getSongIndexInPlaylist(allSongs.get(songIndex));
                            if(songPos == selectedPlaylist.getSongQueueSize() - 1) {
                                writer.println("TRUE");
                            } else {
                                writer.println("FALSE");
                            }
                        }
                    } else {
                        writer.println("ERROR: Invalid playlist ID.");
                    }

                } else {
                    writer.println("ERROR: Unknown request");
                }
            }
        } catch (IOException e) {      
            System.err.println("Error handling client: " + e.getMessage());
            // e.printStackTrace(); // Juste pour debug
        }
    }

}