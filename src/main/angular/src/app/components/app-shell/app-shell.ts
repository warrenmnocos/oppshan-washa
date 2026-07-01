import {AfterViewInit, Component, computed, effect, ElementRef, inject, OnDestroy, signal, viewChild} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {AppHeader} from '../app-header/app-header';
import {AppFooter} from '../app-footer/app-footer';
import {PullRefresh} from '../../services/pull-refresh';

/**
 * The signed-in portal frame: a fixed header and footer with a single scrolling content region
 * between them. It's the parent route for the in-app pages, so the chrome stays mounted and keeps its
 * scroll position as you move between them.
 *
 * The shell also owns the mobile pull-to-refresh gesture. Pulling down past a threshold while the
 * content is scrolled to the top reloads whichever page registered with PullRefresh; the gesture is
 * inert on pages that didn't register. We preventDefault on the pull so the native rubber-band doesn't
 * fight the indicator. The app is zoneless, so the raw touch listeners just write signals and change
 * detection follows.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, AppHeader, AppFooter],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell implements AfterViewInit, OnDestroy {

  /**
   * Coordination point with the refreshable page: enabled() gates the gesture (only a registered page
   * arms it), active() reflects an in-flight reload so we can stop the spinner, and trigger() runs the
   * page's registered refresh action.
   */
  readonly pullRefresh = inject(PullRefresh);

  /** The scrolling content element; the touch listeners attach here and read scrollTop to tell we're at the top. */
  private readonly scroller = viewChild.required<ElementRef<HTMLElement>>('scroller');

  /** Live pull distance in px, damped by RESISTANCE and capped at MAX; the template positions the indicator from it. */
  readonly pullDistance = signal(0);

  /** True while a pull-triggered reload is in flight, so the indicator stays fully revealed and spinning. */
  readonly spinning = signal(false);

  /** Indicator opacity: ramps 0→1 across the pull toward the threshold, then pinned full while spinning. */
  readonly indicatorOpacity = computed(() =>
      this.spinning() ? 1 : Math.min(this.pullDistance() / AppShell.THRESHOLD, 1));

  /** Indicator icon rotation in degrees; grows with the pull to give the gesture tactile feedback. */
  readonly indicatorAngle = computed(() => Math.round(this.pullDistance() * 2.4));

  /** Pull distance (px) that arms a refresh on release and counts as a full indicator reveal. */
  private static readonly THRESHOLD = 64;

  /** Hard cap (px) on how far the indicator travels, however far the finger drags. */
  private static readonly MAX = 96;

  /** Damping factor on raw finger travel, so the pull feels weighted rather than 1:1. */
  private static readonly RESISTANCE = 0.5;

  /** clientY where the current pull began; move deltas are measured from it. */
  private startY = 0;

  /** True between touchstart and release while a valid downward pull is tracking. */
  private pulling = false;

  /** Previous pullRefresh.active() value, so the effect fires the spinner-off only on the true→false edge. */
  private wasActive = false;

  /** Listener removers captured in ngAfterViewInit and run on destroy to detach the manual touch handlers. */
  private teardown: Array<() => void> = [];

  /**
   * Wires an effect that watches pullRefresh.active(): when a reload flips it true then back to false,
   * the spinner stops. Tracking the previous value via wasActive is what lets us fire on that falling
   * edge rather than on every emission.
   */
  constructor() {
    effect(() => {
      const active = this.pullRefresh.active();
      if (this.wasActive && !active) {
        this.spinning.set(false);
      }
      this.wasActive = active;
    });
  }

  /**
   * Attaches the pull-to-refresh touch listeners to the scroll container once it exists. touchstart
   * arms a pull only when refresh is enabled, nothing is already spinning, and we're scrolled to the
   * top; touchmove tracks the damped distance and calls preventDefault to suppress the native
   * rubber-band so the indicator owns the motion; release fires the refresh if the pull cleared the
   * threshold. Only touchmove is non-passive (it preventDefaults); the rest stay passive. The remover
   * closures are stashed in teardown for ngOnDestroy.
   */
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
      event.preventDefault();
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

  /** Detaches every touch listener added in ngAfterViewInit, so nothing leaks once the shell tears down. */
  ngOnDestroy(): void {
    this.teardown.forEach((remove) => remove());
  }
}
