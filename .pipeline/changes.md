# Changes — Murmure Android (bulle flottante + Whisper local)

## Branche
- `feature/murmure-android-bulle`. Dernier commit : `1567c6f` (base, rien n'a été commité : l'orchestrateur s'en charge).
- Rien n'a été poussé et `main` n'a pas été modifiée.

## Fichiers modifiés

| chemin | type de changement | résumé |
|---|---|---|
| `package.json`, `package-lock.json` | créé | Expo SDK 58 (`expo` ~58.0.26, `expo-router` ~58.0.32, RN 0.88.1, React 19.3.0), `expo-localization`, `expo-build-properties`, `expo-system-ui`, `expo-status-bar`, `expo-constants`, `expo-linking`, `react-native-screens`, `react-native-safe-area-context`, `react-dom` (nécessaire à la résolution des peer deps d'expo-router) ; dev : `jest-expo`, `jest`, `@types/jest`, `typescript`. Scripts `start`, `android`, `prebuild`, `typecheck`, `test` |
| `app.config.ts` | créé | nom `Murmure`, slug `murmure-android`, scheme `murmure`, 0.1.0 / versionCode 1, package `app.murmure.android`, icônes, plugins `expo-router`, `expo-localization`, `./plugins/withMurmure.js` ; `blockedPermissions` pour les permissions de stockage ajoutées par défaut par Expo |
| `tsconfig.json` | créé | `extends expo/tsconfig.base`, `strict`, alias `@/*` → `./*`, `types: ["jest","node"]` (TS 6 n'inclut plus les `@types` automatiquement) |
| `jest.config.js`, `jest.setup.ts` | créé | preset `jest-expo`, alias `@/`, mock de `modules/murmure-native` et `expo-localization` (FR) |
| `eas.json` | créé | `cli.appVersionSource: local`, profil `preview` (APK, distribution interne), `production` (non utilisé). Pas de `projectId`, aucun secret |
| `.gitignore` | modifié | `/android/`, `/ios/` (racine seulement), `coverage/`, sorties de build du module (`modules/**/android/build/`, `.cxx/`), `.gradle/`, `expo-env.d.ts`, etc. |
| `README.md` | modifié | slogan, installation APK, autorisations, « paramètres restreints », confidentialité, limites, build local (JBR 21) et EAS, checklist de test manuel |
| `plugins/withMurmure.js` | créé | config plugin idempotent : 6 permissions, `<service>` d'accessibilité, `reactNativeArchitectures=arm64-v8a` |
| `assets/images/*.png` | créé | icône + icônes adaptatives issues du template (à remplacer par une identité Murmure) |
| `app/_layout.tsx` | créé | `Stack` racine, redirection vers `/onboarding` si `onboardingDone == false` |
| `app/(tabs)/_layout.tsx`, `index.tsx`, `history.tsx`, `settings.tsx` | créé | onglets Accueil / Historique / Réglages |
| `app/onboarding.tsx`, `app/model.tsx` | créé | assistant pas à pas et écran modèle |
| `src/i18n.ts`, `src/format.ts`, `src/history.ts`, `src/onboarding.ts`, `src/theme.ts`, `src/useNativeState.ts` | créé | logique pure + hooks natifs |
| `src/components/StepCard.tsx`, `PrimaryButton.tsx`, `ProgressBar.tsx`, `StatusRow.tsx`, `ModelPicker.tsx` | créé | composants UI (`ModelPicker` est partagé par `/model` et l'onboarding) |
| `__tests__/i18n.test.ts`, `format.test.ts`, `history.test.ts`, `onboarding.test.ts` | créé | 25 tests Jest |
| `modules/murmure-native/expo-module.config.json`, `index.ts`, `src/MurmureNative.types.ts`, `src/MurmureNativeModule.ts` | créé | module local (généré par `create-expo-module --local`, plateforme android uniquement, fichier web retiré) |
| `modules/murmure-native/android/build.gradle` | créé | `ndkVersion` racine, `abiFilters arm64-v8a`, CMake 3.22.1, `-DCMAKE_BUILD_TYPE=Release` et `-DANDROID_STL=c++_shared` même en debug, junit 4.13.2 + org.json 20240303 pour les tests |
| `modules/murmure-native/android/src/main/AndroidManifest.xml` | créé | vide (tout passe par le plugin) |
| `.../src/main/cpp/CMakeLists.txt`, `murmure_jni.cpp` | créé | FetchContent whisper.cpp v1.9.5 (URL + SHA256), lib `murmure_whisper`, pages 16 Ko ; JNI init/transcribe/lastLanguage/free/systemInfo |
| `.../java/app/murmure/core/*.kt` (17 fichiers) | créé | `MurmureNativeModule`, `MurmureAccessibilityService`, `BubbleOverlay`, `BubbleVisibility`, `FieldInfo`, `AudioRecorder`, `AudioUtils`, `WhisperBridge`, `WhisperEngine`, `TextMerge`, `TextCleanup`, `TextInserter`, `ModelCatalog`, `ModelManager`, `HistoryStore`, `MurmurePrefs`, `PermissionChecker` |
| `.../res/xml/murmure_accessibility_service.xml`, `values/strings.xml`, `values-fr/strings.xml`, `drawable/murmure_bubble_{bg,mic,stop}.xml`, `drawable/murmure_notification_icon.xml` | créé | config du service, chaînes EN (défaut) + FR, drawables vectoriels |
| `.../src/test/java/app/murmure/core/*Test.kt` (8 fichiers) | créé | 46 tests JVM |

## Décisions techniques
- **Scaffolding** : template `default@sdk-58` généré dans le scratchpad, puis reprise sélective (versions des dépendances, icônes). Le template met ses routes dans `src/app/` ; la spec demande `app/` à la racine avec la logique dans `src/` (sans `src/app/`, expo-router utilise bien `app/`). Contenu de démo, reanimated, expo-image, expo-symbols, etc. non repris. Certains paquets (reanimated, worklets, gesture-handler, masked-view) sont installés **transitivement** (peer deps d'expo-router installées automatiquement par npm) puis autolinkés : pas de dépendance directe ajoutée.
- **Onglets** : `Tabs` de `expo-router/js-tabs` (onglets JS, sans fichiers d'icônes) plutôt que `NativeTabs`, qui demande des images d'icônes.
- **`expo-build-properties`** : installé (étape 1.2) mais **pas** ajouté aux plugins, conformément à « ne pas l'ajouter si inutile ». À désinstaller si le Reviewer préfère.
- **Pas d'état global C++** : le handle JNI pointe vers une structure `MurmureContext { whisper_context*, lastLanguage }` ; `nativeLastLanguage` lit la langue dans cette structure. Le texte est renvoyé via `new String(bytes, "UTF-8")` (et pas `NewStringUTF`), qui ne gère pas correctement l'UTF-8 standard hors BMP.
- **`GGML_CPU_ARM_ARCH=armv8-a`** est forcé dans CMake : base armv8-a explicite, sans fp16 ni dotprod (cf. spec).
- **WhisperEngine** : un seul exécuteur mono-thread pour le préchargement, la transcription et la libération. `release()` et `onModelDeleted()` sont mis en file sur cet exécuteur pour ne jamais bloquer le main thread derrière un chargement de modèle (sinon risque d'ANR).
- **ModelManager** : boucle de redirections manuelle (5 max), `Range` si `.part`, 200 → réécriture depuis 0, 206 → ajout ; un flux interrompu sans erreur explicite garde le `.part` (reprise possible) ; taille supérieure ou sha256 faux → `.part` supprimé + `ModelChecksumException` ; espace libre vérifié → `InsufficientStorageException`. Codes d'erreur JS : `CANCELLED`, `CHECKSUM`, `INSUFFICIENT_STORAGE`, `NETWORK` (toute `IOException`), `UNKNOWN`.
- **HistoryStore** : `parse()` lève une exception (utilisée pour renommer le fichier en `.corrupt`) ; `fromJson()` est la version tolérante (liste vide). `prepend()` pur pour tester le plafond de 500 entrées. Valeurs inconnues de `language`/`outcome` normalisées.
- **Ouverture de l'app depuis le service** : deep link `murmure://model` / `murmure://onboarding` (scheme Expo), sinon intent de lancement.
- **TextMerge** : le curseur se place juste après le texte inséré, espace ajouté inclus. Un espace déjà présent dans le texte existant n'est pas compté.
- **Suivi du champ** : on utilise `AccessibilityService.findFocus(FOCUS_INPUT)`, qui cherche dans toutes les fenêtres. Le nœud cible et son package sont mémorisés quand le champ est éditable, non sensible et n'appartient pas à Murmure.

### Écarts mineurs par rapport aux specs
1. `WhisperEngine.transcribe` renvoie `Result<Transcription>` (texte brut, langue, modelId) au lieu de `Result<String>`, pour remplir l'historique sans appel JNI supplémentaire sur le main thread.
2. `TextInserter.insert(service, target, text, originPackage = null)` : paramètre optionnel ajouté pour appliquer la règle « focus passé dans une autre app → presse-papiers + toast, pas d'insertion ».
3. `nextOnboardingStep(s, modelInstalled, seenWelcome, skipped = new Set())` : 4e paramètre optionnel pour les étapes passables (`notifications`, `overlay`, `restricted`). Sans lui, une étape « passée » réapparaîtrait. `microphone` et `accessibility` ne sont jamais passables. `SKIPPABLE_STEPS` est exporté.
4. `eas.json` : pas de profil `development` (optionnel), car `expo-dev-client` n'est pas installé.
5. Ajouts hors table des fichiers : `jest.config.js` (autorisé par la ligne `package.json`), `src/components/ModelPicker.tsx` (composant partagé demandé par « intégrer le composant de app/model.tsx »), `drawable/murmure_notification_icon.xml` (icône de la notification FGS), `blockedPermissions` dans `app.config.ts`.

## Commandes de vérification lancées
| commande | résultat |
|---|---|
| `npm install` + `npx expo install …` | OK |
| `npx expo install --check` | « Dependencies are up to date » |
| `npx tsc --noEmit` | 0 erreur |
| `npx jest` | 4 suites, 25 tests OK |
| `npx expo export --platform android` (bundle Metro, vérification supplémentaire) | OK (bundle Hermes généré, hors dépôt) |
| `npx expo prebuild --platform android --clean` puis prebuild relancé sans `--clean` | OK ; manifeste : les 6 permissions + service (`BIND_ACCESSIBILITY_SERVICE`, `foregroundServiceType="microphone"`, meta-data `@xml/murmure_accessibility_service`) en un seul exemplaire ; `reactNativeArchitectures=arm64-v8a` présent une fois |
| `./gradlew :murmure-native:testDebugUnitTest` (JBR 21, `ANDROID_HOME`) | BUILD SUCCESSFUL : 46 tests, 0 échec (TextMerge 10, BubbleVisibility 11, FieldInfo 6, AudioUtils 5, TextCleanup 4, HistoryStore 4, ModelCatalog 3, ModelManagerLogic 3). Un premier passage avait révélé une attente de curseur erronée dans `TextMergeTest.cursorInMiddle` (le test était faux, pas le code) : test corrigé |
| `./gradlew assembleDebug` | NDK 27.1.12297006, CMake 3.22.1 et build-tools 37 téléchargés automatiquement par AGP. 1er essai : échec de configuration CMake, car `DOWNLOAD_EXTRACT_TIMESTAMP` n'existe qu'à partir de CMake 3.24. Option retirée. 2e essai : **BUILD SUCCESSFUL**. `unzip -l app/build/outputs/apk/debug/app-debug.apk` contient `lib/arm64-v8a/libmurmure_whisper.so` (1,8 Mo), arm64-v8a uniquement. `llvm-readelf -l` : segments LOAD alignés sur 0x4000 (16 Ko). 5 symboles `Java_app_murmure_core_WhisperBridge_*` exportés. APK debug : 77 Mo |

## Corrections de cycle
Cycle 0 (première implémentation) : aucune anomalie de Tester ou de Reviewer à traiter.

## Points à vérifier
1. `git ls-files` après commit : `modules/murmure-native/android/**` doit être versionné. Une première version de la règle `.gitignore` (`android/`) l'aurait exclu ; elle est corrigée en `/android/`. Aucun `.bin`, `.keystore` ni `android/` racine.
2. Le build natif récupère whisper.cpp par le réseau (FetchContent) : OK en local et attendu OK sur EAS. La première compilation de ggml est longue.
3. Revue de confidentialité : aucun `console.*` dans le code JS. Côté Kotlin et C++, les `Log.*` / `LOG*` ne contiennent que des noms d'exception, des identifiants de modèle ou des codes d'erreur, jamais le texte dicté ni le contenu des champs.
4. Machine d'états du service (`MurmureAccessibilityService`) : débounce des taps (300 ms), appui long = annulation, < 0,4 s ignoré, `isSilenced`, FGS microphone dans un try/catch, nettoyage dans `onUnbind`/`onInterrupt`/`onDestroy`.
5. Tests sur appareil : toute la checklist manuelle du README, en priorité le micro en arrière-plan (Android 14+) et l'insertion dans Chrome / WebView.

---

# Cycle 1 — corrections des anomalies A1 à A4 (`.pipeline/tests.md`)

## Branche
- `feature/murmure-android-bulle`, dernier commit inchangé : `1567c6f`. Rien n'a été commité, rien n'a été poussé et `main` n'a pas été touchée.

## Fichiers modifiés (cycle 1)

| chemin | type de changement | résumé |
|---|---|---|
| `app.config.ts` | modifié | `android.allowBackup: false` (A1) |
| `plugins/withMurmure.js` | modifié | `disableBackup()` force sur `<application>` : `allowBackup="false"`, `fullBackupContent="false"` et `dataExtractionRules="@xml/murmure_data_extraction_rules"`. Idempotent (A1) |
| `modules/murmure-native/android/src/main/res/xml/murmure_data_extraction_rules.xml` | créé | Android 12+ : `<exclude path=".">` sur les 9 domaines, pour `cloud-backup` et `device-transfer` (A1) |
| `modules/murmure-native/android/src/main/java/app/murmure/core/InsertionSafety.kt` | créé | logique pure : `TargetPolicy` (A2), `SetTextCheck` (A3) et `TranscriptionGuard` (A4) |
| `.../core/MurmureAccessibilityService.kt` | modifié | `recompute()` applique `TargetPolicy` et oublie la cible sur FORGET (A2). Jeton `TranscriptionGuard` vérifié dans le callback de `transcribe` ; `cleanup()` l'invalide ; `teardown()` (onUnbind/onDestroy) appelle `cleanup()`, puis `shutdown()`, `overlay = null` et `recorder = null` (A4). `targetPackage` est remis à `null` dans `cleanup()` |
| `.../core/TextInserter.kt` | modifié | `trySetText` renvoie un `SetTextVerdict`. PROBABLY_APPLIED donne `SET_TEXT` sans `ACTION_PASTE` (copie silencieuse dans le presse-papiers) ; seul FAILED déclenche le collage (A3) |
| `modules/murmure-native/android/src/test/java/app/murmure/core/InsertionSafetyTest.kt` | créé | 20 tests JVM : `TargetPolicyTest` 8, `SetTextCheckTest` 8, `TranscriptionGuardTest` 4 |
| `modules/murmure-native/android/src/test/java/app/murmure/core/DataExtractionRulesTest.kt` | créé | 1 test JVM : parse le XML et vérifie les 9 domaines exclus (`path="."`) pour le cloud et le transfert, sans aucun `<include>` |
| `__tests__/withMurmure.test.ts` | modifié (ajout seul) | 2 tests : attributs de sauvegarde posés, et `allowBackup="true"` remplacé de façon idempotente. Les tests du Tester sont conservés tels quels |
| `__tests__/appConfig.test.ts` | créé | 2 tests : `android.allowBackup === false` et plugin déclaré |

## Décisions techniques (cycle 1)
- **A1** : `allowBackup=false` ne suffit pas sur Android 12+. Le **transfert d'appareil à appareil** reste actif pour les apps qui ciblent l'API 31+, d'où les `dataExtractionRules` qui excluent tout, et pas seulement `history.json` et `whisper-models/`. Les préférences ne doivent pas non plus migrer. Une liste blanche serait fragile, donc on exclut tout. `fullBackupContent="false"` couvre les versions ≤ 11. Le XML est placé dans les ressources du module natif, qui sont fusionnées dans l'app ; le plugin se contente d'y faire référence. `allowBackup` est posé à la fois dans `app.config.ts` (demande explicite) et dans le plugin (garantie même si la config change).
- **A2** : la politique de suivi est extraite dans `TargetPolicy.decide` pour être testable. Hors dictée, un focus `null`, non éditable, sensible, sans package ou dans Murmure **oublie** la cible. L'insertion retombe alors sur `rootInActiveWindow.findFocus` au moment voulu, puis sur le presse-papiers et un toast (comportement existant de `TextInserter`). Pendant RECORDING ou TRANSCRIBING, la cible reste figée (KEEP), comme avant. Contrepartie acceptée : un focus brièvement `null` dans la même app fait perdre la cible mémorisée. L'insertion cherche alors le focus courant.
- **A3** : la règle est « ne recoller que si l'on est sûr que rien n'a été appliqué ». `SET_TEXT` refusé → FAILED (collage). Texte relu contenant la dictée → CONFIRMED. `refresh()` en échec, ou texte relu différent de l'original (tronqué ou reformaté) → PROBABLY_APPLIED : **pas de collage**, mais une copie silencieuse dans le presse-papiers sert de filet de sécurité. Texte inchangé (`null` et `""` sont équivalents) → FAILED (collage).
- **A4** : on ne peut pas interrompre l'appel natif `whisper_full` sans toucher au C++ (pas d'`abort_callback` branché). L'« annulation » consiste donc à **ignorer le résultat** : le jeton de génération est invalidé par `cleanup()`, et le callback revient sans insérer, sans historiser et sans `setState`/`recompute`. `WhisperEngine.release()`, déjà mis en file derrière la transcription, libère ensuite le modèle. `overlay = null` n'est fait que dans `teardown()` (onUnbind/onDestroy), pas dans `onInterrupt()` : le service reste vivant après `onInterrupt` et doit garder sa bulle.

## Corrections de cycle
| cycle | anomalie | traitement |
|---|---|---|
| 1 | A1 (bloquante) : `allowBackup="true"` | `allowBackup` et `fullBackupContent` à `false`, plus `dataExtractionRules` qui excluent tout. Vérifié dans les manifestes fusionnés debug et release et dans l'APK (`aapt2 dump xmltree` : `allowBackup=false`, `fullBackupContent=false`, `dataExtractionRules=@0x7f120006` → `res/xml/murmure_data_extraction_rules.xml` présent dans l'APK) |
| 1 | A2 : cible jamais invalidée | `TargetPolicy` + FORGET dans `recompute()` |
| 1 | A3 : double insertion possible | `SetTextCheck` ; pas de `PASTE` si SET_TEXT a vraisemblablement agi |
| 1 | A4 : insertion après désactivation | `TranscriptionGuard` (jeton vérifié dans le callback), `teardown()` avec `overlay = null` |
| — | O1 à O4 | non traitées (observations sans action requise) |

## Commandes de vérification (cycle 1)
| commande | résultat |
|---|---|
| `npx tsc --noEmit` | 0 erreur |
| `npx jest` | 8 suites, **64 tests OK** (60 précédents + 4 nouveaux) |
| `npx expo prebuild --platform android --clean --no-install` | OK ; `<application … android:allowBackup="false" … android:fullBackupContent="false" android:dataExtractionRules="@xml/murmure_data_extraction_rules">` |
| prebuild relancé sans `--clean` | manifeste identique octet pour octet |
| `./gradlew :murmure-native:testDebugUnitTest` (JBR, `ANDROID_HOME`) | BUILD SUCCESSFUL, **129 tests, 0 échec, 0 erreur** (108 précédents + 21 nouveaux) |
| `./gradlew assembleDebug :app:processReleaseMainManifest` | BUILD SUCCESSFUL ; les manifestes fusionnés debug et release contiennent les 3 attributs ; `lib/arm64-v8a/libmurmure_whisper.so` toujours présent |

## Points à vérifier (cycle 1)
1. Sur appareil Android 12+ : `adb shell bmgr backupnow app.murmure.android` ne doit rien sauvegarder, et le transfert d'appareil (ou `adb shell bmgr` en mode D2D) ne doit pas emporter `files/history.json`.
2. A2 : dicter dans l'app A, passer à une app B sans champ exposé avec le clavier ouvert (WebView, Flutter), puis dicter. Le texte doit aller dans le presse-papiers avec un toast, jamais dans A. Vérifier aussi qu'un usage normal (Messages, Gmail) insère toujours directement : un focus brièvement `null` ne doit pas gêner, grâce au repli sur `findFocus` à l'insertion.
3. A3 : champ avec `maxLength` ou masque de saisie (téléphone, carte), champ WebView. Aucun doublon ; le texte reste disponible dans le presse-papiers.
4. A4 : désactiver le service pendant TRANSCRIBING. Aucune insertion, aucune entrée d'historique, aucune exception de bulle ; le service peut être réactivé ensuite normalement.
