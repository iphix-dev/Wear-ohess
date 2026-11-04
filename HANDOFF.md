# HANDOFF — Reprise du projet WristRelay

Document rédigé le 4 novembre 2026, mis à jour après la construction de la nouvelle interface téléphone, pour permettre à une autre IA de reprendre le travail sans contexte.

## 0. Où on en est (résumé en 30 secondes)

- La **nouvelle interface téléphone sombre** est écrite, **compile** (GitHub Actions, les deux flavors) et est poussée sur la branche **`ui-telephone`**. Elle n'est **pas fusionnée dans `main`**. Dernier commit vérifié par le build : `4f417d1` (run GitHub Actions `41895829568`, succès).
- L'utilisateur a installé l'interface et la trouve « magnifique » (rendu validé par lui, le 4 nov. au soir). Le **comportement détaillé** (liaison, notifications, sonnerie) n'a pas été confirmé en retour : l'IA ne peut lancer ni appareil ni émulateur.
- Le 4 nov. 2026 l'utilisateur a **autorisé** de toucher `RelayService` et la montre. Fait : notifications montre avec titre = application d'origine, notification de service du téléphone discrète, sonnerie/vibration de la montre (commande `find`). **Reste à faire** : synchronisation santé (cartes « Aucune donnée »). Tout cela est compilé mais **non testé sur appareil**.
- Prochaine action logique : l'utilisateur installe l'APK de la branche, teste les nouveautés (notifications, sonnerie de la montre), puis on fusionne dans `main`.

## 1. Objectif de l'utilisateur

Relier une montre TicWatch Pro 5 Enduro (Wear OS) à un Xiaomi 18 Pro en ROM chinoise, sans root, sans Mobvoi Health ni Google Play Services pour le lien, via Bluetooth Classic RFCOMM avec une application personnalisée sur chaque appareil.

Priorité : notifications (H24) et appels. Ensuite santé, batterie, outils (sonnerie/vibration à distance). Interface téléphone : vraiment soignée, style « application Samsung / Apple », légère en mémoire et en batterie.

L'utilisateur écrit en français, est à l'aise sous Linux (Arch) et veut des instructions concrètes et des fichiers complets.

**Choix de style confirmés par l'utilisateur (4 nov. 2026)** : mode **sombre** ; il a laissé le choix de la couleur d'accent à l'IA (retenu : indigo clair `#7B8CFF`).

## 2. État du dépôt

### 2.1 Code existant (non modifié par la session UI, sauf mention)

- Projet Android unique, module `app`, deux flavors : `phone` (applicationId `fr.wristrelay.phone`) et `watch` (`fr.wristrelay.watch`). `minSdk 30`, `compileSdk/targetSdk 35`, Java 17. `buildConfig` activé : `BuildConfig.WATCH` distingue les flavors.
- `RelayService.java` : service de liaison Bluetooth RFCOMM, UUID `96bf751e-9529-4f99-9c1e-f952fedc4091`. Gère la reprise (`onTaskRemoved`, `ensureStarted`) : les notifications continuent après retrait des applications récentes. **Ce correctif doit être préservé (intact).** Modifié le 4 nov. à la demande de l'utilisateur : canal `status_quiet` (importance minimale) ; toucher sur la notification d'état = intent de lancement de l'app (PhoneActivity sur le téléphone, MainActivity sur la montre) ; textes d'état lisibles ; réception de l'op `find` (montre) ; notification montre avec titre = application d'origine. Le côté téléphone est client RFCOMM, la montre est serveur. Reconnexion avec attente 5, 10, 20, 40, 80, 160 puis 300 s max.
- `MainActivity.java` : ancienne interface (liste de boutons), **toujours utilisée par la montre**. Dans l'APK téléphone elle reste présente mais n'est plus un lanceur.
- `BatteryHistory.java` : enregistre (timestamp, niveau) dans SharedPreferences `battery_history`, 500 points max. **Modifié** : ajout de `Point`, `prefs(Context)`, `read(Context)` (lecture, du plus ancien au plus récent) et `clear(Context)`. `record()` est inchangé.
- `CallActions.java`, `CallActionReceiver.java` : existent bien (raccrochage/refus à distance via les boutons de la notification d'appel). Fiabilité **non testée**.
- `app/src/phone/java/fr/wristrelay/Listener.java` : `NotificationListenerService` côté téléphone, filtre sur l'ensemble `allow`.
- `app/src/watch/.../HealthCheckActivity.java` : présent côté montre (non étudié ici).
- `app/build.gradle` : dépendances montre uniquement (health-services-client 1.0.0, kotlin-bom 1.8.21, guava 31.0.1-android). Aucune dépendance côté téléphone.
- Workflow `.github/workflows/release.yml` : `assemblePhoneDebug` + `assembleWatchDebug`, APK renommés `WristRelay-phone.apk` / `WristRelay-watch.apk`, artefact `WristRelay-apks`, joints à la release si l'événement est `release`. Déclenchable à la main (`workflow_dispatch`) sur n'importe quelle branche. **Non modifié.**
- Préférences : fichier `cfg` (`peer`, `relay_enabled`, `allow`) ; `battery_history` (points) ; `ui` (`chart_range` : période de la courbe, 0 à 3, ajouté par l'interface).
- `install.sh` : installation adb (Wi-Fi). Non relu ni modifié dans cette session.

### 2.2 Ce qui a été ajouté (branche `ui-telephone`, commits jusqu'à `4f417d1`)

Tous les nouveaux fichiers sont dans `app/src/phone/java/fr/wristrelay/` (donc **absents du build montre**). Aucune ressource XML, aucune bibliothèque : tout est construit en Java et dessiné en Canvas.

| Fichier | Rôle |
|---|---|
| `PhoneActivity.java` | Activité lanceur du téléphone. Barre de navigation basse (4 pages), gestion des marges système (bord à bord), instantané d'état (`Snap`), actions partagées (démarrer/arrêter, permissions, choix appareil, choix apps, test). Verrouillée en portrait. |
| `Page.java` | Base des pages (défilement, en-tête, cartes). |
| `HomePage.java` | Accueil : carte liaison, carte « À configurer » (seulement s'il manque quelque chose), jauge batterie + bouton d'actualisation, courbe d'historique avec puces de période 6 h / 24 h / 7 j / Tout (choix mémorisé). |
| `HealthPage.java` | Santé : 4 cartes (pas, rythme cardiaque, sommeil, sport) toutes à « Aucune donnée », bouton de synchronisation grisé. **Aucune valeur inventée.** |
| `ToolsPage.java` | Outils : sonnerie (deux confirmations, volume alarme, 30 s max, bouton Arrêter) et vibration pour le téléphone, la montre ou les deux. La montre n'est commandée que si la liaison est connectée (op `find`, modes `ring` / `vibrate` / `stop`). |
| `FindActions.java` (dossier `main`, commun aux deux flavors) | Sonnerie en boucle + vibration, arrêt automatique à 30 s ; utilisé par le téléphone (local) et par la montre (commande reçue). |
| `SettingsPage.java` | Réglages : permissions, accès notifications, réglages Bluetooth, appareil autorisé, applications à transmettre, interrupteur de liaison, notification de test, export CSV et effacement de l'historique de batterie, version. |
| `Link.java` | Lecture de l'état de la liaison + actions (démarrer, arrêter, test, demande de batterie). Reprend à l'identique les vérifications de l'ancienne `MainActivity`. |
| `Ui.java` | Palette, formes, boutons, boîtes de dialogue sombres, temps relatif. |
| `IconView.java` | Icônes en trait dessinées en Canvas (grille 24). |
| `GaugeView.java` | Jauge circulaire animée (arc 270°, dégradé, orange ≤ 30 %, rouge ≤ 15 %). |
| `ChartView.java` | Courbe d'historique : interpolation cubique monotone (ne dépasse jamais 0–100 %), points réels visibles, lecture au toucher, textes de « vide » réglables. |
| `HistoryExport.java` | Met l'historique en CSV (`timestamp_ms,date,niveau_pct`) ; partagé via la feuille de partage Android, uniquement sur action de l'utilisateur. |

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
8. Un hook a signalé à tort « commit non poussé / pas de branche distante » : faux positif, la branche distante est identique au commit local (le clone n'a simplement pas de suivi local `origin/ui-telephone`). La référence locale a ensuite été créée.
9. L'utilisateur ne pouvait pas tester et a demandé de continuer à améliorer. Lecture retenue : améliorations **uniquement côté téléphone**, sans toucher à `RelayService` ni au côté montre (règle du handoff), par petits lots vérifiés par le build.
10. **Lot 1** (commit `994be8b`, build OK) : export CSV et effacement (avec confirmation) de l'historique de batterie dans Réglages ; descriptions d'accessibilité (navigation, jauge qui annonce sa valeur, bouton d'actualisation, courbe, icônes décoratives ignorées) ; `Ui.sp()` n'utilise plus `scaledDensity`.
11. **Lot 2** (commit `a7766b3`, build OK) : sélecteur de période 6 h / 24 h / 7 j / Tout sur la courbe, choix mémorisé, message dédié quand il n'y a aucune mesure sur la période.
12. L'utilisateur a installé l'interface, l'a trouvée « magnifique » et a laissé la suite (fonctions manquantes, montre) à l'IA, avec deux demandes : sur la montre, le titre de la notification doit être l'application d'origine (pas WristRelay) ; sur le Xiaomi 18 Pro, la notification de service doit être discrète (« plus de notifications »).
13. **Lot A** (commits `71d61f6` puis `756cc18`) : un premier essai avec `EXTRA_SUBSTITUTE_APP_NAME` a **échoué à la compilation** (constante non publique, permission réservée aux applis système) ; remplacé par des API publiques : titre de la notification montre = nom de l'application, ligne courte « Expéditeur : message », texte déplié sur deux lignes. L'en-tête système « WristRelay » de la carte ne peut pas être changé sans droits système. Notification de service : nouveau canal `status_quiet` en importance minimale, ancien canal supprimé quand le service ne tourne pas, toucher = ouvre l'interface de l'appareil.
14. **Lot B** (commit `4f417d1`, build OK) : `FindActions`, op `find` côté montre, permission `VIBRATE` dans le manifeste montre, cartes Montre et « Les deux » activées dans Outils.

## 4. Ce qui reste à faire (ordre suggéré)

1. **Installer l'APK de la branche et regarder le rendu** (non vérifié). Corriger espacements, tailles de texte, couleurs, éventuels plantages.
2. Fusionner `ui-telephone` dans `main` quand l'utilisateur valide (pas fait, à sa demande).
3. ~~Faire ouvrir `PhoneActivity` depuis la notification d'état~~ : fait (lot A).
4. Côté montre : ~~sonnerie/vibration~~ fait (lot B). **Reste** : envoi de données santé sur action manuelle (Health Services, permissions à demander sur la montre), puis alimenter la page Santé. À ne pas lancer sans pouvoir tester : les erreurs de permissions sont invisibles depuis un simple build.
5. Mettre à jour le workflow si souhaité : Node 20 déprécié, `actions/setup-java` v5 disponible, label `ubuntu-latest` qui migre vers Ubuntu 26. Le paquet SDK `tools` a été retiré : ne pas le réinstaller.
6. Tests sur appareils : connexion, notification de test, écran éteint, retrait des récents, reconnexion, appels, batterie.
7. Pistes déjà évoquées dans le README : protocole avec accusé de réception, nettoyage des notifications au redémarrage, CompanionDeviceManager, OngoingActivity Wear OS.
8. Idées côté téléphone sans risque pour la liaison (pas encore faites) : sélecteur d'applications avec recherche et icônes (aujourd'hui une boîte de dialogue à cases) ; choix de la couleur d'accent dans Réglages ; affichage de la dernière connexion.
9. Autres idées : icône de l'application d'origine en grande icône de la notification montre (à transférer en petit PNG, trame limitée à 16 384 octets) ; sélecteur d'applications avec recherche et icônes.

## 5. Fonctions NON confirmées (ne pas les présenter comme acquises)

- **L'interface téléphone elle-même** : compilée, jamais exécutée ni vue.
- Réception fiable des appels et raccrochage à distance.
- Audio des appels sur la montre.
- Synchronisation santé (sommeil, pas, FC, sport) : n'existe pas.
- Sonnerie/vibration de la montre : implémentées et compilées, **jamais testées** (pas d'accusé de réception : la montre ne répond pas).
- Notification de service « discrète » : canal en importance minimale, mais le rangement exact dans « plus de notifications » dépend de l'interface du Xiaomi (HyperOS), non vérifié.
- Titre des notifications montre : implémenté, rendu sur la montre non vérifié.
- Réponses aux messages, actions de notification.
- Reconnexion automatique après coupure Bluetooth ou redémarrage.
- Autonomie mesurée.
- Export CSV, effacement de l'historique, sélecteur de période, accessibilité : compilés, jamais exécutés.
- Sonnerie du téléphone : utilise le volume des **alarmes** ; comportement en mode ne pas déranger non vérifié.

## 6. Contraintes à préserver

- Pas de root, ROM chinoise conservée.
- Aucun ping périodique en arrière-plan : la batterie n'est demandée qu'à l'ouverture de l'app (ou à la connexion pendant qu'elle est ouverte, ou sur bouton).
- Synchronisation santé uniquement sur action de l'utilisateur (bouton).
- Notifications synchronisées en continu.
- Ne pas toucher à `RelayService` sans besoin réel (l'utilisateur a autorisé les changements de notifications et de `find` le 4 nov. 2026), ni au correctif de reprise, ni à l'UUID, ni au workflow sans raison précise.
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
- Interface verrouillée en portrait ; sur très grand écran Android peut ignorer ce verrou.
- Le choix des applications à transmettre est une boîte de dialogue à cases (liste triée), pas une page dédiée.
- Les anciens journaux ne décrivent pas forcément la version actuelle.
