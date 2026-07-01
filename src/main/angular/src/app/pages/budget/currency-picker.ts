import {Component, input, output} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {Currency} from '../../models/budget.models';

/**
 * Reusable currency selector matching the prototype's {@code curSel}: a read-only glyph for a single
 * currency, a two-button ¥/₱ toggle for two, and a dropdown for three or more (past two buttons a
 * toggle gets unwieldy). Emits the chosen currency code via `changed`.
 */
@Component({
  selector: 'app-currency-picker',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './currency-picker.html',
})
export class CurrencyPicker {
  /** The currencies to choose from; their count decides which of the three forms renders. */
  readonly currencies = input.required<Currency[]>();
  /** The currently selected currency code, highlighted in the picker. */
  readonly selected = input.required<string>();
  /**
   * Picks the wider symbol+code form used inside modal dialogs (the prototype's curFieldHTML: "¥ JPY"
   * toggle buttons, "¥ · JPY" options, .dlgtog / .cursel-dlg sizing). Left false, it keeps the compact
   * symbol-only form (the prototype's curSel).
   */
  readonly dialog = input<boolean>(false);
  /** Emits the currency code the user picked. */
  readonly changed = output<string>();

  /** Emit the picked currency code. */
  pick(code: string): void {
    this.changed.emit(code);
  }
}
