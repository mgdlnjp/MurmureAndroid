import type { PermissionStatus } from '@/modules/murmure-native';
import { isReady, nextOnboardingStep, ONBOARDING_STEPS, type StepId } from '@/src/onboarding';

const none = (sdkInt: number): PermissionStatus => ({
  microphone: false,
  overlay: false,
  accessibility: false,
  notifications: sdkInt < 33,
  sdkInt,
});

const all = (sdkInt: number): PermissionStatus => ({
  microphone: true,
  overlay: true,
  accessibility: true,
  notifications: true,
  sdkInt,
});

describe('ONBOARDING_STEPS', () => {
  it('has the expected order', () => {
    expect(ONBOARDING_STEPS).toEqual([
      'welcome',
      'microphone',
      'notifications',
      'overlay',
      'restricted',
      'accessibility',
      'model',
      'done',
    ]);
  });
});

describe('nextOnboardingStep (Android 13+, sdk 33)', () => {
  const sdk = 33;
  it('walks through every step', () => {
    let s = none(sdk);
    expect(nextOnboardingStep(s, false, false)).toBe('welcome');
    expect(nextOnboardingStep(s, false, true)).toBe('microphone');
    s = { ...s, microphone: true };
    expect(nextOnboardingStep(s, false, true)).toBe('notifications');
    s = { ...s, notifications: true };
    expect(nextOnboardingStep(s, false, true)).toBe('overlay');
    s = { ...s, overlay: true };
    expect(nextOnboardingStep(s, false, true)).toBe('restricted');
    expect(nextOnboardingStep(s, false, true, new Set<StepId>(['restricted']))).toBe('accessibility');
    s = { ...s, accessibility: true };
    expect(nextOnboardingStep(s, false, true)).toBe('model');
    expect(nextOnboardingStep(s, true, true)).toBe('done');
  });

  it('lets optional steps be skipped', () => {
    const s = { ...none(sdk), microphone: true };
    const skipped = new Set<StepId>(['notifications', 'overlay']);
    expect(nextOnboardingStep(s, false, true, skipped)).toBe('restricted');
  });

  it('never skips microphone or accessibility', () => {
    const skipped = new Set<StepId>(['microphone', 'accessibility', 'restricted', 'notifications', 'overlay']);
    expect(nextOnboardingStep(none(sdk), false, true, skipped)).toBe('microphone');
    expect(nextOnboardingStep({ ...none(sdk), microphone: true }, false, true, skipped)).toBe('accessibility');
  });
});

describe('nextOnboardingStep (Android 12L, sdk 32)', () => {
  const sdk = 32;
  it('skips notifications and restricted steps', () => {
    let s = { ...none(sdk), microphone: true };
    expect(nextOnboardingStep(s, false, true)).toBe('overlay');
    s = { ...s, overlay: true };
    expect(nextOnboardingStep(s, false, true)).toBe('accessibility');
    s = { ...s, accessibility: true };
    expect(nextOnboardingStep(s, false, true)).toBe('model');
    expect(nextOnboardingStep(all(sdk), true, true)).toBe('done');
  });

  it('ignores notification permission below 33', () => {
    const s = { ...none(sdk), microphone: true, notifications: false };
    expect(nextOnboardingStep(s, false, true)).toBe('overlay');
  });
});

describe('isReady', () => {
  it('requires microphone, accessibility and model', () => {
    expect(isReady(all(34), true)).toBe(true);
    expect(isReady(all(34), false)).toBe(false);
    expect(isReady({ ...all(34), microphone: false }, true)).toBe(false);
    expect(isReady({ ...all(34), accessibility: false }, true)).toBe(false);
    expect(isReady({ ...all(34), overlay: false, notifications: false }, true)).toBe(true);
  });
});
