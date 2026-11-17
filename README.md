# Noir

Lanceur Android minimaliste : fond noir pur, recherche d'applis (texte seul), heure quand la recherche est vide, une page Lire à gauche et le widget Google Agenda en plein écran à droite.

## Installer

L'APK est compilé par GitHub : télécharger `Noir.apk` dans les Releases du dépôt, l'installer, puis choisir **Noir** comme application d'accueil (« Toujours »).

## Comportement

- Accueil : clavier fermé (toucher la barre de recherche pour l'ouvrir) ; recherche vide = heure + date.
- Résultats : le meilleur est le plus proche du champ. Toucher = lancer, appui long = infos de l'appli (désinstaller), touche « Go » du clavier = lancer le premier résultat.
- Accueil, recherche vide : heure, météo des 24 prochaines heures (Allauch, via Open-Meteo : courbe de température, barres de pluie, heures ; défile à l'horizontale ; la courbe se dessine à chaque arrivée sur l'accueil), raccourcis WhatsApp / Messages / Téléphone / Maps / Chrome en pictos blancs alignés sur une ligne (sans texte), l'appareil photo à l'extrémité droite. Le clavier ouvert ne garde que l'heure.
- Recherche : dès qu'on tape, deux icônes grises apparaissent à droite du champ : Claude (claude.ai, question pré-remplie) et Google. Ce sont les icônes réelles des applis installées, passées en niveaux de gris (pictogramme blanc neutre si l'appli est absente). Touche « Go » : Claude si la saisie commence ou finit par « ? », Google s'il n'y a aucun résultat d'appli, sinon le premier résultat.
- Touche Accueil : retour à l'accueil, recherche vidée, clavier refermé.
- Page de gauche (glisser vers la droite) : Lire. L'article du jour de Wikipédia, puis des articles par thème (WordPress, Marseille, Photo, IA) issus de flux RSS ; toucher ouvre le lien. Actualisé au plus une fois par heure.
- Animations : ressort à l'appui sur les icônes, entrée décalée des raccourcis et des résultats, tracé de la courbe météo, pages voisines qui reculent pendant le glissement. Les pulsations (deux-points, anneau « maintenant ») s'arrêtent après quelques secondes pour ménager la batterie.
- Page de droite (glisser vers la gauche) : au premier passage, choisir un widget Google Agenda dans la liste et accepter la demande d'Android. « Changer de widget » en bas à gauche permet d'en choisir un autre.

## Réglages (dans le code)

- `AUTO_LAUNCH_SINGLE_RESULT` (Apps.kt) : lancement automatique quand il ne reste qu'un résultat.
- Police et tailles : `Theme.kt`. Police par défaut = sans-serif du système.
- Lieu de la météo : `WEATHER_LAT` / `WEATHER_LON` (Weather.kt).
- Raccourcis : `Shortcuts.kt`.
- Sources de la page Lire : `FEEDS` et `TOPICS` (Feed.kt).
- Position de l'heure : `top = 100.dp` dans `HomeContent` (HomePage.kt).

Pictos : Material Symbols (Google), licence Apache 2.0.
