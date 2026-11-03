# WristRelay 0.1 — prototype non compilé et non testé sur appareils

Deux variantes natives Java, sans bibliothèque réseau, Google Data Layer, analytics, INTERNET, GPS, micro, capteurs ni wakelock. Bluetooth RFCOMM sécurisé (socket Android authentifiée/chiffrée via association système), avec appareil distant autorisé explicitement sur chaque côté. Pas de sécurité applicative additionnelle : vérifier le code Bluetooth lors de l'association et n'autoriser que son propre appareil.

## Limites importantes
Prototype de validation, pas produit final ni autonomie garantie. Le Bluetooth RFCOMM indépendant doit être vérifié sur la TicWatch. Ne réinitialisez pas la montre. Aucune configuration Wear OS initiale n'est remplacée. Pas d'audio d'appel, réponse, décrochage, santé, Android Auto, copie d'images ou commandes distantes. Les notifications sensibles peuvent être masquées par Android. Une alerte d'appel n'est envoyée que si l'application Téléphone publie une notification accessible, et est sélectionnée dans le filtre. Pas de démarrage automatique au boot. Pas de historique ni rattrapage hors connexion; les messages peuvent être perdus en cas de coupure, pas d'accusé réception applicatif. Les identifiants de notifications ne sont pas persistants : d'anciennes notifications peuvent rester après redémarrage du service. La liste des applications n'affiche que les applications à activité de lancement visibles; certains composants Téléphone peuvent ne pas y apparaître. Le service doit être démarré manuellement depuis l'écran de chaque application. Les droits sont contrôlés par le système; en cas de refus, les autoriser dans les paramètres et relancer.

## Compilation sur le PC
Requis : Android Studio avec SDK Android 35, build-tools correspondants, JDK 17 et Gradle 8.9. Internet nécessaire sur le PC pour télécharger Gradle/plugin/SDK. Les APK n'ont pas besoin d'Internet. Aucun SDK ou Gradle Wrapper binaire inclus. Importer le dossier dans Android Studio et choisir une distribution Gradle 8.9 locale, ou, avec Gradle 8.9 installé :

    export JAVA_HOME=/chemin/vers/jdk-17
    export ANDROID_HOME="$HOME/Android/Sdk"
    gradle :app:assemblePhoneDebug :app:assembleWatchDebug

APK obtenus : app/build/outputs/apk/phone/debug/app-phone-debug.apk et app/build/outputs/apk/watch/debug/app-watch-debug.apk. Ce sont des versions debug destinées aux essais, pas des releases. Le PC doit garder le même keystore debug pour les mises à jour.

## Installation
Téléphone USB : adb -s SERIAL_PHONE install -r app/build/outputs/apk/phone/debug/app-phone-debug.apk
Montre : activer son débogage ADB Wi-Fi, utiliser les adresses/ports affichés par la montre (selon Wear OS : adb pair IP:PORT puis adb connect IP:PORT), puis adb -s IP:PORT install -r app/build/outputs/apk/watch/debug/app-watch-debug.apk.
Désactiver le débogage Wi-Fi après installation et tests; la liaison applicative utilise exclusivement Bluetooth.

## Association et test
1. Installer les deux APK sans réinitialiser la montre. Ouvrir chaque application et autoriser Bluetooth et notifications. Android 11 utilise les anciennes autorisations Bluetooth à l'installation.
2. Si téléphone et montre ne sont pas associés au niveau Bluetooth système, rendre la montre visible 120 s et l'associer depuis les réglages Bluetooth du téléphone; confirmer le même code. La disponibilité de cette association indépendante est à vérifier sur la montre. Ne pas lancer un transfert/réinitialisation Wear OS.
3. Sur chaque application, « Choisir l'appareil déjà associé » : sélectionner l'autre appareil (filtre de sécurité obligatoire).
4. Démarrer d'abord le service sur la montre, puis sur le téléphone. Leur notification d'état doit indiquer Connecté. En cas de modification de l'appareil sélectionné, arrêter puis redémarrer le service.
5. Téléphone : envoyer la notification de test. Si elle arrive, autoriser l'accès aux notifications, puis sélectionner une seule application dans le filtre et tester un vrai message.
6. Tester écran éteint 15 min, puis téléphone en veille 1 h, coupure/reconnexion, et enfin comparer la batterie. Le service est persistant mais peut être arrêté par l'OS. Sur HyperOS, n'assouplir la batterie que pour WristRelay si nécessaire, pas globalement.
7. Bouton Arrêter sur les deux appareils pour arrêter la liaison. Pour supprimer l'accès aux messages, révoquer aussi l'accès aux notifications dans les réglages Android.

## Choix énergie
Aucun ping, scan périodique, animation ou allumage forcé de l'écran. Threads bloquants au repos. Socket persistante; pas d'ouverture Bluetooth par notification. Reconnexion téléphone après 5, 10, 20, 40, 80, 160 puis 300 secondes max; récepteur montre en attente bloquante. Pas de boucle de reconnexion rapide. Déduplication titre/texte des 200 dernières notifications, filtrage manuel (rien par défaut), exclusion résumés de groupe et notifications persistantes hors catégorie appel. Texte limité à 1200 caractères, file mémoire limitée à 64. Pas de persistance des contenus. Un service premier plan connectedDevice assure la visibilité de la liaison, pas une immunité aux restrictions de veille. Une socket persistante et des vibrations peuvent malgré tout coûter de la batterie; absence de wakelock peut affecter les délais en veille. BLE et CompanionDeviceManager sont des pistes ultérieures, pas implémentées.

## Mesure reproductible
Comparer deux journées ou périodes équivalentes (mêmes AOD, activité, réseau, volume de notifications) avec et sans service. Ne pas conclure depuis les seules premières minutes. Mesurer d'abord sans débogage Wi-Fi et sans chargeur. Pour investigation Android : adb shell dumpsys batterystats, adb logcat -s WristRelay. Les journaux du prototype indiquent les exceptions de liaison, pas le contenu des messages.

## Prochaines étapes avant usage quotidien
Tests matériel, protocole acquitté, nettoyage des notifications au redémarrage, prise en charge plus fiable des apps d'appel, gestion explicite des refus de permission, reconnexion événementielle via CompanionDeviceManager, OngoingActivity Wear OS, essais batterie et analyse de veille. Aucun pourcentage d'autonomie promis.

Sources techniques : documentation Android Bluetooth, NotificationListenerService, foreground services connectedDevice et guide Wear OS Conserve power and battery. Ce projet est une expérimentation indépendante, sans affiliation Xiaomi/Mobvoi.
