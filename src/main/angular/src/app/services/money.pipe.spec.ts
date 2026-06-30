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
});
