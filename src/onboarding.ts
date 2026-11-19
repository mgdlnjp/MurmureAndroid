import type { PermissionStatus } from '@/modules/murmure-native';

export type StepId =
  | 'welcome'
  | 'microphone'
  | 'notifications'
  | 'overlay'
  | 'restricted'
  | 'accessibility'
  | 'model'
  | 'done';

export const ONBOARDING_STEPS: StepId[] = [
  'welcome',
  'microphone',
  'notifications',
  'overlay',
  'restricted',
  'accessibility',
  'model',
  'done',
];

/** Étapes passables (non bloquantes). */
export const SKIPPABLE_STEPS: ReadonlySet<StepId> = new Set<StepId>(['notifications', 'overlay', 'restricted']);

/**
 * Première étape encore à faire, en ignorant les étapes passables déjà vues/passées ([skipped]).
 * Ordre : welcome → microphone → notifications (API 33+) → overlay → restricted (API 33+, si accessibilité off)
 * → accessibility → model → done.
 */
export function nextOnboardingStep(
  s: PermissionStatus,
  modelInstalled: boolean,
  seenWelcome: boolean,
  skipped: ReadonlySet<StepId> = new Set()
): StepId {
  if (!seenWelcome) return 'welcome';
  if (!s.microphone) return 'microphone';
  if (s.sdkInt >= 33 && !s.notifications && !skipped.has('notifications')) return 'notifications';
  if (!s.overlay && !skipped.has('overlay')) return 'overlay';
  if (s.sdkInt >= 33 && !s.accessibility && !skipped.has('restricted')) return 'restricted';
  if (!s.accessibility) return 'accessibility';
  if (!modelInstalled) return 'model';
  return 'done';
}

export function isReady(s: PermissionStatus, modelInstalled: boolean): boolean {
  return s.microphone && s.accessibility && modelInstalled;
}
