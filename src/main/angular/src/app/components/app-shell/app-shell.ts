import {Component} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {AppHeader} from '../app-header/app-header';
import {AppFooter} from '../app-footer/app-footer';

/**
 * The signed-in portal frame: a fixed header and footer with a single scrolling content region
 * between them. Used as the parent route for every in-app page (dashboard, budget, …) so the chrome
 * stays put — and is retained — when navigating between apps. The sign-in page sits outside this
 * shell (it is its own full-screen layout).
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, AppHeader, AppFooter],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
}
