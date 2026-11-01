import { describe, expect, it } from 'vitest';
import { inr, lakh, initials, matchLabel, waited, whyChips } from './format';

describe('format', () => {
  it('groups rupees the Indian way', () => {
    expect(inr(1240000)).toBe('₹12,40,000');
    expect(inr(450000)).toBe('₹4,50,000');
    expect(inr(999)).toBe('₹999');
    expect(inr(-5000)).toBe('-₹5,000');
    expect(inr(null)).toBe('—');
  });
  it('formats lakh', () => expect(lakh(4100000)).toBe('₹41.0 L'));
  it('makes initials', () => expect(initials('Priya Kulkarni')).toBe('PK'));
  it('describes waiting time', () => {
    const now = new Date('2026-10-24T12:00:00Z');
    expect(waited('2026-10-24T11:30:00Z', now)).toBe('30 min');
    expect(waited('2026-10-22T12:00:00Z', now)).toBe('2 days');
  });
  it('turns the engine explanation into chips', () => {
    expect(whyChips('{"sameSector":"true","sharedTerms":"classroom, computer, teacher, labs","bestSentence":"x"}'))
      .toEqual(['Same sector', '“classroom”', '“computer”', '“teacher”']);
    expect(whyChips('{"sameSector":true,"sharedTerms":"books, poor"}')).toEqual(['Same sector', '“books”', '“poor”']);   // as the search service sends it
    expect(whyChips('{"sameSector":false,"sharedTerms":""}')).toEqual([]);
    expect(whyChips('not json')).toEqual([]);
    expect([92, 85, 84, 70, 69, 50, 49].map(matchLabel)).toEqual(['Strong match', 'Strong match', 'Good match', 'Good match', 'Possible match', 'Possible match', 'Weak match']);
  });
});
