# Cahier des charges — Murmure Android (bulle flottante + Whisper local)

## Objectif
Livrer une app Android « Murmure » (Expo SDK 58 + TypeScript, module natif local Kotlin/JNI) dont un service d'accessibilité affiche une bulle flottante quand un champ texte est actif ; un tap enregistre, un second tap transcrit en local avec whisper.cpp et insère le texte dans le champ, avec une app compagnon (onboarding, modèles, historique, réglages) en FR/EN.

## Hypothèses
- **Versions vérifiées le 2026-11-17** : `expo` 58.0.26 (SDK 58, `latest`), `expo-router` 58.0.x, whisper.cpp **v1.9.5** (dernière release, 2026-10-06). Utiliser `npx expo install` pour aligner toutes les dépendances Expo/RN sur le SDK 58 (nouvelle architecture RN, activée par défaut).
- **Identifiants** : nom affiché `Murmure`, slug `murmure-android`, scheme `murmure`, `android.package` = `app.murmure.android`, package Kotlin du module = `app.murmure.core`, nom du module natif = `MurmureNative`, version `0.1.0`, `versionCode` 1.
- **Slogan / identité** reprise de l'app macOS : « Tu parles. Murmure écrit. » / « You speak. Murmure writes. » ; couleur d'accent `#F97316` (orange), accent foncé `#E2510C`, danger `#EF4444`, succès `#34BD75`. Langue de l'UI : FR si la première langue du téléphone commence par `fr`, sinon EN (même règle que `Localization.swift` du Mac, fonction `L(fr, en)`).
- **ABI** : `arm64-v8a` uniquement (tous les téléphones visés). Pas d'x86_64 (le testeur n'a pas d'émulateur garanti ; réduit fortement le temps de build NDK).
- **minSdk** : 26 (Android 8.0), fixé via `expo-build-properties` (`android.minSdkVersion: 26`) dans `app.config.ts` (cycle 2, R1). Les appels API 26+ (`NotificationChannel`, `Notification.Builder(ctx, id)`, `isShowingHintText`) restent protégés par `Build.VERSION.SDK_INT` par robustesse ; la règle lint `NewApi` est en erreur dans le module (`abortOnError true`).
- **Langue de dictée** : réglage `auto` (détection restreinte à FR/EN, jamais de traduction, comme sur Mac) par défaut, `fr` ou `en` en option (plus rapide : évite la passe de détection). Si la détection échoue, repli explicite sur `fr` (jamais de transcription dans une autre langue).
- **Durée max d'un enregistrement** : 120 s (arrêt + transcription automatiques). En dessous de 0,4 s : ignoré (pas de transcription).
- **Historique** : écrit par le code natif (le service tourne sans JS), fichier JSON local, 500 entrées max (les plus anciennes supprimées). Aucune donnée ne sort du téléphone (seule requête réseau : téléchargement du modèle depuis Hugging Face).
- **Bulle** masquée dans l'app Murmure elle-même, sur l'écran de verrouillage et sur tout champ mot de passe / sensible.
- **Presse-papiers** : en cas de repli par collage, l'ancien contenu n'est pas restauré (Android 10+ interdit la lecture du presse-papiers en arrière-plan ; restauration non fiable).
- **Fenêtre de la bulle** : `TYPE_ACCESSIBILITY_OVERLAY` (ne requiert pas `SYSTEM_ALERT_WINDOW`). La permission overlay est quand même déclarée et demandée en onboarding (étape « recommandée », non bloquante) car elle élargit les exemptions de démarrage de service au premier plan (voir risques).
- **Dossier `android/`** généré par `expo prebuild` (Continuous Native Generation) : **non versionné** (ajouté au `.gitignore`), toute personnalisation native passe par le config plugin ou le module.

## Analyse de l'existant
- Dépôt `/Users/mgdlnjp/Documents/PhoneApp/MurmureAndroid` quasi vide : `README.md` (2 lignes), `.gitignore` (node_modules, .expo, dist, *.apk, *.keystore, .env*, `*.bin`, `models/`…), `.pipeline/status.md`. Branche `feature/murmure-android-bulle`.
  - Attention : la règle `models/` du `.gitignore` ignorerait tout dossier nommé `models` ; ne **pas** nommer de dossier de code `models/` (utiliser `src/modelCatalog.ts`, `ModelManager.kt`, etc.).
- App macOS de référence `/Users/mgdlnjp/Documents/DesktopApp/Murmure` (Swift) : `App/Localization.swift` (`L(fr,en)`), `Models/Models.swift` (`HistoryEntry { id, date, appName, raw, final, duration }`, `wordCount`), `Transcription/Transcriber.swift` (détection de langue restreinte aux langues attendues en prenant le max des probabilités, puis transcription forcée dans cette langue), `UI/DesignSystem.swift` (couleurs ci-dessus). Reprendre ces concepts, pas le code.
- Environnement local (vérifié) : Node 23.11, Java par défaut **25** (trop récent pour Gradle/AGP → utiliser le JBR d'Android Studio, Java 21 : `/Applications/Android Studio.app/Contents/jbr/Contents/Home`), SDK Android `~/Library/Android/sdk` (platform android-36, build-tools 36, licences acceptées), **pas de NDK ni de CMake installés** (AGP les télécharge automatiquement au premier `assembleDebug` si licences acceptées ; sinon les installer via Android Studio > SDK Manager), pas de `cmdline-tools`, `ANDROID_HOME` non défini, pas d'`adb` dans le PATH.
- Modèles (vérifiés sur Hugging Face `ggerganov/whisper.cpp`, `resolve/main`) :

| id | fichier | taille (octets) | sha256 |
|---|---|---|---|
| `small-q5_1` (défaut) | `ggml-small-q5_1.bin` | 190085487 | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` |
| `base-q5_1` | `ggml-base-q5_1.bin` | 59707625 | `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898` |

  URL : `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/<fichier>` (redirection 302 vers un CDN, suivre les redirections ; supporte `Range`).
- whisper.cpp v1.9.5 : archive `https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.9.5.tar.gz`, sha256 `ff1a9053feb509ff9d7729703355541ae9690073a6b1c40eb692c962e0dc1720`. API utilisée (vérifiée dans `include/whisper.h`) : `whisper_context_default_params`, `whisper_init_from_file_with_params`, `whisper_pcm_to_mel`, `whisper_lang_auto_detect(ctx, offset_ms, n_threads, float* probs)` (tableau de taille `whisper_lang_max_id()+1`), `whisper_lang_id("fr")`, `whisper_full_default_params(WHISPER_SAMPLING_GREEDY)`, `whisper_full`, `whisper_full_n_segments`, `whisper_full_get_segment_text`, `whisper_free`. Exemple de référence pour les flags Android : `examples/whisper.android/lib/src/main/jni/whisper/CMakeLists.txt` dans l'archive.

## Stratégie
Livraison en 5 étapes, chacune doit compiler et passer ses tests avant la suivante.

**Étape 1 — Socle Expo + module + plugin (vérifiable sans appareil)**
1. Générer le projet Expo SDK 58 TypeScript dans un dossier temporaire (`npx create-expo-app@latest <tmp> --template default@sdk-58`), puis copier dans le dépôt sans écraser `.git`, `.pipeline`, `README.md`, `.gitignore` ; supprimer le contenu de démo (écrans exemples, composants inutilisés, assets inutiles). Ou partir de `blank-typescript@sdk-58` + `npx expo install expo-router` : au choix, résultat attendu identique (structure `app/` ci-dessous).
2. `npx expo install expo-localization expo-build-properties` ; dev : `jest-expo jest @types/jest`. Pas d'autre dépendance (pas d'AsyncStorage : réglages et historique passent par le module natif).
3. Créer le module local `modules/murmure-native` (`npx create-expo-module@latest --local`, nom `murmure-native`, puis retirer iOS/web : `expo-module.config.json` → `platforms: ["android"]`).
4. Écrire le config plugin `plugins/withMurmure.js` et le référencer dans `app.config.ts`.
5. Compléter `.gitignore` : `android/`, `ios/`, `coverage/`.
6. `npx expo prebuild --platform android --clean` doit réussir et le manifeste fusionné contenir permissions + service.

**Étape 2 — Moteur natif (whisper.cpp JNI + modèles)**
1. CMake : récupérer whisper.cpp v1.9.5 par `FetchContent` (URL + `URL_HASH SHA256=…` ci-dessus), `add_subdirectory` avec options désactivant exemples/tests/serveur, lib statique liée dans `libmurmure_whisper.so` avec le fichier JNI.
2. `ModelManager` (téléchargement avec reprise `Range`, vérification taille + sha256, suppression), `WhisperEngine` (chargement paresseux, transcription sur un thread dédié).
3. Tests unitaires JVM des parties pures ; `./gradlew assembleDebug` doit réussir.

**Étape 3 — Service d'accessibilité, bulle, micro, insertion**
1. `MurmureAccessibilityService` : suivi du champ focalisé et de la fenêtre IME → `BubbleVisibility.decide()` → affiche/masque `BubbleOverlay`.
2. `AudioRecorder` 16 kHz mono float, `startForeground(type MICROPHONE)` pendant l'enregistrement, détection de micro « silencié ».
3. `TextInserter` : `ACTION_SET_TEXT` (fusion au curseur via `TextMerge`) → vérification → repli presse-papiers + `ACTION_PASTE` → repli presse-papiers + toast.
4. Écriture de l'historique (`HistoryStore`).

**Étape 4 — App compagnon**
Onboarding des autorisations, écran modèle (choix + téléchargement avec progression), accueil/état, historique (recherche, suppression, copie), réglages. i18n FR/EN. Rafraîchissement des états au retour au premier plan (`AppState` → `active`).

**Étape 5 — Build EAS + doc**
`eas.json` (profil `preview`, APK), README (installation APK, paramètres restreints, build local), checklist de test manuel.

## Fichiers

| chemin | action | rôle |
|---|---|---|
| `package.json` | créer | scripts `start`, `android` (`expo run:android`), `prebuild` (`expo prebuild --platform android --clean`), `typecheck` (`tsc --noEmit`), `test` (`jest`), `lint` optionnel ; `"jest": { "preset": "jest-expo" }` ou `jest.config.js` |
| `app.config.ts` | créer | config Expo (nom, slug, scheme, version, `android.package`, icônes, `plugins: ["expo-router", "expo-localization", ["expo-build-properties", {...}], "./plugins/withMurmure.js"]`). Pas de `app.json` en parallèle |
| `tsconfig.json` | créer | `extends: "expo/tsconfig.base"`, `strict: true`, alias `@/*` → `./*` |
| `eas.json` | créer | profils `development` (optionnel), `preview` (`android.buildType: "apk"`, `distribution: "internal"`), `production` (non utilisé) ; `cli.appVersionSource: "local"`. Aucun secret, pas de `projectId` en dur (ajouté par `eas init` lors du premier build) |
| `.gitignore` | modifier | ajouter `android/`, `ios/`, `coverage/` |
| `README.md` | modifier | présentation (slogan), installation APK, autorisations et « paramètres restreints », build EAS et local (JAVA_HOME JBR 21), confidentialité |
| `plugins/withMurmure.js` | créer | config plugin (CommonJS) : permissions, `<service>` d'accessibilité, `reactNativeArchitectures=arm64-v8a` dans `gradle.properties` |
| `app/_layout.tsx` | créer | `Stack` racine ; redirige vers `/onboarding` si `onboardingDone == false` |
| `app/(tabs)/_layout.tsx` | créer | onglets Accueil / Historique / Réglages (libellés i18n) |
| `app/(tabs)/index.tsx` | créer | Accueil : slogan, état (service actif, modèle prêt, bulle activée), raccourcis vers onboarding / modèle |
| `app/(tabs)/history.tsx` | créer | liste des dictées, champ de recherche, copier, supprimer, tout effacer (avec confirmation) |
| `app/(tabs)/settings.tsx` | créer | interrupteur bulle, langue de dictée, lien modèle, lien autorisations, à propos |
| `app/onboarding.tsx` | créer | assistant pas à pas (étapes ci-dessous) |
| `app/model.tsx` | créer | choix Small/Base, télécharger / annuler / reprendre / supprimer, progression |
| `src/i18n.ts` | créer | `isFrench`, `L(fr, en)`, dictionnaire de chaînes UI |
| `src/format.ts` | créer | `formatBytes`, `formatDuration`, `formatDate` (locale FR/EN) |
| `src/history.ts` | créer | `filterHistory`, `normalizeForSearch`, `wordCount` |
| `src/onboarding.ts` | créer | `ONBOARDING_STEPS`, `nextOnboardingStep(status)`, `isReady(status)` |
| `src/useNativeState.ts` | créer | hooks `usePermissionStatus()`, `useModels()`, `useSettings()`, `useHistory()` (rafraîchis sur `AppState active` et sur événements natifs) |
| `src/theme.ts` | créer | couleurs (accent orange…), espacements ; mode clair/sombre via `useColorScheme` |
| `src/components/*.tsx` | créer | `StepCard`, `PrimaryButton`, `ProgressBar`, `StatusRow` (minimal) |
| `__tests__/i18n.test.ts`, `__tests__/format.test.ts`, `__tests__/history.test.ts`, `__tests__/onboarding.test.ts` | créer | tests Jest de la logique pure |
| `jest.setup.ts` | créer | mock de `modules/murmure-native` (`jest.mock`) |
| `modules/murmure-native/expo-module.config.json` | créer | `{ "platforms": ["android"], "android": { "modules": ["app.murmure.core.MurmureNativeModule"] } }` |
| `modules/murmure-native/index.ts` | créer | ré-exporte l'API typée |
| `modules/murmure-native/src/MurmureNative.types.ts` | créer | types TS (voir Interfaces) |
| `modules/murmure-native/src/MurmureNativeModule.ts` | créer | `requireNativeModule<...>('MurmureNative')` |
| `modules/murmure-native/android/build.gradle` | créer | lib Android Expo ; `externalNativeBuild.cmake` (path `src/main/cpp/CMakeLists.txt`, version `3.22.1`) ; `ndkVersion = rootProject.ext.ndkVersion` si défini ; `defaultConfig.ndk.abiFilters "arm64-v8a"` ; `externalNativeBuild.cmake.arguments "-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_shared"` **même en debug** (ggml en Debug est ~10× plus lent) ; `testImplementation "junit:junit:4.13.2"` et `"org.json:json:20240303"` (org.json Android est un stub en tests JVM) |
| `modules/murmure-native/android/src/main/AndroidManifest.xml` | créer | vide (pas de déclaration ici : tout via le plugin) |
| `modules/murmure-native/android/src/main/cpp/CMakeLists.txt` | créer | FetchContent whisper.cpp v1.9.5 + cible `murmure_whisper` |
| `modules/murmure-native/android/src/main/cpp/murmure_jni.cpp` | créer | fonctions JNI (voir Interfaces) |
| `modules/murmure-native/android/src/main/java/app/murmure/core/MurmureNativeModule.kt` | créer | API JS (Expo Modules API) |
| `.../core/MurmureAccessibilityService.kt` | créer | service d'accessibilité, orchestration |
| `.../core/BubbleOverlay.kt` | créer | vue de la bulle, drag, états visuels |
| `.../core/BubbleVisibility.kt` | créer | logique pure de visibilité |
| `.../core/FieldInfo.kt` | créer | data class extraite d'`AccessibilityNodeInfo` + `isSensitive()` pur |
| `.../core/AudioRecorder.kt` | créer | capture `AudioRecord` 16 kHz mono |
| `.../core/AudioUtils.kt` | créer | pur : `pcm16ToFloat`, `rms`, `isSilenced`, `durationSec` |
| `.../core/WhisperBridge.kt` | créer | `external fun` + `System.loadLibrary("murmure_whisper")` |
| `.../core/WhisperEngine.kt` | créer | singleton, chargement/libération du modèle, file de transcription |
| `.../core/TextMerge.kt` | créer | pur : fusion du texte au curseur, espaces |
| `.../core/TextCleanup.kt` | créer | pur : nettoyage sortie Whisper |
| `.../core/TextInserter.kt` | créer | stratégie d'insertion + replis |
| `.../core/ModelCatalog.kt` | créer | catalogue (table ci-dessus) |
| `.../core/ModelManager.kt` | créer | téléchargement avec reprise, sha256, état |
| `.../core/HistoryStore.kt` | créer | lecture/écriture `history.json` (sérialisation pure testable) |
| `.../core/MurmurePrefs.kt` | créer | SharedPreferences `murmure_prefs` |
| `.../core/PermissionChecker.kt` | créer | statut des autorisations + intents de réglages |
| `modules/murmure-native/android/src/main/res/xml/murmure_accessibility_service.xml` | créer | config du service |
| `modules/murmure-native/android/src/main/res/values/strings.xml` et `values-fr/strings.xml` | créer | libellé/description du service, toasts, notification, contentDescription bulle |
| `modules/murmure-native/android/src/main/res/drawable/murmure_bubble_*.xml` | créer | fond rond orange, icône micro, stop, (vector drawables) |
| `modules/murmure-native/android/src/test/java/app/murmure/core/*Test.kt` | créer | `TextMergeTest`, `TextCleanupTest`, `BubbleVisibilityTest`, `FieldInfoTest`, `AudioUtilsTest`, `ModelCatalogTest`, `ModelManagerLogicTest`, `HistoryStoreTest` |

## Interfaces

### Config plugin `plugins/withMurmure.js`
`module.exports = function withMurmure(config) { … }` utilisant `withAndroidManifest` et `withGradleProperties` de `expo/config-plugins` (`@expo/config-plugins`). Idempotent (pas de doublon si prebuild relancé).
- `<uses-permission>` : `RECORD_AUDIO`, `INTERNET`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`.
- Dans `<application>` :
```xml
<service android:name="app.murmure.core.MurmureAccessibilityService"
    android:exported="true"
    android:label="@string/murmure_accessibility_label"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:foregroundServiceType="microphone">
  <intent-filter><action android:name="android.accessibilityservice.AccessibilityService"/></intent-filter>
  <meta-data android:name="android.accessibilityservice" android:resource="@xml/murmure_accessibility_service"/>
</service>
```
- `gradle.properties` : `reactNativeArchitectures=arm64-v8a`.
- `expo-build-properties` (dans `app.config.ts`) : rien d'obligatoire ; ne pas l'ajouter si inutile.

### `res/xml/murmure_accessibility_service.xml`
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
  android:accessibilityEventTypes="typeViewFocused|typeViewClicked|typeViewTextSelectionChanged|typeWindowStateChanged|typeWindowsChanged"
  android:accessibilityFeedbackType="feedbackGeneric"
  android:accessibilityFlags="flagRetrieveInteractiveWindows|flagReportViewIds"
  android:canRetrieveWindowContent="true"
  android:notificationTimeout="100"
  android:isAccessibilityTool="false"
  android:description="@string/murmure_accessibility_description"
  android:summary="@string/murmure_accessibility_summary" />
```
La description doit expliquer honnêtement l'usage (détecter un champ de texte actif pour afficher la bulle et y insérer le texte dicté ; aucune donnée lue n'est stockée ni envoyée).

### JNI (`murmure_jni.cpp` ↔ `WhisperBridge.kt`)
```kotlin
object WhisperBridge {
  init { System.loadLibrary("murmure_whisper") }
  external fun nativeInit(modelPath: String): Long          // 0 si échec
  external fun nativeTranscribe(ctx: Long, samples: FloatArray, language: String, nThreads: Int): String
      // language: "auto" (détection restreinte fr/en), "fr", "en". Lève RuntimeException si whisper_full != 0
  external fun nativeFree(ctx: Long)
  external fun nativeSystemInfo(): String                    // whisper_print_system_info(), pour debug
}
```
Côté C++ pour `"auto"` : `whisper_pcm_to_mel` → `whisper_lang_auto_detect(ctx, 0, n, probs)` → choisir `fr` si `probs[id("fr")] >= probs[id("en")]` sinon `en` → `whisper_full` avec `params.language` = choix, `detect_language=false`, `translate=false`, `print_*=false`, `no_timestamps=true`, `single_segment=false`, `n_threads`. Concaténer les segments. Contexte : `use_gpu=false`. Pas d'état global C++ mutable hors `ctx`.

CMake (`src/main/cpp/CMakeLists.txt`) : `cmake_minimum_required(VERSION 3.22)` ; `FetchContent_Declare(whisper URL …v1.9.5.tar.gz URL_HASH SHA256=ff1a9053… )` ; avant `FetchContent_MakeAvailable` : `BUILD_SHARED_LIBS OFF`, `WHISPER_BUILD_EXAMPLES OFF`, `WHISPER_BUILD_TESTS OFF`, `WHISPER_BUILD_SERVER OFF`, `GGML_NATIVE OFF`, `GGML_OPENMP OFF` (en `CACHE BOOL "" FORCE`) ; `add_library(murmure_whisper SHARED murmure_jni.cpp)` ; `target_link_libraries(murmure_whisper whisper log android)` ; options Release `-O3`, `-ffunction-sections -fdata-sections`, `-Wl,--gc-sections` ; **`-Wl,-z,max-page-size=16384`** (pages 16 Ko, Android 15+). Base `armv8-a` (pas de `+fp16`/`dotprod` forcé : risque SIGILL sur vieux SoC ; optimisation par variantes = amélioration ultérieure).

### `WhisperEngine.kt`
```kotlin
object WhisperEngine {
  fun ensureLoaded(context: Context): Boolean        // charge le modèle sélectionné s'il est installé ; recharge si le modèle sélectionné a changé
  fun transcribe(context: Context, samples: FloatArray, language: String, callback: (Result<String>) -> Unit) // exécuteur mono-thread dédié ; callback sur le main thread
  fun release()                                       // nativeFree ; appelé sur onTrimMemory(RUNNING_CRITICAL/COMPLETE), onUnbind, changement de modèle
  val isBusy: Boolean
}
```
`nThreads = min(4, max(1, Runtime.availableProcessors() - 1))`. Préchargement (`ensureLoaded`) en arrière-plan à la première apparition de la bulle.

### Logique pure Kotlin (testée en JVM, sans dépendance Android)
```kotlin
data class FieldInfo(val packageName: String?, val isEditable: Boolean, val isPassword: Boolean, val inputType: Int,
                     val text: String?, val isShowingHint: Boolean, val selectionStart: Int, val selectionEnd: Int)
fun FieldInfo.isSensitive(): Boolean
  // isPassword || variation de inputType ∈ {TEXT_PASSWORD(0x80), TEXT_VISIBLE_PASSWORD(0x90), TEXT_WEB_PASSWORD(0xE0), NUMBER_PASSWORD(0x12 avec classe NUMBER)}
  // constantes en dur (valeurs d'InputType) pour rester testable en JVM

data class VisibilityInput(val bubbleEnabled: Boolean, val modelReady: Boolean, val keyguardLocked: Boolean,
                           val imeVisible: Boolean, val focused: FieldInfo?, val ownPackage: String, val busy: Boolean)
object BubbleVisibility { fun decide(i: VisibilityInput): Boolean }
  // busy (enregistrement/transcription) → true (ne jamais masquer en cours de dictée)
  // sinon false si !bubbleEnabled || keyguardLocked || focused?.packageName == ownPackage || focused?.isSensitive() == true
  // sinon true si (focused?.isEditable == true) || imeVisible ; modelReady=false → true quand même (le tap affichera « modèle à télécharger »)

data class MergeResult(val text: String, val cursor: Int)
object TextMerge { fun merge(existing: String?, isShowingHint: Boolean, selStart: Int, selEnd: Int, insert: String): MergeResult }
  // existing nul ou hint affiché → base "" ; sélection invalide (-1, hors bornes) → fin du texte ; sélection non vide → remplacée
  // espace ajouté avant si le caractère précédent existe, n'est pas un blanc et que insert ne commence pas par une ponctuation [.,;:!?)…]
  // espace ajouté après si le caractère suivant existe et n'est pas un blanc ni une ponctuation
  // cursor = position juste après le texte inséré (espace final inclus)

object TextCleanup { fun clean(raw: String): String }
  // trim ; espaces multiples → un ; supprime les marqueurs Whisper du type "[BLANK_AUDIO]", "[Musique]", "(musique)", "[inaudible]" ; résultat vide si rien d'autre

object AudioUtils {
  fun pcm16ToFloat(src: ShortArray, n: Int): FloatArray
  fun rms(samples: FloatArray): Float
  fun isSilenced(samples: FloatArray): Boolean   // tous les échantillons == 0f (micro coupé par le système)
  fun durationSec(sampleCount: Int, rate: Int = 16000): Double
}
```

### `AudioRecorder.kt`
`start(): Boolean` (AudioRecord `MIC` ou `VOICE_RECOGNITION`, 16000 Hz, mono, `ENCODING_PCM_16BIT`, thread de lecture, buffer croissant), `stop(): FloatArray`, `cancel()`, `isRecording`. Auto-stop à 120 s via callback `onLimitReached`. Retourne `false` si `SecurityException` / état non initialisé.

### `TextInserter.kt`
```kotlin
enum class InsertOutcome { SET_TEXT, PASTED, CLIPBOARD_ONLY, NOTHING }
fun insert(service: AccessibilityService, target: AccessibilityNodeInfo?, text: String): InsertOutcome
```
1. Si `text` vide → `NOTHING`. Si cible absente/obsolète (`refresh()` false) → re-chercher `rootInActiveWindow.findFocus(FOCUS_INPUT)`. Si la cible est sensible : ni insertion ni presse-papiers, toast « Champ protégé », `NOTHING` (l'entrée d'historique est quand même enregistrée).
2. `ACTION_SET_TEXT` avec `TextMerge.merge(...)`, puis `ACTION_SET_SELECTION` (start=end=cursor). Vérifier après `refresh()` que le texte du nœud contient `text` ; si oui → `SET_TEXT`.
3. Sinon : presse-papiers (`ClipData` avec label « Murmure », marqué sensible : `ClipDescription.EXTRA_IS_SENSITIVE=true` en API 33+, clé `android.content.extra.IS_SENSITIVE` avant) + `ACTION_PASTE` ; si l'action retourne true → `PASTED`. Vérification de SET_TEXT (cycle 2) : contenu inchangé → échec (collage) ; contient le texte → `SET_TEXT` ; modifié sans le contenir → `SET_TEXT` probable, sans collage ni copie silencieuse.
4. Sinon : texte laissé dans le presse-papiers + toast « Texte copié — colle-le avec un appui long » → `CLIPBOARD_ONLY`.

### `MurmureAccessibilityService.kt`
- `onServiceConnected` : init prefs, `BubbleOverlay`, écoute des changements de prefs (`OnSharedPreferenceChangeListener`), `MurmureNativeModule` notifié (statut).
- `onAccessibilityEvent` : sur les types configurés, recalculer (débounce 150 ms pour masquer, affichage immédiat) : `imeVisible = windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }` ; `focused = findFocus(FOCUS_INPUT)` → `FieldInfo` ; mémoriser le nœud cible (copie) quand il est éditable et non sensible. Positionner la bulle au-dessus de la fenêtre IME (bornes via `getBoundsInScreen`) si l'utilisateur ne l'a pas déplacée.
- Machine d'états : `IDLE → RECORDING → TRANSCRIBING → IDLE` (+ `ERROR` transitoire 1,5 s).
  - tap en `IDLE` : si modèle absent → toast + ouvre l'app (`/model`) ; si `RECORD_AUDIO` refusée → toast + ouvre l'app (`/onboarding`) ; sinon `startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)` (API 29+ avec type ; try/catch `ForegroundServiceStartNotAllowedException`/`SecurityException` → continuer sans FGS et journaliser) puis `AudioRecorder.start()` ; retour haptique léger.
  - tap en `RECORDING` : `stop()` ; `stopForeground(STOP_FOREGROUND_REMOVE)` ; si < 0,4 s → IDLE ; si `isSilenced` → toast « Le micro a été coupé par Android… » → IDLE ; sinon `TRANSCRIBING` → `WhisperEngine.transcribe` → `TextCleanup.clean` → `TextInserter.insert` → `HistoryStore.add` → émettre `onHistoryChanged` → IDLE.
  - appui long en `RECORDING` : annuler (pas de transcription). Tap en `TRANSCRIBING` : ignoré.
- `onUnbind`/`onDestroy` : retirer la vue, arrêter l'enregistrement, `WhisperEngine.release()`. `onInterrupt` ne nettoie rien (déclenchable par n'importe quelle app via `AccessibilityManager.interrupt()`).
- Notification FGS : canal `murmure_recording` (importance LOW), texte « Murmure écoute… », icône petite monochrome.

### `BubbleOverlay.kt`
`WindowManager.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, TYPE_ACCESSIBILITY_OVERLAY, FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_NO_LIMITS, TRANSLUCENT)`, gravity TOP|START. Diamètre 56 dp, ombre. Ne prend **jamais** le focus (sinon le clavier se ferme). Drag : déplacement > 10 px = drag (pas de tap) ; position sauvegardée en fractions d'écran dans les prefs (`bubble_x`, `bubble_y`), bornée à l'écran. États visuels : idle (orange + micro), recording (rouge + stop + pulsation), transcribing (orange + `ProgressBar` indéterminée), error (rouge, 1,5 s). `contentDescription` localisée. API : `show()`, `hide()`, `setState(state)`, `placeAboveKeyboard(imeTop: Int?)`.

### `ModelManager.kt` / `ModelCatalog.kt`
```kotlin
data class ModelSpec(val id: String, val fileName: String, val sizeBytes: Long, val sha256: String, val labelFr: String, val labelEn: String)
object ModelCatalog { val all: List<ModelSpec>; const val DEFAULT_ID = "small-q5_1"; fun byId(id: String): ModelSpec?; fun url(spec: ModelSpec): String }
object ModelManager {
  fun dir(context: Context): File                         // filesDir/whisper-models
  fun installedFile(context: Context, spec: ModelSpec): File   // <dir>/<fileName>
  fun partFile(context: Context, spec: ModelSpec): File        // <dir>/<fileName>.part
  fun isInstalled(context: Context, spec: ModelSpec): Boolean  // fichier final présent ET taille == sizeBytes
  fun download(context: Context, spec: ModelSpec, onProgress: (Long, Long) -> Unit): Unit  // bloquant, sur thread dédié ; lève en cas d'erreur
  fun cancel(id: String); fun delete(context: Context, id: String)
  // pur, testable : fun rangeHeaderFor(partSize: Long, total: Long): String?  ("bytes=N-" ; null si 0 ; si partSize > total → supprimer .part, null)
  // pur, testable : fun sha256Hex(input: InputStream): String
}
```
`HttpURLConnection` (suivre les redirections, y compris 302 cross-host : boucle manuelle max 5), timeouts connexion 15 s / lecture 30 s, `User-Agent` générique (`Murmure-Android/0.1`), en-tête `Range` si `.part` existe ; si réponse 200 alors qu'on demandait une plage → repartir de zéro ; 206 → append. Progression émise au plus toutes les 250 ms. Fin : taille == `sizeBytes` puis sha256 == attendu → `renameTo` final ; sinon supprimer `.part` et lever `ModelChecksumException`. Vérifier l'espace libre (`dir.usableSpace >= restant + 10 Mo`) avant de commencer → `INSUFFICIENT_STORAGE`. Un seul téléchargement à la fois.

### `HistoryStore.kt`
Fichier `filesDir/history.json` : tableau JSON, plus récent en premier. Entrée :
`{ "id": UUID, "date": epochMillis, "text": String, "raw": String, "durationSec": Double, "language": "fr"|"en"|null, "appPackage": String|null, "outcome": "SET_TEXT"|"PASTED"|"CLIPBOARD_ONLY"|"NOTHING", "modelId": String }`.
Fonctions pures testables `toJson(list): String`, `fromJson(s): List<HistoryItem>` (JSON invalide → liste vide, fichier renommé `.corrupt`). `add` (cap 500), `delete(id)`, `clear()` (supprime aussi `.corrupt`), `all()`. Écriture atomique (fichier temporaire + rename), accès `synchronized`. Le texte n'est jamais journalisé dans logcat.
Le champ `language` est rempli via `external fun nativeLastLanguage(ctx: Long): String` (langue utilisée par le dernier `nativeTranscribe`, `""` si inconnue → `null`) ; à ajouter à `WhisperBridge`.

### `MurmurePrefs.kt` (SharedPreferences `murmure_prefs`)
| clé | type | défaut |
|---|---|---|
| `bubble_enabled` | Boolean | true |
| `model_id` | String | `small-q5_1` |
| `language` | String (`auto`/`fr`/`en`) | `auto` |
| `onboarding_done` | Boolean | false |
| `bubble_x`, `bubble_y` | Float (fraction 0–1) ou absent | absent |

### Module JS `MurmureNative` (Expo Modules API → `src/MurmureNative.types.ts`)
```ts
export type PermissionStatus = {
  microphone: boolean;            // RECORD_AUDIO accordée
  overlay: boolean;               // Settings.canDrawOverlays
  accessibility: boolean;         // service activé (Settings.Secure ENABLED_ACCESSIBILITY_SERVICES contient notre composant)
  notifications: boolean;         // API < 33 → true
  sdkInt: number;                 // pour l'explication « paramètres restreints » (>= 33)
};
export type ModelId = 'small-q5_1' | 'base-q5_1';
export type ModelInfo = {
  id: ModelId; labelFr: string; labelEn: string; sizeBytes: number;
  installed: boolean; partialBytes: number; selected: boolean; downloading: boolean;
};
export type DictationLanguage = 'auto' | 'fr' | 'en';
export type Settings = { bubbleEnabled: boolean; modelId: ModelId; language: DictationLanguage; onboardingDone: boolean };
export type HistoryItem = {
  id: string; date: number; text: string; raw: string; durationSec: number;
  language: 'fr' | 'en' | null; appPackage: string | null;
  outcome: 'SET_TEXT' | 'PASTED' | 'CLIPBOARD_ONLY' | 'NOTHING'; modelId: string;
};
export type DownloadProgressEvent = { modelId: ModelId; bytes: number; total: number };
export type DownloadEndEvent = { modelId: ModelId; ok: boolean; error?: 'NETWORK' | 'CHECKSUM' | 'INSUFFICIENT_STORAGE' | 'CANCELLED' | 'UNKNOWN' };

export interface MurmureNativeModuleType {
  getPermissionStatus(): PermissionStatus;               // Function synchrone
  openAccessibilitySettings(): void;                     // ACTION_ACCESSIBILITY_SETTINGS
  openOverlaySettings(): void;                           // ACTION_MANAGE_OVERLAY_PERMISSION + package:URI
  openAppDetailsSettings(): void;                        // ACTION_APPLICATION_DETAILS_SETTINGS (paramètres restreints)
  getSettings(): Settings;
  setSettings(patch: Partial<Settings>): Settings;
  getModels(): ModelInfo[];
  downloadModel(id: ModelId): Promise<void>;             // résout à la fin (ok) ; rejette avec code d'erreur ; émet onDownloadProgress / onDownloadEnd
  cancelDownload(id: ModelId): void;
  deleteModel(id: ModelId): void;                        // toujours autorisé ; si c'était le modèle sélectionné, l'app affiche « modèle requis »
  getHistory(): HistoryItem[];
  deleteHistoryItem(id: string): void;
  clearHistory(): void;
  copyToClipboard(text: string): void;
  // événements : 'onDownloadProgress', 'onDownloadEnd', 'onHistoryChanged', 'onSettingsChanged'
}
```
La permission micro et les notifications se demandent côté JS avec `PermissionsAndroid` de React Native (pas de dépendance supplémentaire). Le module tient une référence faible vers son instance pour que le service puisse émettre `onHistoryChanged` si l'app est ouverte.

### Logique TS pure (`src/`)
```ts
// i18n.ts
export const isFrench: boolean;                       // getLocales()[0]?.languageCode === 'fr' (expo-localization)
export function L(fr: string, en: string): string;
export function pickLanguage(languageCode: string | null | undefined): 'fr' | 'en'; // testable
// format.ts
export function formatBytes(n: number, lang: 'fr' | 'en'): string;   // 190085487 → "181 Mo" / "181 MB" (base 1024, 0 décimale ≥ 10, 1 décimale sinon)
export function formatDuration(sec: number): string;                  // 75.4 → "1:15", 4.2 → "0:04"
export function formatDate(ms: number, lang: 'fr' | 'en', now?: number): string; // "Aujourd'hui 14:05" / "Today 2:05 PM", sinon date courte
// history.ts
export function normalizeForSearch(s: string): string;                // minuscules + suppression des accents (NFD) + espaces normalisés
export function filterHistory(items: HistoryItem[], query: string): HistoryItem[]; // requête vide → tout ; tous les mots doivent apparaître ; insensible casse/accents
export function wordCount(s: string): number;
// onboarding.ts
export type StepId = 'welcome' | 'microphone' | 'notifications' | 'overlay' | 'restricted' | 'accessibility' | 'model' | 'done';
export const ONBOARDING_STEPS: StepId[];
export function nextOnboardingStep(s: PermissionStatus, modelInstalled: boolean, seenWelcome: boolean): StepId;
   // ordre : welcome → microphone → notifications (si sdkInt>=33 et non accordée ; passable) → overlay (passable)
   //       → restricted (si sdkInt>=33 et !accessibility, écran explicatif, passable) → accessibility → model → done
export function isReady(s: PermissionStatus, modelInstalled: boolean): boolean; // microphone && accessibility && modelInstalled
```

### Onboarding (`app/onboarding.tsx`)
Une carte par étape, bouton principal + « Passer » pour les étapes non bloquantes, état relu à chaque `AppState active`.
- **Restricted** (Android 13+) : texte FR/EN : « Android bloque l'accessibilité pour les apps installées hors Play Store. Ouvre Infos sur l'appli › menu ⋮ › Autoriser les paramètres restreints, puis reviens ici. » Bouton → `openAppDetailsSettings()`. Précise que l'option n'apparaît qu'après une première tentative d'activation du service (donc : essayer d'abord l'étape accessibilité, revenir ici si grisé). Implémentation : l'étape accessibilité affiche un lien « Le bouton est grisé ? » vers cette explication.
- **Accessibility** : explication de ce que lit le service + bouton → `openAccessibilitySettings()` (« Applications téléchargées › Murmure »).
- **Model** : intégrer le composant de `app/model.tsx` (Small recommandé, Base plus léger/rapide, avertissement données mobiles).
- **Done** : `setSettings({ onboardingDone: true })`, aller aux onglets.

## Cas limites et risques
**Risques majeurs (à documenter dans le README et tester en priorité sur appareil)**
1. **Micro en arrière-plan (Android 11+, durci en 14+)** : l'accès micro « pendant l'utilisation » exige d'être au premier plan ou un FGS de type `microphone` démarré depuis un contexte autorisé. Un service d'accessibilité lié par le système et une interaction directe sur sa fenêtre devraient le permettre, mais ce n'est pas garanti selon les constructeurs. Mitigations : `startForeground(…MICROPHONE)` dans le service d'accessibilité lui-même au moment du tap ; permission overlay accordée (exemption supplémentaire) ; détection des tampons entièrement nuls (`isSilenced`) → message clair. Repli possible en itération suivante (hors périmètre de ce cycle) : activité transparente « trampoline » — déconseillée car elle volerait le focus du champ.
2. **Politique accessibilité** : Google Play restreint l'usage de l'API ; sans publication Play, sans impact, mais la description doit rester honnête et `isAccessibilityTool=false`. Android 13+ « paramètres restreints » pour APK sideloadé : géré par l'onboarding. Certains constructeurs (Xiaomi, Samsung) tuent le service ou désactivent l'accessibilité après mise à jour de l'APK : à mentionner (réactivation après chaque réinstallation).
3. **Performances whisper** : Small q5_1 ≈ 180 Mo disque, ~300–400 Mo RAM ; sur milieu de gamme compter plusieurs secondes pour 10 s d'audio (la détection auto ajoute un encodage). Base q5_1 nettement plus rapide. Mitigations : build Release forcé, 4 threads max, préchargement du modèle, état « transcription » visible, choix `fr`/`en` fixe possible. Libérer le modèle sur `onTrimMemory` critique. OOM possible sur appareils ≤ 3 Go RAM → message + suggestion Base.
4. **Build natif** : NDK/CMake absents localement (téléchargement auto par AGP, ou via Android Studio) ; FetchContent nécessite le réseau pendant le build (OK sur EAS) ; Java 25 par défaut incompatible → `JAVA_HOME` = JBR 21. Durée de compilation ggml significative (une seule ABI pour la limiter). Pages 16 Ko (flag linker).

**Cas limites fonctionnels**
- Champs mot de passe / `inputType` sensible : bulle jamais affichée ; si le focus bascule vers un champ sensible pendant la transcription → pas d'insertion, pas de presse-papiers, toast.
- Champ sans texte : `node.text` peut renvoyer l'indice (hint) → utiliser `isShowingHintText` (API 26+) ; sinon comparer à `node.hintText`.
- Sélection absente (`-1`) ou nœud WebView/Compose/Flutter ne supportant pas `ACTION_SET_TEXT` → repli collage ; champs de jeux/canvas → presse-papiers + toast.
- Nœud cible recyclé / fenêtre changée entre le tap et la fin de transcription → re-chercher le focus ; si différent de l'app d'origine → presse-papiers + toast (ne pas insérer dans une autre app).
- `ACTION_SET_TEXT` remplace tout le texte : toujours fusionner via `TextMerge` (ne jamais perdre le texte existant) ; texte très long (> 10 000 car.) → utiliser directement le collage.
- Clavier fermé pendant l'enregistrement : la bulle reste jusqu'à la fin (busy).
- Plusieurs taps rapides : ignorer les taps pendant `TRANSCRIBING` ; débounce 300 ms.
- Transcription vide ou uniquement des marqueurs → toast « Rien entendu », pas d'entrée d'historique.
- Appel téléphonique / autre app qui tient le micro : `AudioRecord.startRecording` échoue ou silence → message.
- Rotation / changement de taille d'écran : re-borner la position de la bulle.
- Modèle sélectionné non installé mais l'autre l'est → utiliser celui installé ? Non : afficher « modèle à télécharger » et proposer le changement dans l'app (comportement explicite).
- Téléchargement : coupure réseau → `.part` conservé, reprise ; `.part` plus grand que prévu → supprimé ; sha256 faux → supprimé et erreur `CHECKSUM` ; app tuée pendant le téléchargement → reprise au prochain lancement par l'utilisateur (pas de WorkManager dans ce cycle).
- Service d'accessibilité désactivé pendant une dictée → `onUnbind` nettoie tout (enregistrement arrêté, FGS arrêté).
- Historique JSON corrompu → repartir d'une liste vide sans planter.
- Confidentialité : ne jamais logguer le texte dicté ni le contenu des champs ; ne lire le texte du champ qu'au moment de l'insertion.

## Critères d'acceptation
**Vérifiables localement (Tester / Reviewer)**
1. `npm install` (ou `npm ci`) réussit ; `npx expo install --check` ne signale aucune incompatibilité.
2. `npx tsc --noEmit` : 0 erreur (mode `strict`).
3. `npx jest` : tous les tests passent ; couvrent au minimum `pickLanguage`/`L`, `formatBytes` (dont 190085487 et 59707625), `formatDuration`, `normalizeForSearch` (accents, casse), `filterHistory` (requête vide, multi-mots, aucun résultat), `nextOnboardingStep` (chaque étape, sdkInt 32 vs 33), `isReady`.
4. `npx expo prebuild --platform android --clean` réussit ; dans `android/app/src/main/AndroidManifest.xml` on trouve les 6 permissions listées et le `<service>` `app.murmure.core.MurmureAccessibilityService` avec `BIND_ACCESSIBILITY_SERVICE`, `foregroundServiceType="microphone"` et la meta-data `@xml/murmure_accessibility_service` ; `android/gradle.properties` contient `reactNativeArchitectures=arm64-v8a` ; relancer le prebuild ne crée pas de doublons.
5. Avec `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` et `ANDROID_HOME=~/Library/Android/sdk` : `cd android && ./gradlew :murmure-native:testDebugUnitTest` passe (tests `TextMerge` [vide, hint, curseur au milieu, sélection remplacée, sélection -1, espaces/ponctuation], `TextCleanup` [marqueurs, espaces], `BubbleVisibility` [chaque règle, busy prioritaire], `FieldInfo.isSensitive` [4 variations mot de passe + texte normal], `AudioUtils`, `ModelCatalog` [2 modèles, tailles/sha exacts, URL], `ModelManager.rangeHeaderFor` / `sha256Hex` [vecteur connu : sha256("abc") = `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`], `HistoryStore` toJson/fromJson [aller-retour, JSON invalide, cap 500]).
   (Le nom du projet Gradle du module est celui donné par l'autolinking Expo ; adapter la commande si différent, ex. `:murmure-native`.)
6. Si NDK/CMake disponibles (ou téléchargeables) : `./gradlew assembleDebug` réussit et l'APK contient `lib/arm64-v8a/libmurmure_whisper.so` (`unzip -l app/build/outputs/apk/debug/app-debug.apk | grep murmure_whisper`).
7. Aucun secret ni fichier binaire de modèle dans le dépôt (`git ls-files` ne contient ni `.bin`, ni `.keystore`, ni `android/`) ; `eas.json` contient un profil `preview` avec `"buildType": "apk"`.
8. Toutes les chaînes visibles (JS et `strings.xml` + `values-fr/strings.xml`) existent en FR et EN ; le slogan apparaît sur l'accueil.
9. Le texte dicté et le contenu des champs ne sont jamais passés à `Log.*` / `console.*` (revue de code).

**Checklist de test manuel sur téléphone (APK `eas build -p android --profile preview`)**
- [ ] Installation de l'APK ; premier lancement → onboarding en FR (téléphone FR) / EN (téléphone EN).
- [ ] Micro accordé ; notifications (Android 13+) ; overlay ; Android 13+ : activation de l'accessibilité d'abord grisée → procédure « paramètres restreints » fonctionne ; service activé → l'app le détecte au retour.
- [ ] Téléchargement Small : progression, coupure réseau (mode avion) puis reprise sans repartir de zéro ; modèle marqué installé ; téléchargement et sélection de Base ; suppression.
- [ ] Ouvrir Messages / Gmail / Chrome (barre d'adresse et champ de page web) / WhatsApp / Keep : la bulle apparaît quand le clavier s'ouvre, disparaît quand il se ferme (≤ ~0,5 s).
- [ ] Champ mot de passe (écran de connexion, Chrome) : la bulle n'apparaît pas.
- [ ] Tap → bulle rouge + notification « Murmure écoute… » ; dicter une phrase FR → texte inséré au curseur, texte existant conservé, espace correct ; idem en anglais (pas de traduction) ; mélange de phrases successives FR puis EN.
- [ ] Curseur au milieu d'un texte existant : insertion au bon endroit.
- [ ] App où `SET_TEXT` échoue : texte collé, ou à défaut toast « Texte copié » et collage manuel possible.
- [ ] Appui long pendant l'enregistrement : annulation. Tap très bref : rien. Silence : « Rien entendu ».
- [ ] Enregistrement de 2 min : arrêt auto et transcription.
- [ ] Bulle déplacée par glisser : ne prend pas le focus, ne ferme pas le clavier, position conservée.
- [ ] Désactiver la bulle dans Réglages → plus de bulle ; réactiver → retour.
- [ ] Historique : chaque dictée apparaît (texte, date, durée), recherche sans accents (« ete » trouve « été »), copier, supprimer, tout effacer.
- [ ] Mesurer et noter le temps de transcription d'une phrase de ~10 s avec Small et Base (pour le README).
- [ ] Android 14+ : vérifier que le micro n'est pas coupé (pas de message « micro coupé ») quand on dicte depuis une autre app que Murmure.
- [ ] Mode avion après téléchargement : la dictée fonctionne (100 % local).
- [ ] Écran verrouillé : pas de bulle.
