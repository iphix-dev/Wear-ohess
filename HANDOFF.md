# HANDOFF — Reprise du projet WristRelay

Document rédigé le 4 novembre 2026 pour permettre à une autre IA de reprendre le travail sans contexte.

## 1. Objectif de l'utilisateur

Relier une montre TicWatch Pro 5 Enduro (Wear OS) à un Xiaomi 18 Pro en ROM chinoise, sans root, sans Mobvoi Health ni Google Play Services pour le lien, via Bluetooth Classic RFCOMM avec une application personnalisée sur chaque appareil.

Priorité : notifications (H24) et appels. Ensuite santé, batterie, outils (sonnerie/vibration à distance). Interface téléphone : vraiment soignée, style « application Samsung / Apple », légère en mémoire et en batterie.

L'utilisateur écrit en français, est à l'aise sous Linux (Arch) et veut des instructions concrètes et des fichiers complets.

## 2. État actuel du dépôt (ce qui est fait)

- Projet Android unique, module `app`, avec deux flavors : `phone` (applicationId `fr.wristrelay.phone`) et `watch` (`fr.wristrelay.watch`). `minSdk 30`, `compileSdk/targetSdk 35`, Java 17.
- `RelayService.java` : service de liaison Bluetooth RFCOMM avec UUID `96bf751e-9529-4f99-9c1e-f952fedc4091`. Il gère la reprise (`onTaskRemoved`, `ensureStarted`) : les notifications continuent après retrait des applications récentes. **Ce correctif doit être préservé.**
- `MainActivity.java` : interface actuelle, une simple liste de boutons (permissions, choix de l'appareil, apps à transmettre, démarrer/arrêter, notification de test). Partagée avec la montre.
- `BatteryHistory.java` : enregistre les points (timestamp, niveau) dans SharedPreferences, 500 points max.
- `CallActions` et `CallActionReceiver` sont référencés (raccrochage à distance) mais la classe `CallActions` doit être vérifiée dans le dépôt avant toute modification.
- Workflow `.github/workflows/release.yml` : compile `assemblePhoneDebug` et `assembleWatchDebug`, renomme en `WristRelay-phone.apk` et `WristRelay-watch.apk`, les joint en artefact `WristRelay-apks` et à la release si l'événement est `release`.
- Batterie : à l'ouverture de l'app téléphone, une requête `battery_request` est envoyée à la montre (pas de refresh en arrière-plan, conforme à la demande).
- Installation : script `install.sh` existant (adb, Wi-Fi). À vérifier avant modification.

## 3. Ce qui était en cours

Le travail en cours : construire la nouvelle interface téléphone. Deux tentatives ont échoué (environnement de travail interrompu, budget épuisé avant écriture). **Rien n'a été poussé pour l'interface.** Le code du dépôt est donc inchangé à ce stade.

Prévu pour l'interface téléphone :
- Activité dédiée à la version téléphone (la montre garde `MainActivity`).
- Barre de navigation basse avec 4 pages : Accueil, Santé, Outils, Réglages.
- Accueil : jauge de batterie de la montre, courbe d'historique lissée (dessinée en Canvas, sans bibliothèque lourde), état de la connexion.
- Santé : cartes pas, rythme cardiaque, sommeil, sport. Afficher « Aucune donnée » tant que la synchronisation n'existe pas. Ne jamais inventer de valeurs.
- Outils : faire sonner ou vibrer téléphone, montre, les deux. Sonnerie avec deux confirmations. Boutons « bientôt » si le côté montre n'est pas prêt.
- Réglages : toutes les fonctions actuelles de `MainActivity` (permissions, appareil, apps à transmettre, démarrer/arrêter, notification de test).
- Style : sombre ou clair au choix (à demander à l'utilisateur), accent unique, coins arrondis, typographie système, animations sobres.

Ne pas toucher : `RelayService` (sauf demande explicite), le correctif de reprise, l'UUID, le workflow sans raison précise.

## 4. Ce qui reste à faire (ordre suggéré)

1. Demander à l'utilisateur sa préférence de style (sombre/clair, couleur d'accent).
2. Lire le dépôt réel : `app/build.gradle`, `AndroidManifest.xml`, `CallActions.java`, `install.sh`, workflow. Ne rien supposer.
3. Créer l'activité téléphone et la navigation, pousser par petits lots, vérifier la compilation via GitHub Actions après chaque lot.
4. Ajouter la courbe batterie (lecture de `BatteryHistory`).
5. Ajouter les pages Santé (affichage vide honnête), Outils (boutons, confirmations), Réglages (reprise des fonctions actuelles).
6. Côté montre : implémenter la réception des commandes « sonnerie / vibration » et la synchronisation santé, uniquement quand demandé.
7. Mettre à jour le workflow si nécessaire : Node 20 est déprécié, `actions/setup-java` v5 est disponible. Le paquet SDK `tools` a été retiré : ne pas le réinstaller.
8. Tester sur appareils : connexion, notification de test, écran éteint, retrait des récents, reconnexion, batterie.

## 5. Fonctions NON confirmées (ne pas les présenter comme acquises)

- Réception fiable des appels et raccrochage à distance.
- Audio des appels sur la montre.
- Synchronisation santé (sommeil, pas, FC, sport).
- Réponses aux messages, actions de notification.
- Reconnexion automatique après coupure Bluetooth ou redémarrage.
- Autonomie mesurée.

## 6. Contraintes à préserver

- Pas de root, ROM chinoise conservée.
- Aucun ping périodique en arrière-plan : la batterie n'est demandée qu'à l'ouverture de l'app.
- Synchronisation santé uniquement sur action de l'utilisateur (bouton).
- Notifications synchronisées en continu.
- Ne pas inventer UUID, noms de package, versions ou classes existantes sans les lire dans le dépôt.
- Ne pas conseiller de réinitialiser la montre.
- Ne pas publier de contenu privé de notifications ou de secrets.

## 7. Comment reprendre

1. Lire ce fichier, puis le dépôt réel (pas de supposition).
2. Demander à l'utilisateur ses préférences de style et l'état actuel de ses appareils.
3. Travailler par petits lots, un commit par lot, vérification de compilation à chaque fois.
4. Distinguer clairement : code proposé / compilation réussie / test sur appareils.

## 8. Points d'attention connus

- Le premier essai de l'interface a été interrompu : repartir de zéro, pas de fichier partiel à récupérer.
- Les anciens journaux ne décrivent pas forcément la version actuelle.
- Un build réussi ne garantit pas que la liaison Bluetooth fonctionne.
