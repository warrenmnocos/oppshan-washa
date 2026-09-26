import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {DebtDialog} from './debt-dialog';
import {Debt} from '../../models/budget.models';
import {DebtRepriceMode} from '../../models/debt-reprice-mode';

function debt(): Debt {
    return {
        name: 'Home mortgage', principal: 5000000, annualRate: 6.5, monthly: 38000, termMonths: 240,
        repriceMode: DebtRepriceMode.Payment, cur: 'PHP', prepay: false, prepayAmt: 0, rateSteps: [],
    };
}

describe('DebtDialog', () => {

    beforeEach(() => {
        TestBed.configureTestingModule({providers: [provideTranslateService({lang: 'en'})]});
    });

    function mount(): ComponentFixture<DebtDialog> {
        const fixture = TestBed.createComponent(DebtDialog);
        fixture.componentRef.setInput('debt', debt());
        fixture.componentRef.setInput('currencies', [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}]);
        fixture.detectChanges();
        return fixture;
    }

    it('should add and edit rate steps on the draft without mutating the input', () => {
        const fixture = mount();
        const original = debt();
        fixture.componentInstance.addRateStep();
        fixture.componentInstance.setRateStep(0, 'afterYears', 3);
        fixture.componentInstance.setRateStep(0, 'rate', 5.75);
        expect(original.rateSteps.length).toBe(0);
        const steps = fixture.componentInstance.draft().rateSteps;
        expect(steps.length).toBe(1);
        expect(steps[0]).toEqual({afterYears: 3, rate: 5.75});
    });

    it('should toggle reprice mode', () => {
        const fixture = mount();
        fixture.componentInstance.setRepriceMode(DebtRepriceMode.Term);
        expect(fixture.componentInstance.draft().repriceMode).toBe(DebtRepriceMode.Term);
    });

    it('should toggle the prepayment flag without exposing amount or currency fields', () => {
        const fixture = mount();
        // The dialog carries only the Yes/No prepayment toggle (the prototype); the amount and currency
        // are edited inline on the Money-out prepayment sub-row, so they are not fields here.
        fixture.componentInstance.setPrepay(true);
        expect(fixture.componentInstance.draft().prepay).toBe(true);
        expect((fixture.componentInstance as unknown as { setPrepayAmount?: unknown }).setPrepayAmount).toBeUndefined();
        expect((fixture.componentInstance as unknown as {
            setPrepayCurrency?: unknown
        }).setPrepayCurrency).toBeUndefined();
    });

    it('should hide the interest fields and relabel the principal for an interest-free debt', () => {
        const fixture = mount();
        const host = fixture.nativeElement as HTMLElement;
        expect(host.textContent).toContain('budget.debt.annualRate');

        fixture.componentInstance.setInterestFree(true);
        fixture.detectChanges();

        // The translate pipe echoes keys with no JSON loaded (B.7), so assert on the keys.
        expect(host.textContent).toContain('budget.debt.amountBorrowed');
        expect(host.textContent).toContain('budget.debt.interestFreeHint');
        expect(host.textContent).not.toContain('budget.debt.annualRate');
        expect(host.textContent).not.toContain('budget.debt.termYears');
        expect(host.textContent).not.toContain('budget.debt.rateSteps');
        expect(host.textContent).not.toContain('budget.debt.annualPrepayment');
    });

    it('should drop the rate, term, steps, and prepayment when saving an interest-free debt', () => {
        const fixture = mount();
        const emitted: Debt[] = [];
        fixture.componentInstance.saved.subscribe((d) => emitted.push(d));
        fixture.componentInstance.addRateStep();
        fixture.componentInstance.setPrepay(true);

        fixture.componentInstance.setInterestFree(true);
        fixture.componentInstance.save();

        expect(emitted[0].interestFree).toBe(true);
        expect(emitted[0].annualRate).toBe(0);
        expect(emitted[0].termMonths).toBeUndefined();
        expect(emitted[0].rateSteps).toEqual([]);
        expect(emitted[0].prepay).toBe(false);
        expect(emitted[0].prepayAmt).toBe(0);
        expect(emitted[0].principal).toBe(5000000); // the amount borrowed is kept
    });

    it('should keep the interest settings when switched back before saving', () => {
        const fixture = mount();
        const emitted: Debt[] = [];
        fixture.componentInstance.saved.subscribe((d) => emitted.push(d));

        fixture.componentInstance.setInterestFree(true);
        fixture.componentInstance.setInterestFree(false);
        fixture.componentInstance.save();

        expect(emitted[0].interestFree).toBe(false);
        expect(emitted[0].annualRate).toBe(6.5);
        expect(emitted[0].termMonths).toBe(240);
    });

    it('should emit the edited debt on save with a defaulted name', () => {
        const fixture = mount();
        const emitted: Debt[] = [];
        fixture.componentInstance.saved.subscribe((d) => emitted.push(d));
        fixture.componentInstance.setName('  ');
        fixture.componentInstance.save();
        expect(emitted[0].name).toBe('Debt');
    });
});
