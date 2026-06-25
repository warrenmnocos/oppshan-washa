import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {CurrencyPicker} from './currency-picker';
import {Currency} from '../../models/budget.models';

const JPY: Currency = {code: 'JPY', sym: '¥'};
const PHP: Currency = {code: 'PHP', sym: '₱'};
const USD: Currency = {code: 'USD', sym: '$'};

describe('CurrencyPicker', () => {

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideTranslateService({lang: 'en'})]});
  });

  function mount(currencies: Currency[],
                 selected: string): ComponentFixture<CurrencyPicker> {
    const fixture = TestBed.createComponent(CurrencyPicker);
    fixture.componentRef.setInput('currencies', currencies);
    fixture.componentRef.setInput('selected', selected);
    fixture.detectChanges();
    return fixture;
  }

  it('should render a read-only glyph and no controls for a single currency', () => {
    const host = mount([JPY], 'JPY').nativeElement as HTMLElement;
    const glyph = host.querySelector('.curone') as HTMLElement;
    expect(glyph).toBeTruthy();
    // The glyph shows the currency symbol, and there are no interactive controls to pick from.
    expect(glyph.textContent?.trim()).toBe('¥');
    expect(host.querySelector('.curtog')).toBeNull();
    expect(host.querySelectorAll('button').length).toBe(0);
    expect(host.querySelector('select')).toBeNull();
  });

  it('should fall back to the selected code in the glyph when the currency has no symbol', () => {
    // A code with no matching currency record: the glyph still labels the single choice.
    const host = mount([], 'JPY').nativeElement as HTMLElement;
    const glyph = host.querySelector('.curone') as HTMLElement;
    expect(glyph).toBeTruthy();
    expect(glyph.textContent?.trim()).toBe('JPY');
  });

  it('should render a two-button toggle with the selected one pressed for two currencies', () => {
    const host = mount([JPY, PHP], 'PHP').nativeElement as HTMLElement;
    const toggle = host.querySelector('.curtog');
    expect(toggle).toBeTruthy();
    expect(host.querySelector('select')).toBeNull();

    const buttons = Array.from(toggle!.querySelectorAll('button'));
    expect(buttons.length).toBe(2);
    // Exactly the selected currency's button is pressed, and the buttons carry the code as a title.
    const pressed = buttons.filter((button) => button.getAttribute('aria-pressed') === 'true');
    expect(pressed.length).toBe(1);
    expect(pressed[0].getAttribute('title')).toBe('PHP');
    expect(pressed[0].textContent?.trim()).toBe('₱');
  });

  it('should render a select with an option per currency for three or more', () => {
    const host = mount([JPY, PHP, USD], 'JPY').nativeElement as HTMLElement;
    const select = host.querySelector('select.cursel') as HTMLSelectElement;
    expect(select).toBeTruthy();
    expect(host.querySelector('.curtog')).toBeNull();
    expect(host.querySelector('.curone')).toBeNull();

    const options = Array.from(select.querySelectorAll('option'));
    expect(options.length).toBe(3);
    expect(options.map((option) => option.getAttribute('value'))).toEqual(['JPY', 'PHP', 'USD']);
  });

  it('should emit the picked code when a toggle button is clicked', () => {
    const fixture = mount([JPY, PHP], 'JPY');
    const emitted: string[] = [];
    fixture.componentInstance.changed.subscribe((code) => emitted.push(code));

    const phpButton = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.curtog button'))
        .find((button) => button.getAttribute('title') === 'PHP') as HTMLButtonElement;
    phpButton.click();
    expect(emitted).toEqual(['PHP']);
  });

  it('should emit the picked code when the select value changes', () => {
    const fixture = mount([JPY, PHP, USD], 'JPY');
    const emitted: string[] = [];
    fixture.componentInstance.changed.subscribe((code) => emitted.push(code));

    const select = (fixture.nativeElement as HTMLElement).querySelector('select.cursel') as HTMLSelectElement;
    select.value = 'USD';
    select.dispatchEvent(new Event('change'));
    expect(emitted).toEqual(['USD']);
  });
});
