# Noir

Lanceur Android minimaliste : fond noir pur, recherche d'applis (texte seul), heure quand la recherche est vide, une page Lire à gauche et le widget Google Agenda en plein écran à droite.

## Installer

L'APK est compilé par GitHub : télécharger `Noir.apk` dans les Releases du dépôt, l'installer, puis choisir **Noir** comme application d'accueil (« Toujours »).

L'APK est signé avec une clé fixe (`app/noir.keystore`) et son numéro de version monte à chaque compilation : les mises à jour s'installent par-dessus la version précédente, sans désinstaller. Seule la première installation d'une version issue d'avant cette clé demande de désinstaller l'ancienne. Cette clé est publique dans le dépôt : elle sert à la mise à jour, pas à la sécurité.

## Comportement

- Accueil : clavier fermé (toucher la barre de recherche pour l'ouvrir) ; recherche vide = heure + date.
- Résultats : le meilleur est le plus proche du champ. Toucher = lancer, appui long = infos de l'appli (désinstaller), touche « Go » du clavier = lancer le premier résultat.
- Accueil, recherche vide : heure, météo des 24 prochaines heures (Allauch, via Open-Meteo : courbe de température, barres de pluie, heures ; défile à l'horizontale ; la courbe se dessine à chaque arrivée sur l'accueil), raccourcis WhatsApp / Messages / Téléphone / Maps / Chrome / Spotify en pictos blancs alignés sur une ligne (sans texte), l'appareil photo à l'extrémité droite. Le clavier ouvert ne garde que l'heure. Toucher l'heure ouvre l'appli Horloge de Google.
- Recherche : dès qu'on tape, un micro est à droite du champ vide : il lance l'assistant en mode écoute (commande vocale Android, via Gemini ou l'appli Google ; à défaut, ouverture de l'appli Gemini, de l'assistant du téléphone, puis gemini.google.com). Dès qu'on tape, il laisse la place à deux icônes grises : Claude (claude.ai, question pré-remplie) et Google. Ce sont les icônes réelles des applis installées, passées en niveaux de gris (pictogramme blanc neutre si l'appli est absente). Touche « Go » : Claude si la saisie commence ou finit par « ? », Google s'il n'y a aucun résultat d'appli, sinon le premier résultat.
- Touche Accueil : retour à l'accueil, recherche vidée, clavier refermé.
- Page de gauche (glisser vers la droite) : Lire = un poème à apprendre. Un poème à la fois, en grand : l'œil barré affiche seulement les initiales pour réciter, la flèche passe au suivant, le rond blanc coché marque le poème comme appris et amène le suivant. La progression (« 3 / 19 apprises ») et les textes déjà chargés restent sur le téléphone. Les textes viennent de Wikisource (domaine public), seuls le titre et l'auteur sont dans le code : `POEMS` (Poems.kt).
- Animations : ressort à l'appui sur les icônes, entrée décalée des raccourcis et des résultats, tracé de la courbe météo, pages voisines qui reculent pendant le glissement. Les pulsations (deux-points, anneau « maintenant ») s'arrêtent après quelques secondes pour ménager la batterie.
- Page de droite (glisser vers la gauche) : le widget Google Agenda « jour » est choisi automatiquement (accepter la demande d'Android au premier passage) et occupe tout l'écran. À défaut de widget « jour », la liste « agenda » est prise. « Changer de widget » en bas à gauche ouvre la liste pour en choisir un autre.

## Réglages (dans le code)

- `AUTO_LAUNCH_SINGLE_RESULT` (Apps.kt) : lancement automatique quand il ne reste qu'un résultat.
- Police et tailles : `Theme.kt`. Police par défaut = sans-serif du système.
- Lieu de la météo : `WEATHER_LAT` / `WEATHER_LON` (Weather.kt).
- Raccourcis : `Shortcuts.kt`.
- Poèmes de la page Lire : `POEMS` (Poems.kt), une ligne par poème (titre, auteur).
- Position de l'heure : `top = 100.dp` dans `HomeContent` (HomePage.kt).

Pictos : Material Symbols (Google), licence Apache 2.0.

## Noir et blanc (bouton en haut à droite de l'accueil)

Le picto « contraste » bascule l'écran du téléphone en niveaux de gris (filtre Android « monochromie »).
Pour que ça marche d'un tap, une autorisation à donner **une seule fois** (depuis un ordinateur ou une appli adb sur le téléphone, ex. LADB) :

    adb shell pm grant com.noir.launcher android.permission.WRITE_SECURE_SETTINGS

Sans cette autorisation, le bouton ouvre les réglages d'accessibilité. Sur un Galaxy, le noir et blanc existe aussi dans
Réglages > Bien-être numérique > Mode Coucher (« Échelle de gris »), ou via Options pour les développeurs > « Simuler un espace colorimétrique » > Monochromie.

## Poèmes : en cas de souci

Un poème qui ne se charge pas n'est jamais retiré pour de bon : il est simplement écarté jusqu'à la prochaine ouverture.
Si aucun ne se charge, la page affiche « Réessayer » et, en petit, la cause (nombre de résultats, pages lues…).
