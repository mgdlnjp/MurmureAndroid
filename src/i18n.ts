import { getLocales } from 'expo-localization';

export type UiLanguage = 'fr' | 'en';

/** FR si la première langue du téléphone commence par « fr », sinon EN (même règle que l'app macOS). */
export function pickLanguage(languageCode: string | null | undefined): UiLanguage {
  return (languageCode ?? '').toLowerCase().startsWith('fr') ? 'fr' : 'en';
}

function detectLanguage(): UiLanguage {
  try {
    return pickLanguage(getLocales()[0]?.languageCode);
  } catch {
    return 'en';
  }
}

export const uiLanguage: UiLanguage = detectLanguage();
export const isFrench: boolean = uiLanguage === 'fr';

export function L(fr: string, en: string): string {
  return isFrench ? fr : en;
}

/** Chaînes UI centralisées (FR / EN). */
export const strings = {
  appName: 'Murmure',
  tagline: L('Tu parles. Murmure écrit.', 'You speak. Murmure writes.'),
  tabs: {
    home: L('Accueil', 'Home'),
    history: L('Historique', 'History'),
    settings: L('Réglages', 'Settings'),
  },
  home: {
    status: L('État', 'Status'),
    service: L("Service d'accessibilité", 'Accessibility service'),
    model: L('Modèle Whisper', 'Whisper model'),
    microphone: L('Micro', 'Microphone'),
    bubble: L('Bulle', 'Bubble'),
    enabled: L('Activée', 'On'),
    disabled: L('Désactivée', 'Off'),
    active: L('Actif', 'On'),
    inactive: L('Inactif', 'Off'),
    ready: L('Prêt', 'Ready'),
    missing: L('À télécharger', 'Not downloaded'),
    granted: L('Autorisé', 'Allowed'),
    denied: L('Refusé', 'Denied'),
    allReady: L(
      'Tout est prêt : ouvre n’importe quelle app, touche un champ de texte, puis la bulle orange.',
      'All set: open any app, tap a text field, then the orange bubble.'
    ),
    finishSetup: L('Terminer la configuration', 'Finish setup'),
    manageModel: L('Gérer le modèle', 'Manage model'),
    howTo: L(
      'Touche la bulle pour parler, touche-la à nouveau pour insérer le texte. Appui long pendant l’écoute pour annuler.',
      'Tap the bubble to speak, tap it again to insert the text. Long press while listening to cancel.'
    ),
    privacy: L(
      'Tout reste sur ton téléphone : la transcription est 100 % locale.',
      'Everything stays on your phone: transcription is 100% local.'
    ),
  },
  history: {
    search: L('Rechercher…', 'Search…'),
    empty: L('Aucune dictée pour le moment.', 'No dictations yet.'),
    noResult: L('Aucun résultat.', 'No results.'),
    copy: L('Copier', 'Copy'),
    copied: L('Copié', 'Copied'),
    delete: L('Supprimer', 'Delete'),
    clearAll: L('Tout effacer', 'Clear all'),
    clearTitle: L("Effacer l'historique ?", 'Clear history?'),
    clearMessage: L('Toutes les dictées seront supprimées de ce téléphone.', 'All dictations will be deleted from this phone.'),
    cancel: L('Annuler', 'Cancel'),
    words: (n: number) => (isFrench ? `${n} mot${n > 1 ? 's' : ''}` : `${n} word${n === 1 ? '' : 's'}`),
    outcome: {
      SET_TEXT: L('Inséré', 'Inserted'),
      PASTED: L('Collé', 'Pasted'),
      CLIPBOARD_ONLY: L('Presse-papiers', 'Clipboard'),
      NOTHING: L('Non inséré', 'Not inserted'),
    },
  },
  settings: {
    bubble: L('Bulle flottante', 'Floating bubble'),
    bubbleHint: L('Affiche la bulle quand un champ de texte est actif.', 'Shows the bubble when a text field is active.'),
    language: L('Langue de dictée', 'Dictation language'),
    languageAuto: L('Auto (FR/EN)', 'Auto (FR/EN)'),
    languageFr: L('Français', 'French'),
    languageEn: L('Anglais', 'English'),
    languageHint: L(
      'Une langue fixe est plus rapide (pas de détection). Murmure ne traduit jamais.',
      'A fixed language is faster (no detection). Murmure never translates.'
    ),
    model: L('Modèle', 'Model'),
    permissions: L('Autorisations', 'Permissions'),
    about: L('À propos', 'About'),
    aboutText: L(
      'Murmure transcrit ta voix avec Whisper (whisper.cpp), entièrement sur ton téléphone. Aucune donnée ne quitte l’appareil : la seule connexion sert à télécharger le modèle.',
      'Murmure transcribes your voice with Whisper (whisper.cpp), entirely on your phone. No data leaves the device: the only connection is used to download the model.'
    ),
    version: L('Version', 'Version'),
  },
  model: {
    title: L('Modèle Whisper', 'Whisper model'),
    intro: L(
      'Choisis le modèle utilisé pour la transcription. Small est plus précis, Base plus léger et plus rapide.',
      'Choose the transcription model. Small is more accurate, Base is lighter and faster.'
    ),
    mobileData: L(
      'Téléchargement volumineux : privilégie le Wi-Fi.',
      'Large download: prefer Wi-Fi.'
    ),
    download: L('Télécharger', 'Download'),
    resume: L('Reprendre', 'Resume'),
    cancel: L('Annuler', 'Cancel'),
    delete: L('Supprimer', 'Delete'),
    select: L('Utiliser', 'Use'),
    selected: L('Utilisé', 'In use'),
    installed: L('Installé', 'Installed'),
    required: L(
      'Le modèle sélectionné n’est pas installé : télécharge-le ou choisis-en un autre.',
      'The selected model is not installed: download it or pick another one.'
    ),
    deleteTitle: L('Supprimer le modèle ?', 'Delete model?'),
    errors: {
      NETWORK: L('Erreur réseau : le téléchargement reprendra où il s’est arrêté.', 'Network error: the download will resume where it stopped.'),
      CHECKSUM: L('Fichier corrompu (empreinte invalide) : recommence le téléchargement.', 'Corrupted file (checksum mismatch): please download again.'),
      INSUFFICIENT_STORAGE: L('Espace de stockage insuffisant.', 'Not enough storage space.'),
      CANCELLED: L('Téléchargement annulé.', 'Download cancelled.'),
      UNKNOWN: L('Le téléchargement a échoué.', 'Download failed.'),
    },
  },
  onboarding: {
    next: L('Continuer', 'Continue'),
    skip: L('Passer', 'Skip'),
    stepOf: (i: number, n: number) => L(`Étape ${i} sur ${n}`, `Step ${i} of ${n}`),
    welcomeTitle: L('Bienvenue dans Murmure', 'Welcome to Murmure'),
    welcomeBody: L(
      'Une bulle apparaît quand tu écris dans n’importe quelle app. Touche-la, parle : le texte s’insère tout seul. Tout est transcrit sur ton téléphone.',
      'A bubble appears whenever you type in any app. Tap it, speak: the text is inserted for you. Everything is transcribed on your phone.'
    ),
    start: L('Commencer', 'Get started'),
    micTitle: L('Micro', 'Microphone'),
    micBody: L('Murmure a besoin du micro pour entendre ta dictée. L’audio n’est jamais enregistré sur le disque ni envoyé.', 'Murmure needs the microphone to hear your dictation. Audio is never saved to disk or sent anywhere.'),
    micButton: L('Autoriser le micro', 'Allow microphone'),
    micDenied: L('Autorisation refusée : active-la dans les paramètres de l’app.', 'Permission denied: enable it in the app settings.'),
    openAppSettings: L('Ouvrir les paramètres de l’app', 'Open app settings'),
    notifTitle: L('Notifications', 'Notifications'),
    notifBody: L('Une notification discrète « Murmure écoute… » s’affiche pendant l’enregistrement.', 'A discreet “Murmure is listening…” notification is shown while recording.'),
    notifButton: L('Autoriser les notifications', 'Allow notifications'),
    overlayTitle: L('Affichage par-dessus les autres apps', 'Display over other apps'),
    overlayBody: L('Recommandé : aide Android à laisser Murmure utiliser le micro depuis les autres apps.', 'Recommended: helps Android let Murmure use the microphone from other apps.'),
    overlayButton: L('Ouvrir le réglage', 'Open setting'),
    restrictedTitle: L('Paramètres restreints', 'Restricted settings'),
    restrictedBody: L(
      "Android bloque l'accessibilité pour les apps installées hors Play Store. Ouvre Infos sur l'appli › menu ⋮ › Autoriser les paramètres restreints, puis reviens ici.",
      'Android blocks accessibility for apps installed outside the Play Store. Open App info › ⋮ menu › Allow restricted settings, then come back here.'
    ),
    restrictedNote: L(
      'Cette option n’apparaît qu’après une première tentative d’activation du service : essaie d’abord l’étape suivante, et reviens ici si le bouton est grisé.',
      'This option only appears after a first attempt to enable the service: try the next step first, and come back here if the switch is greyed out.'
    ),
    restrictedButton: L("Ouvrir Infos sur l'appli", 'Open App info'),
    a11yTitle: L("Service d'accessibilité", 'Accessibility service'),
    a11yBody: L(
      'Murmure s’en sert uniquement pour savoir qu’un champ de texte est actif (afficher la bulle) et y insérer ta dictée. Les champs mot de passe sont ignorés ; rien n’est stocké ni envoyé.',
      'Murmure only uses it to know when a text field is active (to show the bubble) and to insert your dictation. Password fields are ignored; nothing is stored or sent.'
    ),
    a11yPath: L('Dans la liste : Applications téléchargées › Murmure › activer.', 'In the list: Downloaded apps › Murmure › turn on.'),
    a11yButton: L("Ouvrir les réglages d'accessibilité", 'Open accessibility settings'),
    a11yGreyed: L('Le bouton est grisé ?', 'Switch greyed out?'),
    modelTitle: L('Modèle de transcription', 'Transcription model'),
    doneTitle: L('C’est prêt !', 'All set!'),
    doneBody: L(
      'Ouvre une app, touche un champ de texte : la bulle orange apparaît. Tu parles, Murmure écrit.',
      'Open an app, tap a text field: the orange bubble appears. You speak, Murmure writes.'
    ),
    finish: L('Terminer', 'Finish'),
  },
};
