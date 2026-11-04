# HANDOFF — Reprise du projet WristRelay

Document rédigé le 4 novembre 2026, mis à jour après la construction de la nouvelle interface téléphone, pour permettre à une autre IA de reprendre le travail sans contexte.

## 0. Où on en est (résumé en 30 secondes)

- La **nouvelle interface téléphone sombre** est écrite, **compile** (GitHub Actions, les deux flavors) et est poussée sur la branche **`ui-telephone`**. Elle n'est **pas fusionnée dans `main`**.
- Elle n'a **jamais été lancée** : ni sur un appareil, ni dans un émulateur, ni vue en capture. Le rendu visuel et le comportement réel sont **non vérifiés**.
- Le côté montre n'a pas été modifié. Les commandes « sonnerie / vibration » vers la montre et la synchronisation santé n'existent pas encore (boutons « Bientôt », cartes « Aucune donnée »).
- Prochaine action logique : l'utilisateur installe l'APK de la branche, regarde le rendu, demande des retouches, puis on fusionne dans `main`.

## 1. Objectif de l'utilisateur

Relier une montre TicWatch Pro 5 Enduro (Wear OS) à un Xiaomi 18 Pro en ROM chinoise, sans root, sans Mobvoi Health ni Google Play Services pour le lien, via Bluetooth Classic RFCOMM avec une application personnalisée sur chaque appareil.

Priorité : notifications (H24) et appels. Ensuite santé, batterie, outils (sonnerie/vibration à distance). Interface téléphone : vraiment soignée, style « application Samsung / Apple », légère en mémoire et en batterie.

L'utilisateur écrit en français, est à l'aise sous Linux (Arch) et veut des instructions concrètes et des fichiers complets.

**Choix de style confirmés par l'utilisateur (4 nov. 2026)** : mode **sombre** ; il a laissé le choix de la couleur d'accent à l'IA (retenu : indigo clair `#7B8CFF`).

## 2. État du dépôt

### 2.1 Code existant (non modifié par la session UI, sauf mention)

- Projet Android unique, module `app`, deux flavors : `phone` (applicationId `fr.wristrelay.phone`) et `watch` (`fr.wristrelay.watch`). `minSdk 30`, `compileSdk/targetSdk 35`, Java 17. `buildConfig` activé : `BuildConfig.WATCH` distingue les flavors.
- `RelayService.java` : service de liaison Bluetooth RFCOMM, UUID `96bf751e-9529-4f99-9c1e-f952fedc4091`. Gère la reprise (`onTaskRemoved`, `ensureStarted`) : les notifications continuent après retrait des applications récentes. **Ce correctif doit être préservé. Non modifié.** Le côté téléphone est client RFCOMM, la montre est serveur. Reconnexion avec attente 5, 10, 20, 40, 80, 160 puis 300 s max.
- `MainActivity.java` : ancienne interface (liste de boutons), **toujours utilisée par la montre**. Dans l'APK téléphone elle reste présente mais n'est plus un lanceur.
- `BatteryHistory.java` : enregistre (timestamp, niveau) dans SharedPreferences `battery_history`, 500 points max. **Modifié** : ajout de `Point`, `prefs(Context)` et `read(Context)` (lecture, du plus ancien au plus récent). `record()` est inchangé.
- `CallActions.java`, `CallActionReceiver.java` : existent bien (raccrochage/refus à distance via les boutons de la notification d'appel). Fiabilité **non testée**.
- `app/src/phone/java/fr/wristrelay/Listener.java` : `NotificationListenerService` côté téléphone, filtre sur l'ensemble `allow`.
- `app/src/watch/.../HealthCheckActivity.java` : présent côté montre (non étudié ici).
- `app/build.gradle` : dépendances montre uniquement (health-services-client 1.0.0, kotlin-bom 1.8.21, guava 31.0.1-android). Aucune dépendance côté téléphone.
- Workflow `.github/workflows/release.yml` : `assemblePhoneDebug` + `assembleWatchDebug`, APK renommés `WristRelay-phone.apk` / `WristRelay-watch.apk`, artefact `WristRelay-apks`, joints à la release si l'événement est `release`. Déclenchable à la main (`workflow_dispatch`) sur n'importe quelle branche. **Non modifié.**
- Préférences : fichier `cfg` (`peer`, `relay_enabled`, `allow`).
- `install.sh` : installation adb (Wi-Fi). Non relu ni modifié dans cette session.

### 2.2 Ce qui a été ajouté (branche `ui-telephone`, commit `9cc38a6`)

Tous les nouveaux fichiers sont dans `app/src/phone/java/fr/wristrelay/` (donc **absents du build montre**). Aucune ressource XML, aucune bibliothèque : tout est construit en Java et dessiné en Canvas.

| Fichier | Rôle |
|---|---|
| `PhoneActivity.java` | Activité lanceur du téléphone. Barre de navigation basse (4 pages), gestion des marges système (bord à bord), instantané d'état (`Snap`), actions partagées (démarrer/arrêter, permissions, choix appareil, choix apps, test). Verrouillée en portrait. |
| `Page.java` | Base des pages (défilement, en-tête, cartes). |
| `HomePage.java` | Accueil : carte liaison, carte « À configurer » (seulement s'il manque quelque chose), jauge batterie + bouton d'actualisation, courbe d'historique. |
| `HealthPage.java` | Santé : 4 cartes (pas, rythme cardiaque, sommeil, sport) toutes à « Aucune donnée », bouton de synchronisation grisé. **Aucune valeur inventée.** |
| `ToolsPage.java` | Outils : sonnerie (deux confirmations, volume alarme, 30 s max, bouton Arrêter) et vibration du **téléphone**. Montre et « les deux » : « Bientôt », boutons inactifs, **rien n'est envoyé à la montre**. |
| `SettingsPage.java` | Réglages : permissions, accès notifications, réglages Bluetooth, appareil autorisé, applications à transmettre, interrupteur de liaison, notification de test, version. |
| `Link.java` | Lecture de l'état de la liaison + actions (démarrer, arrêter, test, demande de batterie). Reprend à l'identique les vérifications de l'ancienne `MainActivity`. |
| `Ui.java` | Palette, formes, boutons, boîtes de dialogue sombres, temps relatif. |
| `IconView.java` | Icônes en trait dessinées en Canvas (grille 24). |
| `GaugeView.java` | Jauge circulaire animée (arc 270°, dégradé, orange ≤ 30 %, rouge ≤ 15 %). |
| `ChartView.java` | Courbe d'historique : interpolation cubique monotone (ne dépasse jamais 0–100 %), points réels visibles, lecture au toucher. |

Autres changements : `app/src/phone/AndroidManifest.xml` (PhoneActivity lanceur, `MainActivity` en `tools:node="replace"` non exportée, permission `VIBRATE`, portrait) et `BatteryHistory.java` (voir plus haut).

Palette : fond `#0A0C11`, surfaces `#13161E` / `#1B1F2B`, texte `#F3F5F9` / `#A0A7B8` / `#666E82`, accent `#7B8CFF` (clair `#B4BEFF`), alerte `#FFB35C`, danger `#FF6F7F`.

### 2.3 Comportement de la liaison dans l'interface

- États affichés : arrêtée, démarrage, recherche de la montre, connectée, **service interrompu** (service demandé mais absent depuis plus de 6 s, bouton « Relancer »).
- Connexion détectée via `RelayService.instance`, `running` et `socket.isConnected()` (attention : côté téléphone, `socket` est non nul **avant** la fin de `connect()`, d'où `isConnected()`).
- Un minuteur de 2,5 s tourne **uniquement quand l'écran est au premier plan** (arrêté à la mise en pause) ; il relit l'état, il n'envoie rien.
- `battery_request` est envoyée : à l'ouverture de l'app si déjà connectée, quand la montre vient de se connecter pendant que l'app est ouverte, et sur appui du bouton d'actualisation. Jamais en arrière-plan. (Piège : `RelayService` vide la file à la connexion, d'où l'envoi *après* connexion et non avant.)
- Changer d'appareil autorisé pendant que la liaison tourne demande d'arrêter puis redémarrer la liaison (le service lit `peer` au démarrage).

## 3. Historique de la session (ce qui s'est passé)

1. Lecture du `HANDOFF.md` et du README via GitHub. Choix de l'utilisateur : sombre, accent laissé à l'IA.
2. Lecture du vrai code (build.gradle, manifestes, MainActivity, RelayService, CallActions, Listener, workflow).
3. Le dossier de travail contenait déjà, **non commités**, une modification de `BatteryHistory.java` (méthodes de lecture) et du manifeste téléphone (déclarant une `PhoneActivity` inexistante) : restes d'une tentative précédente, cohérents avec le plan, conservés et complétés.
4. Écriture de l'interface (fichiers du tableau ci-dessus), repartie de zéro.
5. Pas de SDK Android ni de Maven Central accessibles depuis l'environnement de travail : seule une vérification de **syntaxe** a pu être faite en local (`javac` seul). La vraie compilation s'est faite sur GitHub Actions.
6. **Incident** : un `git add app/src/phone` a embarqué quatre fichiers orphelins d'une tentative précédente (`NavBar`, `RelayControl`, `BatteryGaugeView`, `BatteryChartView`, datés de 02:18, utilisés par aucun fichier actuel). Détecté avant le build : retirés du commit, branche corrigée (push forcé avec garde sur le commit exact). Les fichiers ont été mis de côté hors du dépôt, dans le dossier temporaire de la session (donc **perdus à la fin de la session**). Ils ne sont plus sur la branche. **Conseil : toujours lister `git status` complet avant un `git add` de dossier.**
7. Build GitHub Actions lancé sur `ui-telephone` : **succès** (run `41782977390`), APK téléphone et montre produits, artefact `WristRelay-apks`. L'artefact n'a pas pu être téléchargé depuis l'environnement de travail (accès bloqué) : l'utilisateur doit le prendre sur la page du run.
8. Un hook a signalé à tort « commit non poussé / pas de branche distante » : faux positif, la branche distante est identique au commit local (le clone n'a simplement pas de suivi local `origin/ui-telephone`).

## 4. Ce qui reste à faire (ordre suggéré)

1. **Installer l'APK de la branche et regarder le rendu** (non vérifié). Corriger espacements, tailles de texte, couleurs, éventuels plantages.
2. Fusionner `ui-telephone` dans `main` quand l'utilisateur valide (pas fait, à sa demande).
3. Option : faire ouvrir `PhoneActivity` (et non l'ancienne `MainActivity`) depuis la notification « WristRelay actif ». Nécessite de modifier `RelayService.status()` : **demander l'accord de l'utilisateur** (fichier à ne pas toucher sans demande).
4. Côté montre, **uniquement quand l'utilisateur le demande** : réception des commandes sonnerie/vibration (puis activer les boutons « Bientôt » de la page Outils) ; envoi de données santé sur action manuelle (puis alimenter la page Santé).
5. Mettre à jour le workflow si souhaité : Node 20 déprécié, `actions/setup-java` v5 disponible, label `ubuntu-latest` qui migre vers Ubuntu 26. Le paquet SDK `tools` a été retiré : ne pas le réinstaller.
6. Tests sur appareils : connexion, notification de test, écran éteint, retrait des récents, reconnexion, appels, batterie.
7. Pistes déjà évoquées dans le README : protocole avec accusé de réception, nettoyage des notifications au redémarrage, CompanionDeviceManager, OngoingActivity Wear OS.

## 5. Fonctions NON confirmées (ne pas les présenter comme acquises)

- **L'interface téléphone elle-même** : compilée, jamais exécutée ni vue.
- Réception fiable des appels et raccrochage à distance.
- Audio des appels sur la montre.
- Synchronisation santé (sommeil, pas, FC, sport) : n'existe pas.
- Sonnerie/vibration de la montre : n'existe pas.
- Réponses aux messages, actions de notification.
- Reconnexion automatique après coupure Bluetooth ou redémarrage.
- Autonomie mesurée.
- Sonnerie du téléphone : utilise le volume des **alarmes** ; comportement en mode ne pas déranger non vérifié.

## 6. Contraintes à préserver

- Pas de root, ROM chinoise conservée.
- Aucun ping périodique en arrière-plan : la batterie n'est demandée qu'à l'ouverture de l'app (ou à la connexion pendant qu'elle est ouverte, ou sur bouton).
- Synchronisation santé uniquement sur action de l'utilisateur (bouton).
- Notifications synchronisées en continu.
- Ne pas toucher à `RelayService` (sauf demande explicite), au correctif de reprise, à l'UUID, au workflow sans raison précise.
- Ne pas inventer UUID, noms de package, versions ou classes existantes sans les lire dans le dépôt.
- Ne jamais afficher de fausses valeurs de santé.
- Ne pas conseiller de réinitialiser la montre.
- Ne pas publier de contenu privé de notifications ou de secrets.

## 7. Comment reprendre

1. Lire ce fichier, puis le dépôt réel (pas de supposition). Se placer sur la branche `ui-telephone` tant qu'elle n'est pas fusionnée.
2. Demander à l'utilisateur ce qu'il pense du rendu et l'état de ses appareils.
3. Travailler par petits lots, un commit par lot, vérification de compilation via GitHub Actions à chaque fois : `gh workflow run release.yml --ref <branche>` puis `gh run watch`.
4. Avant tout `git add`, vérifier `git status` en entier (fichiers non suivis inclus).
5. Distinguer clairement : code proposé / compilation réussie / test sur appareils.

## 8. Points d'attention connus

- Aucun compilateur Android local : seule la syntaxe se vérifie hors CI. Les erreurs de types n'apparaissent que dans GitHub Actions.
- Un build réussi ne garantit pas que la liaison Bluetooth fonctionne.
- La notification « WristRelay actif » ouvre encore l'ancienne `MainActivity` (voir tâche 3).
- Interface verrouillée en portrait ; sur très grand écran Android peut ignorer ce verrou.
- `Ui.sp()` utilise `scaledDensity` (déprécié mais fonctionnel) : simple avertissement de compilation.
- Le choix des applications à transmettre est une boîte de dialogue à cases (liste triée), pas une page dédiée.
- Les anciens journaux ne décrivent pas forcément la version actuelle.
