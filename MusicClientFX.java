import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.concurrent.TimeUnit;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.util.Map;

import Utility.ConfigUtil;
import Utility.MusicUtil;

public class MusicClientFX extends Application {

    private Socket commandSocket;
    private Socket audioSocket;
    private BufferedReader serverReader;
    private PrintWriter serverWriter;
    private ListView<String> playlistView;
    private ListView<HBox> songView;

    private static boolean isPlaying = false;
    private MusicUtil mp = new MusicUtil();
    private static Map<String, Object> confGetter = ConfigUtil.getConfig("config.json");
    private static String serverAddress = (String)confGetter.get("serverAddress");
    private static int port = (int)confGetter.get("port");
    private static int currentPlaylistId = 0;
    private static int currentSongIndex = 0;
    private static int songDuration = 0;
    private boolean autoNextEnabled = true; // Auto-next is on by default
    private boolean loopEnabled = false; // Loop is off by default

    private Label nowPlayingLabel = new Label("Now playing: ");
    private Button pauseButton = new Button("Pause");
    private Button resumeButton = new Button("Resume");
    private Button stopButton = new Button("Stop");
    private Button nextButton = new Button("Next");
    private Button previousButton = new Button("Previous");

    public static void main(String[] args) {
        launch(args);
    }

    @SuppressWarnings("unused")
    @Override
    public void start(Stage primaryStage) {
        connectToServer();

        BorderPane root = new BorderPane();
        VBox menuBox = new VBox(10);
        menuBox.setPadding(new Insets(10));

        Button viewPlaylistsButton = new Button("Refresh");
        Button searchButton = new Button("Search");
        Button playlistControlButton = new Button("Playlist Control");
        Button exitButton = new Button("Exit");
        Button toggleLoopButton = new Button("Loop: OFF");
        Button toggleAutoNextButton = new Button("AutoNext: ON");
        
        menuBox.getChildren().addAll(viewPlaylistsButton, searchButton, playlistControlButton, exitButton);
        
        HBox playbackProgressBar = setupPlaybackProgressBar();
        VBox topContainer = new VBox(menuBox, playbackProgressBar); // Combine menu and progress bar
        root.setTop(topContainer);
        
        playlistView = new ListView<>();
        songView = new ListView<>();
        VBox playbackControls = new VBox(10, nowPlayingLabel, pauseButton, resumeButton, stopButton, nextButton, previousButton, toggleLoopButton, toggleAutoNextButton);
        playbackControls.setPadding(new Insets(10));

        root.setLeft(playlistView);
        root.setCenter(songView);
        root.setRight(playbackControls);

        //-------Button handlers assign to functions
        viewPlaylistsButton.setOnAction(e -> fetchAndDisplayPlaylists());
        searchButton.setOnAction(e -> showSearchDialog());
        playlistControlButton.setOnAction(e -> showPlaylistControlDialog());
        exitButton.setOnAction(e -> exitApplication());
        
        toggleLoopButton.setOnAction(e -> {
            loopEnabled = !loopEnabled;
            toggleLoopButton.setText(loopEnabled ? "Loop: ON" : "Loop: OFF");
        });
        
        toggleAutoNextButton.setOnAction(e -> {
            autoNextEnabled = !autoNextEnabled;
            toggleAutoNextButton.setText(autoNextEnabled ? "AutoNext: ON" : "AutoNext: OFF");
        });

        playlistView.setOnMouseClicked(event -> {
            String selectedPlaylist = playlistView.getSelectionModel().getSelectedItem();            
            if (selectedPlaylist != null && !selectedPlaylist.isBlank()) {
                if (selectedPlaylist.startsWith("CURRENT_PLAYLIST:")) selectedPlaylist = null;
                if (selectedPlaylist != null) {
                    int playlistId = Integer.parseInt(selectedPlaylist.substring(selectedPlaylist.indexOf('(') + 1, selectedPlaylist.indexOf(')')));
                    currentPlaylistId = playlistId;
                    if (event.getClickCount() == 2) {
                        serverWriter.println("CHANGE_PLAYLIST:" + currentPlaylistId);
                        serverWriter.flush();
                        try {
                            String resp = serverReader.readLine();
                            System.out.println(resp);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        fetchAndDisplayPlaylists();
                        fetchAndDisplaySongsInPlaylist(playlistId);
                    } else if (event.getClickCount() == 1) {
                        fetchAndDisplaySongsInPlaylist(playlistId);
                    }
                }
            }
        });
        
        songView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                HBox selectedItem = songView.getSelectionModel().getSelectedItem();
                if (selectedItem != null && !((Label)selectedItem.getChildren().get(0)).getText().startsWith("ERROR:")) {
                    Label songLabel = (Label) selectedItem.getChildren().get(0);
                    String selectedSong = songLabel.getText();
                    if (selectedSong != null) {
                        int songId = Integer.parseInt(selectedSong.substring(selectedSong.indexOf('(') + 1, selectedSong.indexOf(')')));
                        currentSongIndex = songId;
                        streamSong(songId);
                    }
                }
            }
        });

        pauseButton.setOnAction(e -> {
            System.out.println("Playback paused.");
            mp.pausePlayback();
        });

        resumeButton.setOnAction(e -> {
            System.out.println("Playback resumed.");
            mp.resumePlayback();
        });

        stopButton.setOnAction(e -> {
            Platform.runLater(() -> nowPlayingLabel.setText("Now playing: "));
            System.out.println("Playback stopped.");
            stopPlayback(audioSocket);
        });

        nextButton.setOnAction(e -> nextBlock());

        previousButton.setOnAction(e -> {
            checkIfPlaying(audioSocket);
            audioSocket = reconnectAudioSocket(audioSocket, serverAddress, port, serverWriter, serverReader);
            serverWriter.println("PREVIOUS");
            new Thread(() -> {
                try {
                    String response = serverReader.readLine();
                    if ("QUEUE_EMPTY".equals(response)) {
                        showError("Queue is empty, previous song not available.");
                    } else if (response.startsWith("STREAM_OK:")) {
                        int newSongId = Integer.parseInt(response.substring(10).trim());
                        currentSongIndex = newSongId;
                        Platform.runLater(() -> nowPlayingLabel.setText("Now playing song ID: " + newSongId));
                        songDuration = requestSongDuration(newSongId);
                        mp.receiveStreamAndPlay(serverWriter, audioSocket.getInputStream());
                        isPlaying = true;
                    }
                } catch (IOException ex) {
                    showError("Error during previous song operation.");
                }
            }).start();
        });

        Scene scene = new Scene(root, 900, 500);
        primaryStage.setTitle("Music Streaming Client");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void connectToServer() {
        try {
            commandSocket = new Socket(serverAddress, port);
            audioSocket = new Socket(serverAddress, port+1);
            serverReader = new BufferedReader(new InputStreamReader(commandSocket.getInputStream()));
            serverWriter = new PrintWriter(commandSocket.getOutputStream(), true);
        } catch (IOException e) {
            showError("Failed to connect to server.");
        }
    }

    private void fetchAndDisplayPlaylists() {
        serverWriter.println("LIST_PLAYLISTS");
        try {
            String response;
            Platform.runLater(() -> playlistView.getItems().clear());
                while ((response = serverReader.readLine()) != null) {
                    if (response.equals("END_RESULTS")) break;
                    String finalResponse = response;
                    Platform.runLater(() -> playlistView.getItems().add(finalResponse));
                }
        } catch (IOException e) {
            showError("Error fetching playlists.");
        }
    }
    
    private void fetchAndDisplaySongsInPlaylist(int playlistId) {
        serverWriter.println("LIST_PLSONGS:" + playlistId);
        try {
            String response;
            Platform.runLater(() -> songView.getItems().clear());
                while ((response = serverReader.readLine()) != null) {
                    if (response.equals("END_RESULTS")) break;
                    String finalResponse = response;
                    if (!finalResponse.isBlank()) {
                        if(finalResponse.startsWith("ERROR:")) {
                            Platform.runLater(() -> {
                                HBox songItem = new HBox(10, new Label(finalResponse));
                                songView.getItems().add(songItem);
                            });
                        } else {
                            Platform.runLater(() -> {
                                MenuButton optionsButton = createSongOptionsButton(finalResponse);
                                HBox songItem = new HBox(10, new Label(finalResponse), optionsButton);
                                songView.getItems().add(songItem);
                            });
                        }
                    }
                }
        } catch (IOException e) {
            showError("Error fetching songs in playlist.");
        }
    }
    
    
    //*----------ADD/REMOVE SONG FROM PLAYLIST
    @SuppressWarnings("unused")
    private MenuButton createSongOptionsButton(String songDetails) {
        MenuButton menuButton = new MenuButton("⋮");
    
        if (!songDetails.contains("ID(") || !songDetails.contains(")")) {
            System.out.println("Debug: songDetails = " + songDetails);
            showError("Invalid song format: " + songDetails);
            return menuButton; // Return an empty button for invalid data
        }
    
        MenuItem removeItem = new MenuItem("Remove from Playlist");
        MenuItem addToPlaylistItem = new MenuItem("Add to Another Playlist");
        
        int songId = Integer.parseInt(songDetails.substring(songDetails.indexOf('(') + 1, songDetails.indexOf(')')));
        
        //?----Removing a song
        removeItem.setOnAction(e -> {
            if (currentPlaylistId == 0) {
                showError("Cannot remove a song from the default playlist.");
            } else {
                serverWriter.println("DEL_SONG_IN_PLAYLIST:" + currentPlaylistId + "-" + songId);
                new Thread(() -> {
                    try {
                        String response = serverReader.readLine();
                        Platform.runLater(() -> showInformation("Remove Song", response));
                        fetchAndDisplaySongsInPlaylist(currentPlaylistId); // Refresh
                    } catch (IOException ex) {
                        showError("Error during remove song operation.");
                    }
                }).start();
            }
        });
    
        //?----Adding a song
        addToPlaylistItem.setOnAction(e -> showPlaylistSelectionPopup(songId));
    
        menuButton.getItems().addAll(removeItem, addToPlaylistItem);
        return menuButton;
    }
    
    //?----------The ADDING itself
    private void fetchAndDisplayPlaylistsInView(ListView<String> playlistListView) {
        serverWriter.println("LIST_PLAYLISTS");
        new Thread(() -> {
            try {
                String response;
                Platform.runLater(() -> playlistListView.getItems().clear());
                while ((response = serverReader.readLine()) != null) {
                    if (response.equals("END_RESULTS")) break;
                    String finalResponse = response;
                    Platform.runLater(() -> playlistListView.getItems().add(finalResponse));
                }
            } catch (IOException e) {
                showError("Error fetching playlists.");
            }
        }).start();
    }
    
    @SuppressWarnings("unused")
    private void showPlaylistSelectionPopup(int songId) {
        Stage playlistPopup = new Stage();
        ListView<String> playlistListView = new ListView<>();
        Button closeButton = new Button("Close");
    
        closeButton.setOnAction(e -> playlistPopup.close());
    
        fetchAndDisplayPlaylistsInView(playlistListView);
    
        playlistListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selectedPlaylist = playlistListView.getSelectionModel().getSelectedItem();
                if (selectedPlaylist != null) {
                    int playlistId = Integer.parseInt(selectedPlaylist.substring(selectedPlaylist.indexOf('(') + 1, selectedPlaylist.indexOf(')')));
                    addSongToPlaylist(songId, playlistId);
                    playlistPopup.close();
                }
            }
        });
    
        VBox popupLayout = new VBox(10, new Label("Select a Playlist:"), playlistListView, closeButton);
        popupLayout.setPadding(new Insets(10));
        Scene popupScene = new Scene(popupLayout, 300, 400);
        playlistPopup.setTitle("Add Song to Playlist");
        playlistPopup.setScene(popupScene);
        playlistPopup.show();
    }

    private void addSongToPlaylist(int songId, int playlistId) {
        serverWriter.println("ADD_SONG_IN_PLAYLIST:" + playlistId + "-" + songId);
        new Thread(() -> {
            try {
                String response = serverReader.readLine();
                Platform.runLater(() -> showInformation("Add Song", response));
                fetchAndDisplaySongsInPlaylist(playlistId); // Refresh
            } catch (IOException e) {
                showError("Error during add song operation.");
            }
        }).start();
    }
    
    //?----------STREAM
    private void streamSong(int songId) {
        checkIfPlaying(audioSocket);
        if (audioSocket.isClosed()) {
            audioSocket = reconnectAudioSocket(audioSocket, serverAddress, port, serverWriter, serverReader);
        }

        songDuration = requestSongDuration(songId);

        serverWriter.println("STREAM:" + songId);
        serverWriter.flush();
        try {
            String response = serverReader.readLine();
            if ("STREAM_OK".equals(response)) {
                Platform.runLater(() -> nowPlayingLabel.setText("Now playing song ID: " + songId));
                mp.receiveStreamAndPlay(serverWriter, audioSocket.getInputStream());
                isPlaying = true;
            } else if (response.startsWith("ERROR:")) {
                showError(response);
            } else {
                showError("Unexpected response: " + response);
            }
        } catch (IOException e) {
            showError("Error during streaming.");
        }
    }

    //?----------SEARCH
    private void showSearchDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Search Music");
        dialog.setHeaderText("Enter song or artist to search (type 'listAll' to see everything):");
        dialog.setContentText("Search:");

        dialog.showAndWait().ifPresent(searchTerm -> {
            if (searchTerm.equalsIgnoreCase("listAll")) {
                serverWriter.println("SEARCH:listAll");
            } else {
                serverWriter.println("SEARCH:" + searchTerm);
            }
            displaySearchResults();
        });
    }

    private void displaySearchResults() {
        new Thread(() -> {
            try {
                ListView<HBox> resultsListView = new ListView<>();
                Platform.runLater(() -> {
                    Stage searchResultsStage = new Stage();
                    searchResultsStage.setTitle("Search Results");
                    resultsListView.setPrefSize(400, 300);

                    VBox vbox = new VBox(10, resultsListView);
                    vbox.setPadding(new Insets(10));

                    Scene scene = new Scene(vbox);
                    searchResultsStage.setScene(scene);
                    searchResultsStage.show();
                    
                });

                String response;
                while ((response = serverReader.readLine()) != null) {
                    if (response.equals("END_RESULTS")) break;
                    String finalResponse = response;

                    Platform.runLater(() -> {
                        MenuButton optionsButton = createSongOptionsButton(finalResponse);
                        HBox songItem = new HBox(10, new Label(finalResponse), optionsButton);
                        resultsListView.getItems().add(songItem);
                    });
                }
            } catch (IOException e) {
                showError("Error fetching search results.");
            }
        }).start();
    }


    //*----------PLAYLIST CONTROL
    @SuppressWarnings("unused")
    private void showPlaylistControlDialog() {
        Stage controlStage = new Stage();
        VBox controlRoot = new VBox(10);
        controlRoot.setPadding(new Insets(10));

        Button createPlaylistButton = new Button("Create Playlist");
        Button deletePlaylistButton = new Button("Delete Playlist");
        Button reorderSongsButton = new Button("Reorder Songs");

        controlRoot.getChildren().addAll(createPlaylistButton, deletePlaylistButton, reorderSongsButton);

        createPlaylistButton.setOnAction(e -> createPlaylist());
        deletePlaylistButton.setOnAction(e -> deletePlaylist());
        reorderSongsButton.setOnAction(e -> reorderSongs());

        Scene controlScene = new Scene(controlRoot, 400, 300);
        controlStage.setTitle("Playlist Control");
        controlStage.setScene(controlScene);
        controlStage.show();
    }

    private void createPlaylist() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Playlist");
        dialog.setHeaderText("Enter the name of the new playlist:");
        dialog.setContentText("Playlist Name:");

        dialog.showAndWait().ifPresent(playlistName -> {
            serverWriter.println("CREATE_PLAY:" + playlistName);
            try {
                String response = serverReader.readLine();
                if (response.startsWith("PLAYLIST_CREATED")) {
                    showInformation("Success", "Playlist '" + playlistName + "' created successfully.");
                    fetchAndDisplayPlaylists();
                } else {
                    showError("Failed to create playlist.");
                }
            } catch (IOException e) {
                showError("Error during playlist creation.");
            }
        });
    }

    @SuppressWarnings("unused")
    private void deletePlaylist() {
        Stage playlistPopup = new Stage();
        ListView<String> playlistListView = new ListView<>();
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> playlistPopup.close());
        fetchAndDisplayPlaylistsInView(playlistListView);
    
        playlistListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selectedPlaylist = playlistListView.getSelectionModel().getSelectedItem();
                if (selectedPlaylist != null) {
                    int playlistId = Integer.parseInt(selectedPlaylist.substring(selectedPlaylist.indexOf('(') + 1, selectedPlaylist.indexOf(')')));
                    if (playlistId == 0) {
                        showError("Initial playlist cannot be deleted.");
                    } else {
                        serverWriter.println("DELETE_PLAY:" + playlistId);
                        String response;
                        try {
                            response = serverReader.readLine();
                            if (response.startsWith("PLAYLIST_DELETED")) {
                                showInformation("Success", "Playlist deleted successfully.");
                                fetchAndDisplayPlaylists();
                            } else if (response.startsWith("ERROR:1")) {
                                showError("Error: Playlist ID (" + playlistId + ") doesn't exist.");
                            } else {
                                showError("Failed to delete playlist.");
                            }
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                    playlistPopup.close();
                }
            }
        });
    
        VBox popupLayout = new VBox(10, new Label("Select a Playlist:"), playlistListView, closeButton);
        popupLayout.setPadding(new Insets(10));
        Scene popupScene = new Scene(popupLayout, 300, 400);
        playlistPopup.setTitle("Delete a playlist");
        playlistPopup.setScene(popupScene);
        playlistPopup.show();
    }

    private void reorderSongs() {
        fetchAndDisplayPlaylists();
        TextInputDialog playlistDialog = new TextInputDialog();
        playlistDialog.setTitle("Reorder Songs");
        playlistDialog.setHeaderText("Enter the ID of the playlist to reorder songs:");
        playlistDialog.setContentText("Playlist ID:");

        playlistDialog.showAndWait().ifPresent(playlistId -> {
            try {
                int id = Integer.parseInt(playlistId);
                fetchAndDisplaySongsInPlaylist(id);
                TextInputDialog songDialog = new TextInputDialog();
                songDialog.setTitle("Reorder Songs");
                songDialog.setHeaderText("Enter the ID of the song to reorder:");
                songDialog.setContentText("Song ID:");

                songDialog.showAndWait().ifPresent(songId -> {
                    TextInputDialog positionDialog = new TextInputDialog();
                    positionDialog.setTitle("Reorder Songs");
                    positionDialog.setHeaderText("Enter the new position of the song:");
                    positionDialog.setContentText("New Position:");

                    positionDialog.showAndWait().ifPresent(position -> {
                        try {
                            int newPosition = Integer.parseInt(position);
                            serverWriter.println("REORDER:" + id + "-" + songId + "-" + newPosition);
                            String response = serverReader.readLine();
                            showInformation("Reorder Songs", response);
                            fetchAndDisplaySongsInPlaylist(id); // Refresh
                        } catch (NumberFormatException | IOException e) {
                            showError("Invalid input or error during reorder operation.");
                        }
                    });
                });
            } catch (NumberFormatException e) {
                showError("Invalid playlist ID.");
            }
        });
    }

    //*----------UTILITY
    private void stopPlayback(Socket audioSocket) {
        try {
            mp.stopPlayback();
            audioSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkIfPlaying(Socket audioSocket) {
        if (isPlaying) {
            stopPlayback(audioSocket);
            isPlaying = false;
        }
    }

    private Socket reconnectAudioSocket(Socket audioSocket, String serverAddress, int port, PrintWriter writer, BufferedReader reader) {
        try {
            audioSocket = new Socket(serverAddress, port + 1);
            writer.println("RECONNECT_AUDIO");
            String response = reader.readLine();
            if ("AUDIO_RECONNECTED".equals(response)) {
                // System.out.println("Audio socket reconnected.");
            } else {
                showError("Failed to reconnect audio socket.");
            }
        } catch (IOException e) {
            showError("Error reconnecting audio socket: " + e.getMessage());
        }
        return audioSocket;
    }

    private int requestSongDuration(int songId) {
        serverWriter.println("SONG_DURATION:" + songId);
        try {
            songDuration = Integer.parseInt(serverReader.readLine());
            System.out.println("Song duration: " + songDuration + "seconds");
            return songDuration;
        } catch (NumberFormatException e) {
            showError("Error parsing song duration.");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showInformation(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void exitApplication() {
        serverWriter.println("EXIT");
        try {
            checkIfPlaying(audioSocket);
            commandSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing sockets.");
        }
        Platform.exit();
    }

    //*----------PROGRESS BAR and auto-play
    private HBox setupPlaybackProgressBar() {
        HBox playbackInfoBox = new HBox(10);
        playbackInfoBox.setPadding(new Insets(10));

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);

        Label timeLabel = new Label("00:00 / 00:00");

        playbackInfoBox.getChildren().addAll(progressBar, timeLabel);

        @SuppressWarnings("unused")
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            if (isPlaying && songDuration > 0) {
                long elapsedMillis = mp.getElapsedTimeMillis();
                double progress = (double) elapsedMillis / (songDuration * 1000);

                Platform.runLater(() -> {
                    progressBar.setProgress(progress);
                    timeLabel.setText(formatTime(elapsedMillis / 1000) + " / " + formatTime(songDuration));
                });

                // System.out.println(elapsedMillis);
                if (elapsedMillis >= (songDuration * 1000)-1000 && autoNextEnabled) {
                    if (loopEnabled || isLastSong() == 1) { // Auto-next logic
                        nextBlock();
                    } else if (isLastSong() == 0) {
                        Platform.runLater(() -> showInformation("Playback Finished", "No more songs in the playlist."));
                    }
                }
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        return playbackInfoBox;
    }

    private String formatTime(long seconds) {
        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        long remainingSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remainingSeconds);
    }
    
    private int isLastSong() {
        serverWriter.println("LAST_POSITION:"+currentPlaylistId + "-" + currentSongIndex);
        String response;
        try {
            response = serverReader.readLine();
            if (response.equals("TRUE")) {
                return 0;
            } else if (response.equals("FALSE")) {
                return 1;
            } else {
                showError(response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private void nextBlock() {
        checkIfPlaying(audioSocket);
        audioSocket = reconnectAudioSocket(audioSocket, serverAddress, port, serverWriter, serverReader);
        serverWriter.println("NEXT");
        new Thread(() -> {
            try {
                String response = serverReader.readLine();
                if ("QUEUE_EMPTY".equals(response)) {
                    showError("Queue is empty, next song not available.");
                } else if (response.startsWith("STREAM_OK:")) {
                    int newSongId = Integer.parseInt(response.substring(10).trim());
                    currentSongIndex = newSongId;
                    Platform.runLater(() -> nowPlayingLabel.setText("Now playing song ID: " + newSongId));
                    songDuration = requestSongDuration(newSongId);
                    mp.receiveStreamAndPlay(serverWriter, audioSocket.getInputStream());
                    isPlaying = true;
                }
            } catch (IOException ex) {
                showError("Error during next song operation.");
            }
        }).start();
    }
}