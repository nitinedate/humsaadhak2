import { describe, expect, it } from 'vitest';
import type { BarChartDto } from '../api/types';
import { barSpans, cardColumns, chartHeight, compactRupees, donutColumns, isHorizontal, nameAxisWidth, shorten, tickText } from './chartLayout';

const chart = (labels: string[], series = ['A']): BarChartDto =>
  ({ title: 't', series, unit: '₹', bars: labels.map((label) => ({ label, values: series.map(() => 1) })) });
const months = chart(['Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct'], ['Individual', 'Corporate']);
const who = chart(['Individual donors', 'Corporate donors'], ['Direct', 'Indirect']);
const state = chart(['Matched and donated', 'Matched, no donation yet', 'Donated without a match', 'Neither matched nor donated'], ['Causes', 'Projects']);
const quarters = chart(['Q1', 'Q2', 'Q3', 'Q4'], ['Individual', 'Corporate']);

describe('dashboard layout', () => {
  it('puts donuts side by side so none is left alone', () => {
    expect([1, 2, 3, 4, 5].map(donutColumns)).toEqual([1, 2, 3, 2, 3]);
  });

  it('runs bars upright for periods and short names, horizontally for long names', () => {
    expect(isHorizontal(months)).toBe(false);
    expect(isHorizontal(who)).toBe(false);            // "Individual donors" fits under a column
    expect(isHorizontal(state)).toBe(true);
    expect(isHorizontal(quarters)).toBe(false);
  });

  it('never leaves an empty space next to a bar chart', () => {
    expect(barSpans([months, who, state])).toEqual(['full', 'half', 'half']);   // manager
    expect(barSpans([quarters, state])).toEqual(['half', 'half']);              // donor, beneficiary
    expect(barSpans([quarters])).toEqual(['full']);                              // a single chart
    expect(barSpans([who, months, quarters])).toEqual(['full', 'full', 'full']);// a lone half before a wide chart takes the row
    expect(barSpans([who, quarters, state])).toEqual(['half', 'half', 'full']);
  });

  it('labels rupee axes in Indian units', () => {
    expect(compactRupees(850)).toBe('₹850');
    expect(compactRupees(45000)).toBe('₹45k');
    expect(compactRupees(450000)).toBe('₹4.5 L');
    expect(compactRupees(1800000)).toBe('₹18 L');
    expect(compactRupees(12000000)).toBe('₹1.2 Cr');
    expect(tickText('₹ lakh', 6)).toBe('₹6 L');
    expect(tickText('count', 30)).toBe('30');
  });

  it('sizes charts to their content', () => {
    expect(chartHeight(months)).toBe(296);
    expect(chartHeight(state)).toBe(252);
    expect(nameAxisWidth(state)).toBe(178);           // 27 characters
    expect(shorten('Digital literacy lab for adolescent girls', 24)).toBe('Digital literacy lab fo…');
    expect(shorten('Library on wheels', 24)).toBe('Library on wheels');
  });
});

describe('card columns (Bootstrap rows of headline figures and export cards)', () => {
  it('never leaves a card alone on a row', () => {
    expect(cardColumns(4)).toBe('row-cols-1 row-cols-sm-2 row-cols-xl-4');   // 1, then 2 + 2, then 4
    expect(cardColumns(3)).toBe('row-cols-1 row-cols-md-3');                  // 1, then 3 (never 2 + 1)
    expect(cardColumns(2)).toBe('row-cols-1 row-cols-sm-2');
    expect(cardColumns(1)).toBe('row-cols-1');
  });
});

