 DROP TABLE STREAM_SONGS;
 DROP SEQUENCE Stream_songs_SEQ;

 DROP TABLE Stream_playlists_songs;
 DROP SEQUENCE Stream_playlists_songs_SEQ;

Create table Stream_songs(
    id_song NUMBER PRIMARY KEY,
    title VARCHAR2(255),
    extension VARCHAR2(10),
    artist VARCHAR2(255),
    album VARCHAR2(255),
    genre VARCHAR2(100),
    yearOfRelease number(4)
);

CREATE SEQUENCE Stream_songs_SEQ INCREMENT BY 1 MINVALUE 0 NOCYCLE NOCACHE;

-- INIT LES SONGS de BASE
INSERT INTO Stream_songs (id_song, title, extension, artist, album, genre, yearOfRelease) VALUES (Stream_songs_SEQ.nextval, 'CapSule', '.mp3', 'Hololive', 'None', 'J-Trap', 2022);
INSERT INTO Stream_songs (id_song, title, extension, artist, album, genre, yearOfRelease) VALUES (Stream_songs_SEQ.nextval, 'Biri-Biri', '.mp3', 'YOASOBI', 'None', 'J-Pop', 2024);
INSERT INTO Stream_songs (id_song, title, extension, artist, album, genre, yearOfRelease) VALUES (Stream_songs_SEQ.nextval, 'Another Episode', '.mp3', 'Yuki Kajiura', 'None', 'Orchestral', 2011);
INSERT INTO Stream_songs (id_song, title, extension, artist, album, genre, yearOfRelease) VALUES (Stream_songs_SEQ.nextval, 'Done for Me', '.mp3', 'Charlie Puth', 'None', 'Pop', 2018);
INSERT INTO Stream_songs (id_song, title, extension, artist, album, genre, yearOfRelease) VALUES (Stream_songs_SEQ.nextval, 'The Monster', '.mp3', 'Rihanna', 'None', 'Pop', 2013);
INSERT INTO Stream_songs (id_song, title, extension, artist, album, genre, yearOfRelease) VALUES (Stream_songs_SEQ.nextval, 'The Way', '.mp3', 'Ariana Grande', 'None', 'Pop', 2013);
INSERT INTO Stream_songs (id_song, title, extension, artist, album, genre, yearOfRelease) VALUES (Stream_songs_SEQ.nextval, 'Water Color', '.mp3', 'Wheein', 'None', 'K-Funk', 2021);
INSERT INTO Stream_songs (id_song, title, extension, artist, album, genre, yearOfRelease) VALUES (Stream_songs_SEQ.nextval, 'Watashi wa Ame', '.mp3', 'Niigo', 'None', 'J-Pop', 2023);

Create table Stream_playlists_songs(
	id_playSongs NUMBER PRIMARY KEY,
    id_playlist NUMBER,
    name VARCHAR2(255),
    id_song NUMBER,
    order_index NUMBER
);

CREATE SEQUENCE Stream_playlists_songs_SEQ INCREMENT BY 1 MINVALUE 0 NOCYCLE NOCACHE;

-- INIT les SONGS to ALL_SONGS playlist
INSERT INTO Stream_playlists_songs (id_playSongs, id_playlist, name, id_song, order_index) VALUES (Stream_playlists_songs_SEQ.nextval, 0, 'All_Songs', 0, 0);
INSERT INTO Stream_playlists_songs (id_playSongs, id_playlist, name, id_song, order_index) VALUES (Stream_playlists_songs_SEQ.nextval, 0, 'All_Songs', 1, 1);
INSERT INTO Stream_playlists_songs (id_playSongs, id_playlist, name, id_song, order_index) VALUES (Stream_playlists_songs_SEQ.nextval, 0, 'All_Songs', 2, 2);
INSERT INTO Stream_playlists_songs (id_playSongs, id_playlist, name, id_song, order_index) VALUES (Stream_playlists_songs_SEQ.nextval, 0, 'All_Songs', 3, 3);
INSERT INTO Stream_playlists_songs (id_playSongs, id_playlist, name, id_song, order_index) VALUES (Stream_playlists_songs_SEQ.nextval, 0, 'All_Songs', 4, 4);
INSERT INTO Stream_playlists_songs (id_playSongs, id_playlist, name, id_song, order_index) VALUES (Stream_playlists_songs_SEQ.nextval, 0, 'All_Songs', 5, 5);
INSERT INTO Stream_playlists_songs (id_playSongs, id_playlist, name, id_song, order_index) VALUES (Stream_playlists_songs_SEQ.nextval, 0, 'All_Songs', 6, 6);
INSERT INTO Stream_playlists_songs (id_playSongs, id_playlist, name, id_song, order_index) VALUES (Stream_playlists_songs_SEQ.nextval, 0, 'All_Songs', 7, 7);






