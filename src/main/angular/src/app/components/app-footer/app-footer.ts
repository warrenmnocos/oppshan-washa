import {Component} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';

/** Shared portal footer (mirrors files.oppshan.com): tagline, domain link, and an attribution line. */
@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './app-footer.html',
  styleUrl: './app-footer.scss',
})
export class AppFooter {

  readonly year = new Date().getFullYear();
}
