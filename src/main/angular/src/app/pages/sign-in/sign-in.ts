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

  /**
   * The MessageCode a server redirect asked us to show (denied or failed sign-in), or null on a clean
   * visit. Resolved once in the constructor; the template translates it into a banner.
   */
  readonly messageKey: MessageCode | null;

  /**
   * Reads the ?message code the OIDC error/denied redirect carries and resolves it through the enum,
   * so only codes we recognize render; anything else falls to null and shows no banner.
   */
  constructor() {
    const raw = new URLSearchParams(window.location.search).get('message');
    this.messageKey = raw ? messageCodeOf(raw) : null;
  }

  /**
   * Sends the browser to the backend's Google OIDC trigger as a full-page navigation (not an XHR), so
   * the provider redirects and the cookie round-trip work; the backend drives the rest of the flow.
   */
  signIn(): void {
    window.location.href = '/sso/sign-in/oidc/google';
  }
}
