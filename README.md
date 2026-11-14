# Noir

Lanceur Android minimaliste : fond noir pur, recherche d'applis (texte seul), heure quand la recherche est vide, et une page Agenda plein écran en glissant vers la gauche.

## Installer

1. Ouvrir le dossier `Noir` dans Android Studio (laisser la synchro Gradle se terminer ; accepter la mise à jour d'AGP/Gradle si elle est proposée).
2. Brancher le téléphone (débogage USB) puis **Run ▶**, ou **Build > Build APK(s)** pour obtenir un APK de debug à copier sur le téléphone.
3. Appuyer sur Accueil, choisir **Noir** puis « Toujours ».
4. Glisser vers la gauche : page Agenda, puis « Autoriser l'accès à l'agenda ».

## Comportement

- Accueil : clavier fermé (toucher la barre de recherche pour l'ouvrir) ; recherche vide = heure + date.
- Résultats : le meilleur est le plus proche du champ. Toucher = lancer, appui long = infos de l'appli (désinstaller), touche « Go » du clavier = lancer le premier résultat.
- Touche Accueil : retour à l'accueil, recherche vidée, clavier refermé.
- Agenda : 14 prochains jours, calendriers visibles de Google Agenda (lecture locale, sans compte ni clé d'API). Toucher un événement l'ouvre dans Google Agenda.

## Réglages (dans le code)

- `AUTO_LAUNCH_SINGLE_RESULT` (Apps.kt) : lancement automatique quand il ne reste qu'un résultat.
- `AGENDA_DAYS` (AgendaPage.kt) : nombre de jours affichés.
- Police et tailles : `Theme.kt`. Police par défaut = sans-serif du système (Roboto).
