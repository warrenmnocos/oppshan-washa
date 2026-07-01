import {Injectable, signal} from '@angular/core';

/**
 * Coordinates pull-to-refresh between the app-shell — which owns the scroll container and the touch
 * gesture — and whichever page is refreshable. A page registers a refresh action on init and
 * unregisters on destroy; the shell calls trigger() when the user pulls past the threshold.
 *
 * The shell shows its indicator only while enabled() (so the dashboard and sign-in never show it),
 * and spins while active(). The registering page mirrors its own loading state into active(), so the
 * spinner tracks the reload and stops when it settles.
 */
@Injectable({providedIn: 'root'})
export class PullRefresh {

  private onRefresh: (() => void) | null = null;

  readonly enabled = signal(false);
  readonly active = signal(false);

  register(onRefresh: () => void): void {
    this.onRefresh = onRefresh;
    this.enabled.set(true);
  }

  unregister(): void {
    this.onRefresh = null;
    this.enabled.set(false);
    this.active.set(false);
  }

  trigger(): void {
    this.onRefresh?.();
  }
}
