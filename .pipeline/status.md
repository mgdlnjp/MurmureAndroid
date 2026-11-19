# Statut du pipeline

- **Demande** : Murmure Android — app Expo (React Native) + module natif Kotlin. Une bulle flottante Murmure apparaît automatiquement quand un champ de texte est actif / le clavier est ouvert (via un service d'accessibilité + overlay). Toucher la bulle → enregistrement micro → transcription Whisper 100 % locale (whisper.cpp, modèle Small quantifié ~180 Mo par défaut, Base ~60 Mo en option, téléchargés au premier lancement) → texte inséré directement dans le champ actif (ACTION_SET_TEXT / ACTION_PASTE), presse-papiers en secours. Pas de clavier (IME). App compagnon Expo : onboarding des autorisations (overlay, accessibilité, paramètres restreints Android 13+, micro), choix du modèle, historique local. Test via APK EAS (pas Expo Go). Pas de publication Play Store.
- **Date** : 2026-11-17
- **Projet** : ~/Documents/PhoneApp/MurmureAndroid
- **Branche** : feature/murmure-android-bulle (principale : main)
- **Étape courante** : FUSIONNÉ dans main
- **Cycle courant** : 2

## Journal
- 2026-11-17 : dépôt initialisé, branche feature créée.
- 2026-11-17 : Planner terminé, specs.md produit (pas de question bloquante). Étape 2 — Coder.
- 2026-11-18 : Coder terminé, changes.md produit (tsc, jest 25, prebuild, gradle 46 tests, assembleDebug OK). Étape 3 — Tester.
- 2026-11-18 : Tester FAIL (A1 bloquante allowBackup ; A2-A4 non bloquantes). Cycle 1 — Coder.
- 2026-11-18 : Coder cycle 1 terminé (A1-A4 corrigées, jest 64, gradle 129). Étape 3 — Tester (re-test).
- 2026-11-18 : Tester cycle 1 PASS (jest 67, gradle 144). Étape 4 — Reviewer.
- 2026-11-18 : Reviewer CHANGES_REQUESTED (R1 : API 26 non protégées, minSdk 24). Cycle 2 — Coder.
- 2026-11-18 : Coder cycle 2 terminé (R1 + recos 1-8, jest 68, gradle 158, lint OK). Étape 3 — Tester (cycle 2).
- 2026-11-18 : Tester cycle 2 PASS (jest 68, gradle 176, lint OK, minSdk 26). Étape 4 — Reviewer (cycle 2).
- 2026-11-18 : Reviewer cycle 2 APPROVED (0 bloquant). Statut : VALIDÉ EN ATTENTE DE FUSION. Fusion dans main en attente de validation humaine.
- 2026-11-19 : fusion validée par l'utilisateur, branche fusionnée dans main.
