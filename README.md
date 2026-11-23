# Noir

Lanceur Android minimaliste : fond noir pur, recherche d'applis (texte seul), heure quand la recherche est vide, une page Objectifs à gauche et le widget Google Agenda en plein écran à droite.

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

## Page Objectifs (à gauche de l'accueil)

Une liste d'objectifs à tenir (par exemple « Nourriture non transformée »). Chaque objectif affiche la semaine en cours en 7 ronds : touche un rond pour cocher un jour tenu (blanc plein), retouche pour décocher. Aujourd'hui est entouré de blanc, les jours passés non tenus en gris, les jours à venir ne se cochent pas. À droite, le nombre de jours tenus cette semaine ; dessous, la série en cours.
Toucher le nom ouvre le détail : jours tenus du mois, série en cours, meilleure série et calendrier du mois (flèches pour remonter dans les mois, jours passés modifiables).
« Modifier » : le moins rouge retire un objectif (confirmation « Supprimer ? », ses jours cochés sont effacés), les chevrons réordonnent, la ligne du bas ajoute. Tout est enregistré sur le téléphone (`goals_json`, Goals.kt), sans réseau. « Retour » revient de l'écran de détail à la liste, puis à l'accueil.

## Sans Shorts (appli séparée)

Le dossier `shorts/` contient une deuxième appli, **Sans Shorts** (`com.noir.shorts`), compilée en même temps que Noir et publiée dans les Releases sous `SansShorts.apk`. Elle est séparée du lanceur car elle utilise l'accessibilité, que Google Play Protect surveille : si Play Protect bloque un jour le bloqueur, Noir n'est pas touché.

Ce qu'elle fait : quitte les Shorts dès qu'ils s'affichent dans l'appli YouTube (touche « Retour » automatique, message « Pas de Shorts. »). L'accueil, la recherche et les vidéos normales de YouTube ne sont pas touchés. Si les Shorts restent affichés après trois « Retour » rapprochés, elle renvoie à l'écran d'accueil.

Installer : Play Protect bloque l'installation (« Appli bloquée pour protéger votre appareil »). Play Store > icône de profil > Play Protect > roue crantée > désactiver « Analyser les applis avec Play Protect », installer `SansShorts.apk`, puis réactiver Play Protect.

Activer : ouvrir Sans Shorts > « Activer dans l'accessibilité » > Sans Shorts > activer. Si l'interrupteur est grisé (« Paramètre restreint ») : « Infos de l'appli » > menu ⋮ > « Autoriser les paramètres restreints », puis recommencer. L'écran de Sans Shorts indique « Actif » quand c'est bon.

Le service n'écoute que YouTube (`com.google.android.youtube`), ne cherche que le lecteur de Shorts (`reel_player_page_container`), n'enregistre rien et n'a pas accès au réseau. Si YouTube renomme ce lecteur et que les Shorts repassent, ajouter le nouveau nom dans `SHORTS_VIEW_IDS` (shorts/…/ShortsBlocker.kt).
