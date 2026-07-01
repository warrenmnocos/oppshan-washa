import {Injectable, signal} from '@angular/core';

/**
 * Mediates pull-to-refresh between the shell that owns the scroll container and touch gesture and
 * whatever content is currently refreshable. A refreshable view registers a refresh action on init and
 * unregisters on destroy; the shell calls trigger() once the user pulls past the threshold.
 *
 * enabled() gates the shell's indicator so it shows only where a refresh action is registered; active()
 * reflects an in-flight reload. The registrant mirrors its own loading state into active(), so the
 * spinner tracks the reload and stops when it settles.
 */
@Injectable({providedIn: 'root'})
export class PullRefresh {

  /** The registered refresh callback, or null when nothing is currently refreshable. */
  private onRefresh: (() => void) | null = null;

  /** Whether a refresh action is registered; gates the shell's pull affordance. */
  readonly enabled = signal(false);
  /** Whether a reload is in flight; drives the spinner. */
  readonly active = signal(false);

  /** Register a refresh callback and enable the gesture (called by a refreshable view on init). */
  register(onRefresh: () => void): void {
    this.onRefresh = onRefresh;
    this.enabled.set(true);
  }

  /** Clear the callback and reset both flags (called on the refreshable view's destroy). */
  unregister(): void {
    this.onRefresh = null;
    this.enabled.set(false);
    this.active.set(false);
  }

  /** Invoke the registered refresh callback, if any (called once the user pulls past the threshold). */
  trigger(): void {
    this.onRefresh?.();
  }
}
