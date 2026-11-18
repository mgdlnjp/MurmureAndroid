# Tests — Murmure Android

# Cycle 1 (re-test des anomalies A1 à A4)

## Verdict
**PASS**

Les 4 anomalies du cycle 0 sont corrigées. Je l'ai vérifié en lisant le code de production, pas seulement à partir des tests, puis par les builds. Aucune régression n'est détectée : tsc, Jest, prebuild, les tests JVM, assembleDebug et le manifeste release fusionné sont tous au vert.
Il reste deux observations non bloquantes, O5 et O6 (ci-dessous), et la validation sur appareil réel.

## Tests ajoutés (cycle 1)
- `modules/murmure-native/android/src/test/java/app/murmure/core/Cycle1EdgeCasesTest.kt` (nouveau, 15 tests JVM) :
  - `TargetPolicyEdgeCasesTest` (5 tests) : NUMBER_PASSWORD, WEB_PASSWORD et VISIBLE_PASSWORD donnent FORGET ; un champ URI donne REMEMBER ; KEEP pendant une dictée, même sur un champ sensible ou dans Murmure ; enchaînement app A puis app B (la cible passe à B) ; app A, puis focus non éditable dans B, puis dictée (cible figée à null).
  - `SetTextCheckEdgeCasesTest` (6 tests) : refus de l'action prioritaire sur l'échec de `refresh()` ; émojis et accents ; champ vide qui reçoit le texte ; champ vidé par l'app (PROBABLY_APPLIED, donc pas de collage) ; champ qui force les majuscules ; caractérisation de O5.
  - `TranscriptionGuardEdgeCasesTest` (4 tests) : `onInterrupt` puis nouvelle dictée ; double `teardown` (onUnbind puis onDestroy) puis `reopen` ; un `reopen` sans `shutdown` ne réactive pas un jeton invalidé ; deux `begin()` dans la même génération.
- `__tests__/backupCycle1.test.ts` (nouveau, 3 tests Jest) :
  - les 3 attributs sont posés sur une `<application>` minimale, sans toucher à `android:name` ;
  - des valeurs contraires sont remplacées (`fullBackupContent` et `dataExtractionRules`, qui pointaient vers d'autres règles, par exemple celles d'expo-secure-store) ;
  - la ressource XML référencée existe et contient 18 `<exclude>` et aucun `<include>`.

Aucun test existant n'a été modifié, ni ceux du Coder ni ceux du cycle 0. Aucun fichier de production n'a été modifié.

## Résultats (cycle 1)
| commande | résultat |
|---|---|
| `npx tsc --noEmit` | 0 erreur (exit 0) |
| `npx jest` | **9 suites, 67 tests : 67 réussis, 0 échoué, 0 ignoré** (64 du Coder + 3 du Tester) |
| `npx expo prebuild --platform android --clean --no-install` | OK. `<application … android:allowBackup="false" … android:fullBackupContent="false" android:dataExtractionRules="@xml/murmure_data_extraction_rules">` |
| prebuild relancé sans `--clean` | `AndroidManifest.xml` identique (`diff` vide) : idempotent |
| `./gradlew :murmure-native:testDebugUnitTest assembleDebug :app:processReleaseMainManifest` (JAVA_HOME = JBR d'Android Studio, ANDROID_HOME = ~/Library/Android/sdk) | BUILD SUCCESSFUL en 2 min 39 |
| résultats JUnit (`build/test-results/testDebugUnitTest`) | **23 classes, 144 tests : 144 réussis, 0 échec, 0 erreur, 0 ignoré** (129 du Coder + 15 du Tester) |
| manifestes fusionnés debug et release (`merged_manifest/*`) | les 3 attributs sont présents : `allowBackup="false"`, `fullBackupContent="false"`, `dataExtractionRules="@xml/murmure_data_extraction_rules"` |
| `aapt2 dump xmltree` (APK debug) | `allowBackup=false`, `fullBackupContent=false`, `dataExtractionRules=@0x7f120006` ; `res/xml/murmure_data_extraction_rules.xml` est présent dans l'APK avec 18 `exclude` |
| `unzip -l app-debug.apk` | `app-debug.apk` de 77,6 Mo ; `lib/arm64-v8a/libmurmure_whisper.so` (1 884 936 octets) ; 0 entrée `lib/` hors arm64-v8a |
| manifeste release : service et permissions | service présent une seule fois ; RECORD_AUDIO, INTERNET, SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE et POST_NOTIFICATIONS présentes chacune une seule fois, plus VIBRATE et DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION (inchangé) |

## Vérification des anomalies (lecture du code)
| anomalie | statut | constat |
|---|---|---|
| A1 (bloquante) | **corrigée** | `app.config.ts:16` contient `allowBackup: false`. `plugins/withMurmure.js`, `disableBackup()`, force les 3 attributs et écrase toute valeur existante. `murmure_data_extraction_rules.xml` exclut `path="."` sur les 9 domaines, pour `cloud-backup` comme pour `device-transfer`. C'est bien nécessaire : sur Android 12+ ciblant l'API 31+, `allowBackup=false` ne bloque pas le transfert d'appareil à appareil. `fullBackupContent="false"` couvre Android ≤ 11. Le manifeste fusionné ne présente aucun conflit, et aucune bibliothèque ne réimpose `allowBackup`. |
| A2 | **corrigée** | `MurmureAccessibilityService.kt:159-172`. Hors dictée, `TargetPolicy.decide` oublie la cible (nœud et package) si le focus est `null`, non éditable, sensible, sans package ou dans Murmure. Dans le scénario du rapport (app A, puis app B sans champ exposé), `target` et `origin` valent `null` : `TextInserter.insert` repasse par `rootInActiveWindow.findFocus` et, faute de nœud, par le presse-papiers et un toast. Il ne peut plus insérer dans A. `cleanup()` remet aussi `targetPackage` à `null`. |
| A3 | **corrigée** | `TextInserter.kt:59-70` et `:79-92`. `ACTION_PASTE` n'est plus tenté que sur FAILED, c'est-à-dire si l'action est refusée ou si le contenu est inchangé. Un champ tronqué, reformaté, vidé ou illisible après un SET_TEXT accepté donne PROBABLY_APPLIED : pas de collage, donc pas de doublon, avec une copie silencieuse dans le presse-papiers. |
| A4 | **corrigée** | `MurmureAccessibilityService.kt:108-129` et `:283-286`. Le callback de `WhisperEngine.transcribe` est posté sur le main thread (`WhisperEngine.kt:81`), donc le jeton non synchronisé est sûr. `cleanup()` invalide le jeton et `teardown()` (onUnbind/onDestroy) appelle `shutdown()`, `overlay = null` et `recorder = null`. Un résultat arrivé après la désactivation retourne avant `onTranscribed` : aucune insertion, aucune entrée d'historique, aucun `setState` ni `recompute`. `onServiceConnected` appelle `reopen()`. |

## Anomalies (cycle 1)
Aucune anomalie bloquante ni non bloquante à corriger. A1 à A4 sont fermées.

### Observations nouvelles (non bloquantes, aucune action requise)
- **O5** : `InsertionSafety.kt:48`, `SetTextCheck.verdict`. Le test `now.contains(inserted)` passe avant la comparaison avec `before`.
  - Scénario : le champ contient déjà « ok merci », la dictée donne « ok », et l'app accepte SET_TEXT sans l'appliquer (champ contrôlé qui se réinitialise). Le verdict est alors CONFIRMED : pas de collage et pas de copie dans le presse-papiers. La dictée n'est conservée que dans l'historique.
  - Ce cas est rare et ne crée aucun doublon. Il est figé par le test `unchangedFieldAlreadyContainingDictationIsConfirmed`.
  - Amélioration possible : traiter `now == before` comme FAILED avant le `contains`. Cela suppose que le texte fusionné diffère toujours de l'original, ce qui est vrai puisque `text` n'est pas vide.
- **O6** : `MurmureAccessibilityService.kt:80-82`. `onInterrupt()` appelle `cleanup()`, qui invalide désormais aussi une transcription en cours (le résultat est perdu, il n'est même pas historisé). `onInterrupt` arrêtait déjà l'enregistrement avant le cycle 1. Comme il est appelé par le système quand le retour d'accessibilité est interrompu, il faut vérifier sur appareil, en particulier avec TalkBack actif, qu'il n'est pas déclenché en cours d'usage normal.
- Rappel : comme prévu, une dictée commencée dans l'app A puis terminée après un passage à l'app B est insérée dans A (cible figée avec KEEP), si le nœud de A est encore rafraîchissable. C'est la conséquence de la cible figée, et non un retour de A2.

## Non-régression (cycle 1)
- Les 60 tests Jest et les 108 tests JVM du cycle 0 passent sans modification.
- Manifeste : service, intent-filter, meta-data et permissions sont inchangés, sans doublon. La seule différence avec le cycle 0 concerne les 3 attributs de sauvegarde.
- `gradle.properties` contient toujours `reactNativeArchitectures=arm64-v8a`, et l'APK ne contient que l'ABI arm64-v8a.
- Le prebuild `--clean` puis un prebuild simple sont idempotents.
- Le comportement de `TextInserter` est inchangé pour : champ sensible (NOTHING et toast), changement de package (presse-papiers), insertion confirmée (SET_TEXT) et échec complet (PASTE, puis presse-papiers et toast).
- Branche `feature/murmure-android-bulle`, HEAD `1567c6f` inchangé, aucun commit, `main` non touchée.

## À vérifier sur appareil réel (ajouts du cycle 1)
1. `adb shell bmgr backupnow app.murmure.android` ne sauvegarde rien ; un transfert d'appareil (Android 12+) n'emporte pas `files/history.json`.
2. A2 : app A, puis app B en WebView ou Flutter avec le clavier ouvert. La dictée doit aller dans le presse-papiers avec un toast, jamais dans A. Dans Messages et Gmail, l'insertion doit rester directe.
3. A3 : champs avec `maxLength`, masque de téléphone ou de carte, et WebView. Aucun doublon.
4. A4 et O6 : désactiver le service pendant TRANSCRIBING (aucune insertion ni historique, puis réactivation normale) ; vérifier avec TalkBack que `onInterrupt` ne coupe pas les dictées.

---

# Cycle 0 (rapport initial, conservé pour référence)


## Verdict (cycle 0, remplacé par le verdict du cycle 1)
**FAIL**

Les 9 critères d'acceptation vérifiables en local sont **tous satisfaits** : tsc, Jest, prebuild, tests JVM, assembleDebug avec `.so` arm64-v8a, .gitignore, secrets, i18n et logs.
Le verdict est FAIL à cause d'une seule anomalie **bloquante**, A1. Elle touche la confidentialité : l'historique dicté peut quitter le téléphone par la sauvegarde Android. Elle va contre l'hypothèse « Aucune donnée ne sort du téléphone » des specs et contre la promesse du README. La correction tient en une ligne.
A2, A3 et A4 sont **non bloquantes** : ce sont des risques repérés en revue de code, à corriger de préférence et à confirmer sur appareil.

## Tests ajoutés
JS / Jest (`__tests__/`) :
- `__tests__/withMurmure.test.ts` (nouveau, 5 tests). Le config plugin est exécuté directement, sans prebuild. On vérifie : les 6 permissions sans doublon (y compris quand INTERNET est déjà présente ou que la liste est absente), les attributs exacts du `<service>`, l'intent-filter et la meta-data, l'idempotence (2 passes sans doublon, les autres services sont conservés) et `reactNativeArchitectures=arm64-v8a` présent une seule fois, la valeur existante étant remplacée.
- `__tests__/edgeCases.test.ts` (nouveau, 21 tests). Cas limites de `formatBytes` (négatif, frontières d'unités, très grandes valeurs), `formatDuration` (négatif, arrondi, plus d'une heure), `formatDate` (midi et minuit en EN, veille au changement d'année, date future), `normalizeForSearch` et `filterHistory` (vide, cédilles, « ete » qui trouve « été », requête accentuée sur texte non accentué, espaces multiples, caractères spéciaux de regex, pas de mutation de la liste), `wordCount` (apostrophes, tirets, émojis), `nextOnboardingStep` (welcome prioritaire, API 33+ avec accessibilité déjà active, API 32, micro retiré après coup, liste des étapes passables) et `isReady`.
- `__tests__/i18nCompleteness.test.ts` (nouveau, 8 tests, critère 8). Arbre `strings` FR et EN : mêmes clés, aucune chaîne vide, chaînes dynamiques (pluriels, « Étape x sur n »), slogan dans les deux langues, traductions réellement différentes, et repli EN si `getLocales()` est vide ou lève une exception.

Kotlin / JUnit (`modules/murmure-native/android/src/test/java/app/murmure/core/`) :
- `PureLogicEdgeCasesTest.kt` (nouveau, 6 classes, 38 tests) :
  - `TextMergeEdgeCasesTest` : sélection inversée, sélection à moitié invalide, insertion en position 0, au milieu d'un mot, sur un indice avec curseur à 7, `null` avec indice, sélection totale, « … », avant « ? », retour à la ligne, émoji (paire de substitution), texte long, curseur toujours dans les bornes.
  - `TextCleanupEdgeCasesTest` : espace initial de Whisper, marqueur collé à la ponctuation, marqueurs EN/FR (`[Music]`, `(applause)`, `(Rires)`, `*rires*`, `[ Silence ]`), vraies parenthèses conservées, « ? » français conservé, vide, ♪, chiffres.
  - `FieldInfoEdgeCasesTest` : variantes mot de passe combinées à des flags (CAP_SENTENCES, MULTI_LINE, NO_SUGGESTIONS, SIGNED, DECIMAL), variantes non sensibles (NULL, DATE, PHONE, WEB_EMAIL, FILTER, POSTAL, PERSON_NAME), `isPassword` sur un nœud non éditable.
  - `BubbleVisibilityEdgeCasesTest` : sans focus et sans IME, champ sensible avec IME, verrouillage avec IME seule, propre app avec IME, busy dans la propre app, package inconnu, bulle désactivée avec modèle absent, nœud non éditable avec IME.
  - `AudioUtilsEdgeCasesTest` : taux 0 ou négatif, 120 s, frontière 0,4 s (6399 / 6400 échantillons), tableau vide considéré comme silencieux, petit négatif, RMS pleine échelle, conversion de 0 échantillon, valeurs dans [-1, 1[.
  - `ModelCatalogEdgeCasesTest` : sha en hexadécimal minuscule sur 64 caractères, ids identiques au type TS `ModelId`, unicité, `byId` sensible à la casse, `rangeHeaderFor` avec des valeurs au-delà de 2 Go.
- `StorageWithFakeContextTest.kt` (nouveau, 16 tests, avec un `FakeContext` qui ne fournit que `filesDir`, sans réseau) :
  - `HistoryStoreFileTest` : fichier absent, `add` avec le plus récent en tête et unicode persisté, aucun `.tmp` résiduel, plafond de 500 via `add`, `delete` (id inconnu sans effet) et `clear` (`[]`), fichier corrompu renommé en `.corrupt` (l'ancien `.corrupt` est remplacé) puis reprise normale, `parse("{}")` qui lève une exception, éléments parasites ou champs manquants, UUID uniques, clés de `toMap()` identiques au type TS.
  - `ModelManagerFileTest` : chemins (`filesDir/whisper-models`, `.part`), `isInstalled` qui exige la taille exacte, `partialBytes`, téléchargement d'un modèle déjà installé sans réseau (progression `(size, size)`), `.part` complet mais sha256 faux (fichier supprimé, `ModelChecksumException`, verrou libéré), `delete` qui supprime le fichier final et le `.part`, `cancel` hors téléchargement sans effet.

Aucun test existant n'a été modifié. Aucun fichier de production n'a été modifié.

## Résultats
| commande | résultat |
|---|---|
| `npx tsc --noEmit` | 0 erreur (exit 0), nouveaux tests inclus |
| `npx jest` | **7 suites, 60 tests : 60 réussis, 0 échoué, 0 ignoré** (25 existants + 35 ajoutés) |
| `EXPO_OFFLINE=1 npx expo install --check` | « Dependencies are up to date » (mode hors ligne, sans appel à l'API Expo) ; `npm ls --depth=0` cohérent (expo 58.0.26, RN 0.88.1, React 19.3.0) |
| `npx expo prebuild --platform android --clean --no-install` | OK |
| prebuild relancé sans `--clean` | `AndroidManifest.xml` et `gradle.properties` identiques octet pour octet (`diff` vide), donc **aucun doublon** |
| `./gradlew :murmure-native:testDebugUnitTest` (JBR 21, `ANDROID_HOME=~/Library/Android/sdk`) | BUILD SUCCESSFUL : **16 classes, 108 tests : 108 réussis, 0 échec, 0 erreur, 0 ignoré** (46 existants + 62 ajoutés) |
| `./gradlew assembleDebug` (après prebuild `--clean`, donc recompilation native complète, whisper.cpp récupéré par FetchContent) | BUILD SUCCESSFUL en 2 min 55 ; `app-debug.apk` de 77,6 Mo |
| `unzip -l app-debug.apk \| grep murmure_whisper` | `lib/arm64-v8a/libmurmure_whisper.so` (1 884 936 octets) ; **aucune autre ABI** (0 entrée `lib/` hors arm64-v8a) |
| `llvm-readelf` sur le `.so` | segments LOAD alignés sur 0x4000 (16 Ko) ; 5 symboles JNI `Java_app_murmure_core_WhisperBridge_{nativeInit,nativeTranscribe,nativeLastLanguage,nativeFree,nativeSystemInfo}` ; NEEDED = liblog, libandroid, libm, libdl, libc++_shared, libc |
| `aapt2 dump xmltree` (manifeste de l'APK) et `:app:processReleaseMainManifest` | service `app.murmure.core.MurmureAccessibilityService` présent une seule fois, exported, `BIND_ACCESSIBILITY_SERVICE`, `foregroundServiceType=0x80` (microphone), intent-filter et meta-data présents. Permissions en release, chacune une seule fois : RECORD_AUDIO, INTERNET, SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE, POST_NOTIFICATIONS, plus VIBRATE et DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION (ajoutées par Expo/AndroidX) ; READ/WRITE_EXTERNAL_STORAGE retirées (`tools:node="remove"`). En debug seulement : ACCESS_LOCAL_NETWORK (serveur de dev RN) |

### Critères d'acceptation vérifiables en local
| # | critère | statut |
|---|---|---|
| 1 | npm / `expo install --check` | OK (contrôle hors ligne ; `npm ci` non relancé pour ne pas réinstaller `node_modules`) |
| 2 | tsc strict, 0 erreur | OK |
| 3 | Jest + couverture minimale demandée | OK (`pickLanguage`/`L`, `formatBytes` 190085487 → 181 Mo et 59707625 → 57 Mo, `formatDuration`, `normalizeForSearch`, `filterHistory`, `nextOnboardingStep` sdk 32 et 33, `isReady`) |
| 4 | prebuild, manifeste (6 permissions et service), arm64-v8a, idempotence | OK |
| 5 | tests JVM (TextMerge, TextCleanup, BubbleVisibility, FieldInfo, AudioUtils, ModelCatalog, rangeHeaderFor/sha256 « abc », HistoryStore) | OK |
| 6 | assembleDebug et `.so` arm64-v8a dans l'APK | OK |
| 7 | pas de secret, de `.bin`, de keystore ni de `android/` ; `eas.json` avec `preview` en `apk` | OK. Simulation avec `git ls-files --others --exclude-standard` (rien n'est encore commité) : 82 fichiers, dont les 38 du module natif (`build.gradle`, `cpp`, `kotlin`, `res`, tests). Ni `android/` racine, ni `build/`, `.cxx/`, `.bin`, keystore, `.env` ou `.so`. `git check-ignore` confirme : `android/…`, `modules/…/android/build/…`, `.cxx/…`, `*.bin`, `*.keystore` et `.env.local` sont ignorés ; `modules/murmure-native/android/src/**` est suivi. Recherche de secrets (clés API, jetons, `hf_`, `ghp_`, `AKIA`, mots de passe de keystore, `projectId`) : aucun résultat. Aucun fichier de plus de 1 Mo |
| 8 | chaînes FR/EN, slogan sur l'accueil | OK : test de complétude `strings` ; `values/strings.xml` et `values-fr/strings.xml` ont exactement les mêmes 19 clés ; aucun texte en dur dans `app/` ni `src/components/` (grep) ; `strings.tagline` affiché dans `app/(tabs)/index.tsx:23` |
| 9 | pas de texte dicté ni de contenu de champ dans `Log.*` / `console.*` | OK (revue) : 0 `console.*` en JS. Les 19 `Log.*` Kotlin et les 4 `LOG*` C++ ne journalisent que des messages fixes, des noms de classes d'exception, des identifiants de modèle ou des codes d'erreur. `fieldInfoOf(withText=false)` est utilisé pour la visibilité : le texte du champ n'est lu qu'au moment de l'insertion. Les logs internes de whisper.cpp et ggml partent sur stderr, qui n'est pas redirigé vers logcat sur Android, et `print_*` vaut false |

### Champs mot de passe (vérifié en local)
- `FieldInfo.isSensitive()` : les 4 variantes (0x80, 0x90, 0xE0, NUMBER|0x10) et `isPassword` sont couvertes, y compris combinées à des flags. 0x10 en classe TEXT (URI) n'est pas considéré comme sensible.
- `BubbleVisibility` : la bulle est masquée sur un champ sensible, même avec l'IME visible.
- `TextInserter.insert` (revue, l.47-51) : sur un nœud sensible, ni `SET_TEXT`, ni presse-papiers, ni `PASTE`. Un toast « Champ protégé » s'affiche et le résultat est `NOTHING`.
- `MurmureAccessibilityService.recompute` (l.147) : un nœud sensible n'est jamais mémorisé comme cible.

## Anomalies

**A1 — BLOQUANTE (confidentialité) : `android:allowBackup="true"`, donc l'historique dicté peut quitter le téléphone.**
- Fichiers : `app.config.ts:12` (bloc `android`, sans `allowBackup`). Conséquence visible dans `android/app/src/main/AndroidManifest.xml:18` après prebuild et dans le manifeste release fusionné (`android:allowBackup="true"`, valeur par défaut d'Expo).
- Reproduction : `npx expo prebuild --platform android --clean` puis `grep -o 'allowBackup="[a-z]*"' android/app/src/main/AndroidManifest.xml`.
- Attendu : aucune donnée ne sort du téléphone (specs, « Hypothèses » ; README, « Confidentialité »). `files/history.json`, qui contient le texte dicté brut et nettoyé, ne doit être ni sauvegardé sur Google Drive ni transféré d'un appareil à l'autre.
- Obtenu : `allowBackup="true"` sans règles d'exclusion. L'Auto Backup Android et le transfert entre appareils incluent `filesDir` (`history.json`, `murmure_prefs`, et potentiellement `whisper-models/`).
- Correction suggérée : `android: { allowBackup: false, … }` dans `app.config.ts`. Sinon, des `dataExtractionRules` / `fullBackupContent` qui excluent `history.json` et `whisper-models/`, via le config plugin.

**A2 — non bloquante (risque d'insertion dans la mauvaise app, à confirmer sur appareil) : la cible mémorisée n'est jamais invalidée.**
- Fichier : `MurmureAccessibilityService.kt:147-152`. `targetNode` et `targetPackage` ne sont mis à jour que si un nouveau champ éditable et non sensible a le focus. Ils ne sont pas remis à `null` quand le focus passe sur un nœud non éditable, devient `null` ou change d'app. Or la bulle s'affiche avec `imeVisible` seul (`focused == null`, fréquent avec WebView, Compose ou Flutter).
- Scénario : dicter dans un champ de l'app A, puis passer dans l'app B dont le champ n'est pas exposé (`findFocus` renvoie `null`) avec le clavier ouvert. La bulle s'affiche ; tap, dictée, puis `TextInserter.insert(target = nœud de A, origin = A)`. Si `refresh()` du nœud de A réussit encore (multi-fenêtre, écran partagé, fenêtre de A conservée), le texte est inséré dans A, et le contrôle `originPackage` passe puisque l'origine vaut A.
- Attendu : quand le champ focalisé n'est pas éditable ou est `null`, la cible doit être oubliée ; il faut alors chercher le focus au moment de l'insertion, ou à défaut passer par le presse-papiers et un toast.
- Obtenu : risque d'insérer le texte dans une autre app que celle affichée.

**A3 — non bloquante (risque de double insertion, à confirmer sur appareil).**
- Fichier : `TextInserter.kt:83-84` puis `:62-63`. Si `ACTION_SET_TEXT` renvoie true mais que la vérification échoue, le code bascule sur presse-papiers et `ACTION_PASTE`. La vérification échoue si `refresh()` renvoie false, ou si le champ reformate ou tronque le texte (maxLength, masque, champ de saisie formaté).
- Scénario : champ limité à N caractères ou à formatage automatique. `SET_TEXT` applique déjà le texte fusionné (tronqué), puis `PASTE` le colle une seconde fois.
- Attendu : pas de doublon. Par exemple, ne pas coller si le texte du nœud a changé après `SET_TEXT`, ou renvoyer `SET_TEXT` si l'action a réussi et que le contenu diffère de l'original.
- Obtenu : texte potentiellement présent deux fois.

**A4 — non bloquante : une transcription en cours n'est pas annulée quand le service est désactivé.**
- Fichiers : `MurmureAccessibilityService.kt:106-116` (`cleanup`) et `:263-268` (callback `WhisperEngine.transcribe`). `cleanup()` arrête l'enregistrement et le service au premier plan, mais le callback déjà mis en file sur le thread `murmure-whisper` s'exécute ensuite quand même. Il appelle `onTranscribed`, qui insère le texte, écrit l'historique et appelle `setState(IDLE)` puis `recompute()`, ce qui peut tenter un `overlay.show()` sur un service détruit (exception interceptée).
- Scénario : désactiver le service d'accessibilité pendant l'état TRANSCRIBING.
- Attendu (specs, cas limites) : « Service d'accessibilité désactivé pendant une dictée → onUnbind nettoie tout ».
- Obtenu : insertion et entrée d'historique après la désactivation. Correction : un drapeau `destroyed` ou un jeton de génération vérifié dans le callback, et `overlay = null` dans `cleanup`.

### Observations (non bloquantes, aucune action requise)
- O1 : `TextCleanup` (regex `\*[^*]{1,40}\*`) supprime tout texte entre astérisques. Par exemple, « 2 * 3 * 4 » devient « 2 4 ». C'est rare en dictée, mais la règle est plus large que les « marqueurs Whisper » prévus par les specs.
- O2 : `murmure_jni.cpp:121`. Si la détection FR/EN échoue, `params.language = "auto"` laisse Whisper choisir n'importe quelle langue. La restriction FR/EN n'est alors plus garantie, et un repli explicite sur `fr` serait plus conforme.
- O3 : `recompute()` s'exécute à chaque événement, y compris `typeViewTextSelectionChanged` à chaque frappe. Chaque appel fait 2× `ModelManager.isInstalled` (stat de fichier), `windows`, `findFocus` et une interrogation du keyguard. Le coût est acceptable, mais il faudra surveiller la fluidité de frappe sur appareil d'entrée de gamme.
- O4 : `formatBytes` affiche « 1.0 KB » ou « 10.0 MB » juste sous une frontière (arrondi de 9,99). C'est conforme à la règle des specs, mais purement cosmétique.

## Non-régression
- Les 25 tests Jest et les 46 tests JVM d'origine passent sans modification.
- Le prebuild `--clean` régénère `android/` (ignoré par git), et un 2e prebuild produit un manifeste et un `gradle.properties` identiques.
- Une recompilation native complète depuis zéro (sans cache `.cxx`) réussit, avec FetchContent vérifié par SHA256 et la base `armv8-a`.
- `.gitignore` : les règles d'origine (`*.bin`, `models/`, `*.keystore`, `.env*`, `*.apk`) sont toujours actives, et la règle `/android/` est limitée à la racine, donc le module natif reste suivi.
- `main` n'a pas été touchée ; aucun commit.

## À vérifier uniquement sur appareil réel
(Aucun téléphone ni émulateur disponible : ces points n'ont pas pu être exécutés.)
1. Chargement réel de `libmurmure_whisper.so` (`System.loadLibrary`), absence de SIGILL sur un SoC ancien, temps de transcription Small et Base sur une phrase d'environ 10 s, consommation mémoire, comportement en cas d'OOM.
2. Micro en arrière-plan sur Android 11, 14 et plus : FGS `microphone` démarré depuis le service d'accessibilité (`ForegroundServiceStartNotAllowedException` ?), détection `isSilenced`, notification « Murmure écoute… ».
3. Apparition et disparition de la bulle à l'ouverture et à la fermeture du clavier (Messages, Gmail, Chrome barre d'adresse et page web, WhatsApp, Keep), délai ≤ 0,5 s, absence sur les champs mot de passe réels, sur l'écran verrouillé et dans Murmure.
4. Bulle `TYPE_ACCESSIBILITY_OVERLAY` qui ne prend pas le focus (le clavier reste ouvert), glisser-déposer, position conservée, rotation.
5. Insertion : `SET_TEXT` fusionné au curseur, repli `PASTE` puis presse-papiers et toast (WebView, Compose, Flutter), plus A2 et A3 ci-dessus.
6. Détection FR/EN réelle, sans traduction ; enchaînement d'une phrase FR puis d'une phrase EN.
7. Téléchargement réel depuis Hugging Face : redirection 302 vers le CDN, `Range` et 206, reprise après coupure (mode avion), sha256 OK, espace insuffisant, annulation.
8. Onboarding sur Android 13+ : « paramètres restreints », retour au premier plan qui rafraîchit les états, permissions micro et notifications via `PermissionsAndroid`, deep links `murmure://model` et `murmure://onboarding` depuis le service.
9. Appui long pour annuler, tap très bref ignoré, « Rien entendu », arrêt automatique à 120 s, désactivation du service pendant une dictée (A4).
10. Rendu de l'app compagnon (onglets, historique : copier, supprimer, tout effacer), thème clair et sombre, langue de l'UI selon la langue du téléphone.
