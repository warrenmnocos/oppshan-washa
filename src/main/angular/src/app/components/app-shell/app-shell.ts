import {AfterViewInit, Component, computed, effect, ElementRef, inject, OnDestroy, signal, viewChild} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {AppHeader} from '../app-header/app-header';
import {AppFooter} from '../app-footer/app-footer';
import {PullRefresh} from '../../services/pull-refresh';

/**
 * The signed-in portal frame: a fixed header and footer with a single scrolling content region
 * between them. Used as the parent route for every in-app page (dashboard, budget, …) so the chrome
 * stays put — and is retained — when navigating between apps. The sign-in page sits outside this
 * shell (it is its own full-screen layout).
 *
 * The shell also owns the mobile pull-to-refresh gesture: pulling down past a threshold while the
 * content is scrolled to the top reloads whichever page registered with PullRefresh (currently the
 * budget page). preventDefault on the pull suppresses the native rubber-band so the indicator drives
 * the motion; the gesture is inert on pages that didn't register. The app is zoneless, so the raw
 * touch listeners just write signals and change detection follows.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, AppHeader, AppFooter],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell implements AfterViewInit, OnDestroy {

  readonly pullRefresh = inject(PullRefresh);

  private readonly scroller = viewChild.required<ElementRef<HTMLElement>>('scroller');

  // Live pull distance (px, damped) and whether a pull-triggered reload is currently spinning.
  readonly pullDistance = signal(0);
  readonly spinning = signal(false);

  // The indicator reveals proportionally to the pull (full once spinning); its icon rotates as you pull.
  readonly indicatorOpacity = computed(() =>
      this.spinning() ? 1 : Math.min(this.pullDistance() / AppShell.THRESHOLD, 1));
  readonly indicatorAngle = computed(() => Math.round(this.pullDistance() * 2.4));

  private static readonly THRESHOLD = 64;
  private static readonly MAX = 96;
  private static readonly RESISTANCE = 0.5;

  private startY = 0;
  private pulling = false;
  private wasActive = false;
  private teardown: Array<() => void> = [];

  constructor() {
    // Stop the spinner once a pull-triggered reload finishes (active goes true, then false).
    effect(() => {
      const active = this.pullRefresh.active();
      if (this.wasActive && !active) {
        this.spinning.set(false);
      }
      this.wasActive = active;
    });
  }

  ngAfterViewInit(): void {
    const el = this.scroller().nativeElement;

    const onStart = (event: TouchEvent) => {
      if (!this.pullRefresh.enabled() || this.spinning() || el.scrollTop > 0) {
        return;
      }
      this.startY = event.touches[0].clientY;
      this.pulling = true;
    };
    const onMove = (event: TouchEvent) => {
      if (!this.pulling) {
        return;
      }
      const delta = event.touches[0].clientY - this.startY;
      if (delta <= 0 || el.scrollTop > 0) {
        this.pulling = false;
        this.pullDistance.set(0);
        return;
      }
      event.preventDefault(); // suppress the native rubber-band so our indicator owns the pull
      this.pullDistance.set(Math.min(delta * AppShell.RESISTANCE, AppShell.MAX));
    };
    const onEnd = () => {
      if (!this.pulling) {
        return;
      }
      this.pulling = false;
      const shouldRefresh = this.pullDistance() >= AppShell.THRESHOLD;
      this.pullDistance.set(0);
      if (shouldRefresh) {
        this.spinning.set(true);
        this.pullRefresh.trigger();
      }
    };

    el.addEventListener('touchstart', onStart, {passive: true});
    el.addEventListener('touchmove', onMove, {passive: false});
    el.addEventListener('touchend', onEnd, {passive: true});
    el.addEventListener('touchcancel', onEnd, {passive: true});
    this.teardown = [
      () => el.removeEventListener('touchstart', onStart),
      () => el.removeEventListener('touchmove', onMove),
      () => el.removeEventListener('touchend', onEnd),
      () => el.removeEventListener('touchcancel', onEnd),
    ];
  }

  ngOnDestroy(): void {
    this.teardown.forEach((remove) => remove());
  }
}
