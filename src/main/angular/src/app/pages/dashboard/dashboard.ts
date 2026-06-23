import {Component} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslatePipe} from '@ngx-translate/core';
import {AppHeader} from '../../components/app-header/app-header';
import {AppFooter} from '../../components/app-footer/app-footer';

/**
 * The washa portal landing page: a launcher for the household's apps. Budget is the first; the grid
 * is built to grow as washa adds more (the user display + sign-out live in the shared app-header).
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, TranslatePipe, AppHeader, AppFooter],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
}
