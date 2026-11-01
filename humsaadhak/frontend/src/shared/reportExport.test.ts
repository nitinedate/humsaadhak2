import { describe, expect, it } from 'vitest';
import type { ReportResult } from './api/types';
import { buildExcel, buildPdf, dayMonthYear, exportFileName, formatValue } from './reportExport';

const report: ReportResult = {
  title: 'Donation Report', entityLine: 'Entity Name - Pragati Trust [ID: IB100031]', filterLine: 'Financial year - 2025-2026',
  columns: [{ key: 'donorName', label: 'Donor Name', type: 'text' }, { key: 'donationAmount', label: 'Donation Amount', type: 'money' },
    { key: 'donationDate', label: 'Donation Date', type: 'date' }],
  rows: [{ donorName: 'Asha Foundation CSR', donationAmount: 1240000, donationDate: '2025-07-01' },
    { donorName: 'Neha Joshi', donationAmount: 50000, donationDate: null }],
  total: 2, page: 0, size: 10000,
};

describe('report exports', () => {
  it('formats values like the original reports', () => {
    expect(dayMonthYear('2025-06-10')).toBe('10-Jun-2025');
    expect(formatValue(1240000, 'money')).toBe('₹12,40,000');
    expect(formatValue(1240000, 'money', true)).toBe('INR 12,40,000');
    expect(formatValue(null, 'text')).toBe('—');
    expect(exportFileName(report, 'xlsx', new Date('2026-10-24T10:00:00Z'))).toBe('donation-report-2026-10-24.xlsx');
  });

  it('writes Excel with the title, entity and filter lines above a header row, and real numbers and dates', async () => {
    const { default: ExcelJS } = await import('exceljs');
    const wb = new ExcelJS.Workbook();
    await wb.xlsx.load(await buildExcel(report));
    const ws = wb.worksheets[0]!;
    expect(ws.getCell('A1').value).toBe('Donation Report');
    expect(ws.getCell('A2').value).toBe('Entity Name - Pragati Trust [ID: IB100031]');
    expect(ws.getCell('A3').value).toBe('Financial year - 2025-2026');
    expect(ws.getRow(5).values).toEqual([undefined, 'Donor Name', 'Donation Amount', 'Donation Date']);
    expect(ws.getCell('B6').value).toBe(1240000);
    expect(ws.getCell('B6').numFmt).toContain('"₹"');
    expect((ws.getCell('C6').value as Date).toISOString().slice(0, 10)).toBe('2025-07-01');
    expect(ws.getCell('C7').value).toBeNull();
  });

  it('writes a PDF', async () => {
    const pdf = new TextDecoder().decode((await buildPdf(report, null, new Date('2026-10-24T10:00:00Z'))).slice(0, 5));
    expect(pdf).toBe('%PDF-');
  });
});
