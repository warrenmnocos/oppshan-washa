import {Component} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslatePipe} from '@ngx-translate/core';

/**
 * The washa portal landing page: a launcher for the household's apps. Renders inside the shared
 * AppShell (which provides the fixed header and footer), so this is just the page content. Budget
 * is the first app; the grid is built to grow as washa adds more.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
}
