import { describe, expect, it } from 'vitest';
import { cell, dashboardRows, toCsv } from './csv';

describe('CSV', () => {
  it('quotes where needed and starts with a byte-order mark', () => {
    expect(toCsv([['Name', 'Amount'], ['Asha Foundation, CSR', 450000], ['Say "hi"', null]]))
      .toBe('\uFEFFName,Amount\r\n"Asha Foundation, CSR",450000\r\n"Say ""hi""",\r\n');
  });
  it('never lets a spreadsheet run a formula', () => {
    expect(cell('=SUM(A1)')).toBe("'=SUM(A1)");
    expect(cell('+91 98200 00000')).toBe("'+91 98200 00000");
    expect(cell('-5000')).toBe('-5000');
    expect(cell(-5000)).toBe('-5000');
  });
  it('flattens a dashboard into figures and charts', () => {
    const rows = dashboardRows({
      role: 'MANAGER', title: 'Manager dashboard',
      kpis: [{ label: 'Donations', value: '61', note: '₹41.6 L' }],
      donuts: [{ title: 'Organisations by type', totalLabel: 'organisations', slices: [{ label: 'Individual donors', value: 42, colorKey: 'rust' }] }],
      bars: [{ title: 'Who gave to whom', series: ['To direct', 'To indirect'], unit: '₹', bars: [{ label: 'Corporate donors', values: [10, 20] }] }],
    }, 'Manager · Financial year 2026–27');
    expect(rows).toEqual([
      ['Manager dashboard'], ['Manager · Financial year 2026–27'], [], ['Figure', 'Value', 'Note'], ['Donations', '61', '₹41.6 L'],
      [], ['Organisations by type'], ['Group', 'Value'], ['Individual donors', 42],
      [], ['Who gave to whom (₹)'], ['', 'To direct', 'To indirect'], ['Corporate donors', 10, 20],
    ]);
  });
});
