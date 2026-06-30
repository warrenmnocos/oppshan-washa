import {Component, input, output} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {Currency} from '../../models/budget.models';

/**
 * Currency selector matching the prototype's {@code curSel}: a read-only glyph when there is a
 * single currency, a two-button ¥/₱ toggle for two, and a dropdown for three or more (a toggle past
 * two buttons gets unwieldy). Emits the chosen currency code. Reused on the money-out rows and in
 * the edit dialogs so the picker behaves identically everywhere.
 */
@Component({
  selector: 'app-currency-picker',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './currency-picker.html',
})
export class CurrencyPicker {
  readonly currencies = input.required<Currency[]>();
  readonly selected = input.required<string>();
  // Dialog variant: the modal dialogs render the wider symbol+code form (the prototype's
  // curFieldHTML — "¥ JPY" toggle buttons, "¥ · JPY" options, .dlgtog / .cursel-dlg sizing). The
  // inline money-out/money-in rows keep the compact symbol-only form (the prototype's curSel).
  readonly dialog = input<boolean>(false);
  readonly changed = output<string>();

  pick(code: string): void {
    this.changed.emit(code);
  }
}
