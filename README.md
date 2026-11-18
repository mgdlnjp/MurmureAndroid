# Murmure Android

**Tu parles. Murmure écrit.** — *You speak. Murmure writes.*

Dictée vocale Whisper 100 % locale pour Android, avec une bulle flottante qui insère le texte dans le champ actif.

- Une bulle orange apparaît dès qu'un champ de texte est actif (clavier ouvert), dans n'importe quelle app.
- Un tap : Murmure écoute (bulle rouge + notification « Murmure écoute… »). Un second tap : la voix est transcrite **sur le téléphone** avec [whisper.cpp](https://github.com/ggml-org/whisper.cpp) et le texte est inséré au curseur.
- Appui long pendant l'écoute : annulation. Enregistrement limité à 2 minutes (arrêt et transcription automatiques).
- Français et anglais (détection automatique restreinte FR/EN, jamais de traduction), ou langue fixe (plus rapide).
- App compagnon : configuration guidée des autorisations, choix du modèle, historique local (recherche, copie, suppression), réglages. Interface en français si le téléphone est en français, sinon en anglais.

Pas de clavier (IME) : Murmure fonctionne avec votre clavier habituel.

## Installation (APK)

1. Récupérer l'APK produit par EAS (`eas build -p android --profile preview`, lien de téléchargement fourni par EAS) et l'ouvrir sur le téléphone. Autoriser l'installation depuis cette source si Android le demande.
2. Ouvrir Murmure et suivre l'assistant :
   1. **Micro** (obligatoire).
   2. **Notifications** (Android 13+, recommandé : notification pendant l'écoute).
   3. **Affichage par-dessus les autres apps** (recommandé : aide Android à autoriser le micro depuis les autres apps).
   4. **Service d'accessibilité** (obligatoire) : Réglages › Accessibilité › Applications téléchargées › Murmure › activer.
   5. **Modèle Whisper** : Small (≈ 181 Mo, recommandé) ou Base (≈ 57 Mo, plus rapide). Privilégier le Wi-Fi. Le téléchargement reprend là où il s'est arrêté en cas de coupure.

### « Paramètres restreints » (Android 13+)

Android bloque l'activation de l'accessibilité pour les applications installées hors Play Store : l'interrupteur du service est grisé.

1. Essayer d'abord d'activer le service une fois (l'option suivante n'apparaît qu'après cette tentative).
2. Ouvrir **Infos sur l'appli** de Murmure › menu **⋮** (en haut à droite) › **Autoriser les paramètres restreints** (confirmer avec le code / l'empreinte).
3. Revenir dans Accessibilité et activer Murmure.

L'assistant propose un bouton « Le bouton est grisé ? » qui mène à cette procédure.

> Certains constructeurs (Xiaomi, Samsung…) désactivent le service d'accessibilité après une mise à jour de l'APK ou tuent les services en arrière-plan : il faut alors le réactiver (et éventuellement exclure Murmure de l'optimisation de batterie).

## Confidentialité

- La transcription est **entièrement locale** ; l'audio n'est jamais écrit sur le disque ni envoyé.
- La seule requête réseau est le téléchargement du modèle depuis Hugging Face (`ggerganov/whisper.cpp`), vérifié par taille et empreinte SHA-256.
- Le service d'accessibilité sert uniquement à détecter un champ de texte actif (pour afficher la bulle) et à y insérer la dictée. Le contenu des champs n'est lu qu'au moment de l'insertion, n'est ni stocké ni journalisé. La bulle n'apparaît jamais sur les champs mot de passe / sensibles, ni sur l'écran de verrouillage, ni dans Murmure.
- L'historique (500 dictées max) est stocké localement dans l'app ; il peut être vidé à tout moment.
- En cas de repli par presse-papiers, l'ancien contenu du presse-papiers n'est pas restauré (Android 10+ interdit sa lecture en arrière-plan).

## Limites connues

- **Micro en arrière-plan** (Android 11+, durci en 14+) : Murmure démarre un service au premier plan de type « micro » au moment du tap. Selon les constructeurs, Android peut tout de même couper le micro ; Murmure le détecte (audio entièrement nul) et affiche « Le micro a été coupé par Android… ». Accorder l'autorisation « Affichage par-dessus les autres apps » améliore la situation.
- **Performances** : Small q5_1 ≈ 300–400 Mo de RAM ; sur un téléphone milieu de gamme, compter plusieurs secondes pour 10 s d'audio. Base est nettement plus rapide. Fixer la langue (FR ou EN) évite la passe de détection. Temps mesurés : *à compléter lors des tests sur appareil*.
- **Insertion** : certaines apps (WebView, Compose, Flutter, jeux) n'acceptent pas l'insertion directe ; Murmure colle alors le texte, ou à défaut le laisse dans le presse-papiers (« Texte copié — colle-le avec un appui long »).
- Architecture : `arm64-v8a` uniquement.

## Développement

Stack : Expo SDK 58 (React Native, TypeScript, expo-router) + module natif local `modules/murmure-native` (Kotlin, Expo Modules API, JNI vers whisper.cpp v1.9.5 récupéré par CMake `FetchContent`). Le dossier `android/` est généré par `expo prebuild` et n'est pas versionné ; toute personnalisation native passe par `plugins/withMurmure.js` ou le module.

```bash
npm install
npm run typecheck        # tsc --noEmit
npm test                 # Jest (logique TS pure)
npm run prebuild         # expo prebuild --platform android --clean
```

### Build local

Java 25 (défaut macOS récent) est trop récent pour Gradle/AGP : utiliser le JBR (Java 21) d'Android Studio. Le NDK et CMake 3.22.1 sont téléchargés automatiquement par AGP si les licences du SDK sont acceptées (sinon : Android Studio › SDK Manager › SDK Tools). Le premier build télécharge whisper.cpp (réseau requis) et compile ggml (plusieurs minutes).

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
npm run prebuild
cd android
./gradlew :murmure-native:testDebugUnitTest   # tests JVM Kotlin
./gradlew assembleDebug                       # APK debug (arm64-v8a)
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep murmure_whisper
```

Le code natif whisper est toujours compilé en Release (même en debug), sinon ggml est ~10× plus lent.

### Build EAS (APK de test)

```bash
npx eas-cli@latest login
npx eas-cli@latest init          # ajoute le projectId (première fois)
npx eas-cli@latest build -p android --profile preview
```

Le profil `preview` produit un APK installable directement (distribution interne). Aucun secret n'est stocké dans le dépôt.

## Checklist de test manuel

- [ ] Installation de l'APK ; premier lancement → onboarding en FR (téléphone FR) / EN (téléphone EN).
- [ ] Micro accordé ; notifications (Android 13+) ; overlay ; Android 13+ : accessibilité d'abord grisée → procédure « paramètres restreints » ; service activé → l'app le détecte au retour.
- [ ] Téléchargement Small : progression, coupure réseau (mode avion) puis reprise sans repartir de zéro ; modèle marqué installé ; téléchargement et sélection de Base ; suppression.
- [ ] Messages / Gmail / Chrome (barre d'adresse et champ de page web) / WhatsApp / Keep : la bulle apparaît à l'ouverture du clavier, disparaît à sa fermeture (≤ ~0,5 s).
- [ ] Champ mot de passe (écran de connexion, Chrome) : pas de bulle.
- [ ] Tap → bulle rouge + notification « Murmure écoute… » ; phrase FR → texte inséré au curseur, texte existant conservé, espaces corrects ; idem en anglais (pas de traduction) ; FR puis EN successivement.
- [ ] Curseur au milieu d'un texte : insertion au bon endroit.
- [ ] App où l'insertion directe échoue : texte collé, ou toast « Texte copié » et collage manuel possible.
- [ ] Appui long pendant l'écoute : annulation. Tap très bref : rien. Silence : « Rien entendu ».
- [ ] Enregistrement de 2 min : arrêt et transcription automatiques.
- [ ] Bulle déplacée par glisser : ne prend pas le focus, ne ferme pas le clavier, position conservée.
- [ ] Réglages › bulle désactivée → plus de bulle ; réactivée → retour.
- [ ] Historique : chaque dictée (texte, date, durée), recherche sans accents (« ete » trouve « été »), copier, supprimer, tout effacer.
- [ ] Temps de transcription d'une phrase de ~10 s avec Small et Base (à reporter dans « Limites connues »).
- [ ] Android 14+ : pas de message « micro coupé » en dictant depuis une autre app.
- [ ] Mode avion après téléchargement : la dictée fonctionne.
- [ ] Écran verrouillé : pas de bulle.
