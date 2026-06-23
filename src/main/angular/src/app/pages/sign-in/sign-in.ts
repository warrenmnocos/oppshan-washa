import {Component} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {MessageCode, messageCodeOf} from '../../models/message-code';

/**
 * Public, no-session sign-in page for the washa portal, routed at /sso/sign-in (the guard sends
 * signed-out visitors here; the OIDC error/denied redirects land here with ?message=<code>). The
 * button does a full-page navigation to the backend OIDC trigger, which begins the Google code
 * flow. Copy is portal-generic — washa may host apps beyond the budget, so nothing here is
 * budget-specific.
 */
@Component({
  selector: 'app-sign-in',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './sign-in.html',
  styleUrl: './sign-in.scss',
})
export class SignIn {

  readonly messageKey: MessageCode | null;

  constructor() {
    // Server redirects (denied/failed sign-in) carry a MessageCode in ?message; resolve it through
    // the enum so only known codes render, then translate it in the template.
    const raw = new URLSearchParams(window.location.search).get('message');
    this.messageKey = raw ? messageCodeOf(raw) : null;
  }

  signIn(): void {
    window.location.href = '/sso/sign-in/oidc/google';
  }
}
