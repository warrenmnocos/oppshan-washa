import {MoneyPipe} from './money.pipe';

describe('MoneyPipe', () => {

    const pipe = new MoneyPipe();

    it('should round to whole units and group thousands', () => {
        expect(pipe.transform(1234567.89, {code: 'JPY', sym: '¥'})).toBe('¥1,234,568');
    });

    it('should prefix the currency symbol from a Currency object', () => {
        expect(pipe.transform(360, {code: 'PHP', sym: '₱'})).toBe('₱360');
    });

    it('should accept a plain symbol string', () => {
        expect(pipe.transform(1000, '¥')).toBe('¥1,000');
    });

    it('should treat null or undefined as zero', () => {
        expect(pipe.transform(null)).toBe('0');
        expect(pipe.transform(undefined)).toBe('0');
    });

    // Each case: the figure, the currency, and what the page must show for it.
    it.each([
        [0, 'JPY', '¥0'],
        [0.49, 'JPY', '¥0'],
        [0.5, 'JPY', '¥1'],
        [2.5, 'JPY', '¥3'],
        [-2.5, 'JPY', '¥-3'],
        [-0.4, 'JPY', '¥0'],
        [-0.5, 'JPY', '¥-1'],
        [-1234.5, 'PHP', '₱-1,235'],
        [1234.5, 'PHP', '₱1,235'],
        [999.5, 'USD', '$1,000'],
        [-999.49, 'USD', '$-999'],
        [0.35 / 0.1, 'EUR', '€4'], // 3.4999999999999996 in floating point; really 3.5
        [4.35 / 0.1, 'EUR', '€44'], // 43.49999999999999; really 43.5
        [1.15 * 100, 'GBP', '£115'], // 114.99999999999999
        [2.675 * 100, 'GBP', '£268'], // 267.49999999999997
        [1.005 * 1000, 'KRW', '₩1,005'], // 1004.9999999999999
        [2.4999999999999, 'JPY', '¥2'], // genuinely under a half: stays down
        [987654321098.5, 'VND', '₫987,654,321,099'],
        [-987654321098.5, 'VND', '₫-987,654,321,099'],
        [12345678.5, 'IDR', 'Rp12,345,679'],
        [0.0004, 'KWD', 'KWD0'],
    ])('should show %s %s as %s', (amount, currency, shown) => {
        expect(pipe.transform(amount, currency)).toBe(shown);
    });
});
