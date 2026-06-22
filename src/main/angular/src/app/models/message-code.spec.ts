import {MessageCode, messageCodeOf} from './message-code';

describe('messageCodeOf', () => {

  it('should resolve a known code string to its enum value', () => {
    expect(messageCodeOf('messages.errors.accessDenied')).toBe(MessageCode.AccessDenied);
  });

  it('should fall back to Unknown for an unrecognized code', () => {
    expect(messageCodeOf('messages.errors.nope')).toBe(MessageCode.Unknown);
  });

  it('should fall back to Unknown for null or empty input', () => {
    expect(messageCodeOf(null)).toBe(MessageCode.Unknown);
    expect(messageCodeOf('')).toBe(MessageCode.Unknown);
  });
});
