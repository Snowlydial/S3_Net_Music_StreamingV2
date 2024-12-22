Main:
    - MusicServer est le server
    - MusicClient est le client

Dans MyObj:
> Les classes dans MyObj servent a conserver les donnees de la DB dans un objet pour des manipulations plus simples
> Ces objets sont majoritairement des 'copies' des donnees de la DB
    
    - PausablePlayer: le lecteur audio qui contient les methodes du playback: play, pause/resume (son stop n'est pas utilise dans notre programme)

    - Song: permet de sauvegarder les infos des chansons de la DB dans un objet

    - Playlist: pretty obvious on what it does (it uses la classe Song)

Dans Utility:
> Diverses classes à rôles divers (indiqué par leurs noms), manipulant à la fois les instances d'objet avec les tables de la DB

    - ConnectJDBC: la connection à la DB
    
    - ConfigUtil: get les data dans le fichier config.json (needs Jackson.jar) 
    
    - MusicUtil: elle sert de pont entre le client et le serveur (permet l'interaction entre les deux, ex: stream/receive)

    - SongHandler: liaison entre la classe Song et la table Stream_songs on DB

    - PlaylistHandler: liaison entre la classe Playlist et la table Stream_playlist_songs