import {Component, inject, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {BudgetApiService} from '../../services/budget-api.service';
import {Me} from '../../models/budget.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {

  private readonly api = inject(BudgetApiService);

  readonly user = signal<Me | null>(null);

  constructor() {
    this.api.me().subscribe({next: (me) => this.user.set(me)});
  }
}
