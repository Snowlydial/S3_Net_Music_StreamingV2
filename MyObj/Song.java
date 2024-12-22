package MyObj;

public class Song {
    private int id_song;
    private String title;
    private String extension;
    private String artist;
    private String album;
    private String genre;
    private int yearOfRelease;

    public Song(int id_song, String title, String extension, String artist, String album, String genre, int yearOfRelease) {
        setId_song(id_song);
        setTitle(title);
        setExtension(extension);
        setArtist(artist);
        setAlbum(album);
        setGenre(genre);
        setYearOfRelease(yearOfRelease);
    }

    //SETTERS
    private void setId_song(int id_song) {
        this.id_song = id_song;
    }
    private void setTitle(String title) {
        this.title = title;
    }
    private void setExtension(String extension) {
        this.extension = extension;
    }
    private void setArtist(String artist) {
        this.artist = artist;
    }
    private void setAlbum(String album) {
        this.album = album;
    }
    private void setGenre(String genre) {
        this.genre = genre;
    }
    private void setYearOfRelease(int yearOfRelease) {
        this.yearOfRelease = yearOfRelease;
    }

    //GETTERS
    public String getAlbum() {
        return album;
    }
    public String getArtist() {
        return artist;
    }
    public String getExtension() {
        return extension;
    }
    public String getGenre() {
        return genre;
    }
    public int getId_song() {
        return id_song;
    }
    public String getTitle() {
        return title;
    }
    public int getYearOfRelease() {
        return yearOfRelease;
    }

}
