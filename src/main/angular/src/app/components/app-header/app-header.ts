import {Component, inject, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslatePipe} from '@ngx-translate/core';
import {BudgetApiService} from '../../services/budget-api.service';
import {Me} from '../../models/budget.models';

/**
 * The washa portal top bar (shared shell, mirrors files.oppshan.com's toolbar): brand mark + word
 * mark on the left, the signed-in person and a sign-out control on the right. Self-fetches the
 * current user so any page can drop {@code <app-header/>} in.
 */
@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  templateUrl: './app-header.html',
  styleUrl: './app-header.scss',
})
export class AppHeader {

  /** HTTP client used to fetch the signed-in user for the right-hand identity display. */
  private readonly api = inject(BudgetApiService);

  /** The signed-in user, or null until /api/me resolves (and if the lookup fails); the template renders from it. */
  readonly user = signal<Me | null>(null);

  /** Fetches the current user on construction; a failed lookup just leaves the signal null instead of erroring. */
  constructor() {
    this.api.me().subscribe({next: (me) => this.user.set(me), error: () => this.user.set(null)});
  }
}
