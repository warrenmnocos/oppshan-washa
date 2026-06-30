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

  private readonly api = inject(BudgetApiService);

  readonly user = signal<Me | null>(null);

  constructor() {
    this.api.me().subscribe({next: (me) => this.user.set(me), error: () => this.user.set(null)});
  }
}
