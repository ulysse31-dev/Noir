# Noir

Lanceur Android minimaliste : fond noir pur, recherche d'applis (texte seul), heure quand la recherche est vide, et le widget Google Agenda en plein écran à droite.

## Installer

L'APK est compilé par GitHub : télécharger `Noir.apk` dans les Releases du dépôt, l'installer, puis choisir **Noir** comme application d'accueil (« Toujours »).

L'APK est signé avec une clé fixe (`app/noir.keystore`) et son numéro de version monte à chaque compilation : les mises à jour s'installent par-dessus la version précédente, sans désinstaller. Seule la première installation d'une version issue d'avant cette clé demande de désinstaller l'ancienne. Cette clé est publique dans le dépôt : elle sert à la mise à jour, pas à la sécurité.

## Comportement

- Accueil : clavier fermé (toucher la barre de recherche pour l'ouvrir) ; recherche vide = heure + date.
- Résultats : le meilleur est le plus proche du champ. Toucher = lancer, appui long = infos de l'appli (désinstaller), touche « Go » du clavier = lancer le premier résultat.
- Accueil, recherche vide : heure, météo des 24 prochaines heures (Allauch, via Open-Meteo : une phrase donne la plage de pluie, par exemple « Pluie de 17 h à 22 h », avec le pic et les millimètres attendus ; en dessous, la courbe de température en dégradé chaud/froid, les heures (en blanc quand la pluie est probable, seuil `WET_PROB` = 40 %, Weather.kt) et, sous chaque heure où la pluie compte (30 % et plus), son pourcentage en bleu; défile à l'horizontale ; le tracé se rejoue à chaque arrivée sur l'accueil), raccourcis WhatsApp / Messages / Téléphone / Maps / Chrome / Spotify en pictos blancs alignés sur une ligne (sans texte), l'appareil photo à l'extrémité droite. Le clavier ouvert ne garde que l'heure. Toucher l'heure ouvre l'appli Horloge de Google.
- Recherche : dès qu'on tape, deux icônes grises apparaissent à droite du champ : Claude (claude.ai, question pré-remplie) et Google. Ce sont les icônes réelles des applis installées, passées en niveaux de gris (pictogramme blanc neutre si l'appli est absente). Touche « Go » : Claude si la saisie commence ou finit par « ? », Google s'il n'y a aucun résultat d'appli, sinon le premier résultat.
- Touche Accueil : retour à l'accueil, recherche vidée, clavier refermé.
- Animations : ressort à l'appui sur les icônes, entrée décalée des raccourcis et des résultats, tracé de la courbe météo, pages voisines qui reculent pendant le glissement. Les pulsations (deux-points, anneau « maintenant ») s'arrêtent après quelques secondes pour ménager la batterie.
- Page de droite (glisser vers la gauche) : le widget Google Agenda « jour » est choisi automatiquement (accepter la demande d'Android au premier passage) et occupe tout l'écran. À défaut de widget « jour », la liste « agenda » est prise. « Changer de widget » en bas à gauche ouvre la liste pour en choisir un autre.

## Réglages (dans le code)

- `AUTO_LAUNCH_SINGLE_RESULT` (Apps.kt) : lancement automatique quand il ne reste qu'un résultat.
- Police et tailles : `Theme.kt`. Police par défaut = sans-serif du système.
- Lieu de la météo : `WEATHER_LAT` / `WEATHER_LON` (Weather.kt).
- Raccourcis : `Shortcuts.kt`.
- Position de l'heure : `top = 100.dp` dans `HomeContent` (HomePage.kt).

Pictos : Material Symbols (Google), licence Apache 2.0.
