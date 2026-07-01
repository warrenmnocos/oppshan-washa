import {Component} from '@angular/core';
import {RouterOutlet} from '@angular/router';

/**
 * Root application component that Angular bootstraps into the page. It's a bare router outlet:
 * everything the router resolves renders through here, and the root owns no chrome or state itself.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {
}
