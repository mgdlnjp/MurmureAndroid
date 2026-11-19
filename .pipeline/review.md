# Revue — cycle 2 (commit 7b7e80a)

## Verdict
**APPROVED** — R1 résolu, recommandations 1-8 correctement traitées, aucun nouveau défaut bloquant. tsc 0 erreur, Jest 68/68, 176 tests JVM, lint module 0 erreur, assembleDebug OK.

## Critères d'acceptation
| # | Critère | Statut |
|---|---|---|
| 1 | npm install / expo install --check | OK |
| 2 | tsc --noEmit strict | OK |
| 3 | Jest + couverture | OK (68 tests) |
| 4 | Prebuild, manifeste, ABI, idempotence | OK (minSdk 26 dans manifeste et APK) |
| 5 | Tests JVM | OK (176 ; 1 test modifié légitimement — O5 inversé) |
| 6 | assembleDebug + .so arm64-v8a | OK |
| 7 | Pas de secret/.bin/keystore/android racine ; eas preview apk | OK |
| 8 | Chaînes FR/EN, slogan | OK |
| 9 | Aucun texte dicté dans les logs | OK |

Checklist manuelle sur téléphone : à faire (changes.md, cycle 2, points 1-7).

## Problèmes bloquants
Aucun.

## Recommandations (non bloquantes)
1. O7 ModelManager.kt:102-108 : supprimer partFile avant installedFile dans delete().
2. O8 AudioRecorder : drapeau `running` par session.
3. TextCleanup.kt:8-13 : ajouter `\b` après les mots-marqueurs (« (musicien) », `*musical*` supprimés à tort).
4. specs.md:229 : « API 29+ » → 30 pour le type FGS micro.
5. changes.md / tests.md cycle 2 mentionnent « HEAD 26ae7d5 » alors que 7b7e80a existe.
6. O9 : gardes SDK 26 devenues inutiles (ObsoleteSdkInt), retrait possible.
7. Reports cycle 1 : points 9-12 (espace disque sur 200, erreur nativeInit, .pipeline versionné, test Robolectric).
8. Sur appareil : aperçu presse-papiers masqué Android 13+, ACTION_PASTE avec clip sensible, libération micro, arrêt 120 s.

## Sécurité
Branche dédiée, main intacte (1567c6f). Aucun secret. Clips de repli sensibles, plus de copie silencieuse, clear() efface la copie corrompue, onInterrupt n'est plus exploitable. Langue FR/EN garantie côté natif.

## Performances
Pas de régression. À surveiller : recompute() par événement, HistoryStore.add sur le main thread, priorité du thread whisper.

---

# Revue — cycle 1 (commit 26ae7d5)

## Verdict
**CHANGES_REQUESTED** — 1 problème bloquant (R1). Architecture conforme, confidentialité bien traitée, tests verts.

## Conformité (critères vérifiables localement)
| # | Critère | Statut |
|---|---|---|
| 1 | npm install / expo install --check | OK (rapport Tester) |
| 2 | tsc --noEmit strict | OK |
| 3 | Jest + couverture minimale | OK (9 suites, 67 tests) |
| 4 | Prebuild : manifeste, ABI, idempotence | OK |
| 5 | Tests JVM | OK (144 tests) |
| 6 | assembleDebug + .so arm64-v8a | OK |
| 7 | Pas de secret/.bin/keystore/android racine ; eas preview apk | OK |
| 8 | Chaînes FR/EN, slogan | OK |
| 9 | Aucun texte dicté dans les logs | OK |

Hypothèse non respectée : minSdk 24 avec API 26+ non protégées (R1). Checklist manuelle sur téléphone : à faire.

## Problèmes bloquants
**R1 — Plantage sur Android 7.0/7.1 (API 24-25).** minSdk effectif 24, lint `abortOnError false`.
- MurmureAccessibilityService.kt:322-327 : NotificationChannel/createNotificationChannel (API 26) sans garde dans onServiceConnected → NoClassDefFoundError, le service ne démarre jamais.
- Même fichier l.330 : Notification.Builder(this, CHANNEL_ID) (API 26).
- TextInserter.kt:21 : node.hintText (API 26) dans la branche SDK_INT < 26 → NoSuchMethodError à chaque insertion.
- Correction : gardes SDK_INT ≥ 26, ou minSdk 26 via expo-build-properties ; relancer `./gradlew :murmure-native:lintDebug` sans NewApi.

## Recommandations (non bloquantes)
1. onInterrupt() → cleanup() : déclenchable par toute app via AccessibilityManager.interrupt() ; ne nettoyer que dans onUnbind/onDestroy.
2. murmure_jni.cpp:121 : langue détectée hors fr/en → repli explicite sur "fr".
3. InsertionSafety.kt:46-48 : tester `now == before` → FAILED avant `contains`.
4. HistoryStore.kt:149-151 : history.json.corrupt conservé indéfiniment, non supprimé par clear().
5. TextInserter.kt:63 : copie « silencieuse » dans le presse-papiers sans IS_SENSITIVE → historique Gboard/aperçu Android 13+. Documenter ou supprimer.
6. AudioRecorder.shutdown() : release() possible pendant read() si join(500 ms) expire.
7. ModelManager.delete pendant téléchargement du même modèle → erreur NETWORK au lieu de CANCELLED.
8. TextCleanup : regex `\*[^*]{1,40}\*` trop large (« 2 * 3 * 4 » → « 2 4 »).
9. Réponse 200 à une requête Range : pas de re-vérification d'espace disque.
10. Échec nativeInit (OOM) remonté comme ModelNotInstalledException ; WhisperEngine.isBusy inutilisé.
11. .pipeline/*.md commité : s'assurer que c'est voulu.
12. Tests : Robolectric pour TextInserter ; lint NewApi en erreur.

## Sécurité
Branche dédiée, main intacte. Aucun secret ni modèle. Sauvegarde désactivée (cloud + transfert). Service exported protégé par BIND_ACCESSIBILITY_SERVICE. Permissions minimales, stockage bloqué. Champs sensibles exclus. Modèles vérifiés (taille + sha256), whisper.cpp épinglé par SHA256. Logs propres.

## Performances
Transcription sur exécuteur dédié, pas d'ANR. Mémoire acceptable (≤ ~20 Mo de tampons à 120 s). À surveiller : recompute() à chaque événement (2 stat + updateViewLayout) ; HistoryStore.add sur le main thread ; thread whisper en MAX_PRIORITY.
