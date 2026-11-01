/**
 * Excel and PDF files for the beneficiary reports, in the original project's layout: logo, report title,
 * "Entity Name - … [ID: …]", the filter line, then the table. Built in the browser from the API's rows; the libraries
 * load only when someone exports.
 */
import type { ReportColumn, ReportResult } from './api/types';
import { inr } from './format';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** "2025-06-10" -> "10-Jun-2025" (the original's date style). */
export function dayMonthYear(iso: string): string {
  const [y, m, d] = iso.slice(0, 10).split('-');
  return `${d}-${MONTHS[Number(m) - 1]}-${y}`;
}

/** How a value is shown on screen and in PDFs. PDFs use "INR" because their standard fonts have no ₹ sign. */
export function formatValue(value: string | number | null | undefined, type: ReportColumn['type'], pdf = false): string {
  if (value == null || value === '') return '—';
  if (type === 'money') return pdf ? `INR ${inr(Number(value)).slice(1)}` : inr(Number(value));
  if (type === 'date') return dayMonthYear(String(value));
  return String(value);
}

export function exportFileName(r: ReportResult, ext: 'xlsx' | 'pdf', today = new Date()): string {
  return `${r.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')}-${today.toISOString().slice(0, 10)}.${ext}`;
}

/** Indian grouping for Excel: ₹1,00,00,000 / ₹10,00,000 / ₹10,000. */
export const EXCEL_RUPEES = '[>=10000000]"₹"##\\,##\\,##\\,##0;[>=100000]"₹"##\\,##\\,##0;"₹"##,##0';

export async function buildExcel(r: ReportResult, logoPng?: ArrayBuffer | null): Promise<ArrayBuffer> {
  const { default: ExcelJS } = await import('exceljs');
  const wb = new ExcelJS.Workbook();
  wb.creator = 'HumSaadhak';
  wb.created = new Date();
  const ws = wb.addWorksheet(r.title.slice(0, 31).replace(/[\\/?*[\]:]/g, ' '));
  const width = Math.max(r.columns.length, 1);
  let row = 1;
  if (logoPng) {
    const img = wb.addImage({ buffer: logoPng as unknown as never, extension: 'png' });
    ws.addImage(img, { tl: { col: 0, row: 0 }, ext: { width: 196, height: 46 } });
    ws.getRow(1).height = 22; ws.getRow(2).height = 22;
    row = 3;
  }
  const line = (text: string, font: Partial<import('exceljs').Font>) => {
    ws.mergeCells(row, 1, row, width);
    const c = ws.getCell(row, 1);
    c.value = text;
    c.font = font;
    row++;
  };
  line(r.title, { bold: true, size: 14 });
  line(r.entityLine, { size: 11 });
  line(r.filterLine, { italic: true, size: 10, color: { argb: 'FF555555' } });
  row++;
  const headerRow = row;
  ws.getRow(headerRow).values = r.columns.map((c) => c.label);
  ws.getRow(headerRow).eachCell((c) => {
    c.font = { bold: true };
    c.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFEEF0F6' } };
    c.border = { bottom: { style: 'thin', color: { argb: 'FFB0B5C5' } } };
  });
  r.rows.forEach((data, i) => {
    const excelRow = ws.getRow(headerRow + 1 + i);
    r.columns.forEach((col, j) => {
      const v = data[col.key];
      const cell = excelRow.getCell(j + 1);
      if (v == null || v === '') cell.value = null;
      else if (col.type === 'money') { cell.value = Number(v); cell.numFmt = EXCEL_RUPEES; }
      else if (col.type === 'date') { cell.value = new Date(`${String(v).slice(0, 10)}T00:00:00Z`); cell.numFmt = 'dd-mmm-yyyy'; }
      else cell.value = String(v);
    });
  });
  r.columns.forEach((col, j) => {
    const longest = Math.max(col.label.length, ...r.rows.map((d) => formatValue(d[col.key], col.type).length));
    ws.getColumn(j + 1).width = Math.min(Math.max(longest + 2, 10), 48);
  });
  ws.views = [{ state: 'frozen', ySplit: headerRow }];
  return wb.xlsx.writeBuffer() as Promise<ArrayBuffer>;
}

export async function buildPdf(r: ReportResult, logoDataUrl?: string | null, today = new Date()): Promise<ArrayBuffer> {
  const { jsPDF } = await import('jspdf');
  const { autoTable } = await import('jspdf-autotable');
  const doc = new jsPDF({ orientation: r.columns.length > 6 ? 'landscape' : 'portrait', unit: 'pt', format: 'a4' });
  let y = 36;
  if (logoDataUrl) { doc.addImage(logoDataUrl, 'PNG', 36, y, 120, 28); y += 44; }
  doc.setFont('helvetica', 'bold').setFontSize(15).text(r.title, 36, y); y += 18;
  doc.setFont('helvetica', 'normal').setFontSize(10).text(r.entityLine, 36, y); y += 14;
  doc.setTextColor(90).text(r.filterLine, 36, y); y += 10;
  doc.setTextColor(0);
  const generated = `Generated on ${dayMonthYear(today.toISOString())}`;
  autoTable(doc, {
    startY: y + 6,
    head: [r.columns.map((c) => c.label)],
    body: r.rows.map((d) => r.columns.map((c) => formatValue(d[c.key], c.type, true))),
    styles: { fontSize: 8, cellPadding: 4 },
    headStyles: { fillColor: [232, 233, 251], textColor: [30, 35, 64] },
    columnStyles: Object.fromEntries(r.columns.map((c, i) => [i, { halign: c.type === 'money' ? 'right' : 'left' }])),
    margin: { left: 36, right: 36 },
    didDrawPage: () => {
      const page = doc.getNumberOfPages();
      const h = doc.internal.pageSize.getHeight();
      doc.setFontSize(8).setTextColor(120).text(`${generated} · Page ${page}`, 36, h - 18);
      doc.setTextColor(0);
    },
  });
  return doc.output('arraybuffer');
}

/** Loads the logo for exports; exports still work (without the logo) if it cannot be read. */
export async function loadLogo(url: string): Promise<{ png: ArrayBuffer; dataUrl: string } | null> {
  try {
    const res = await fetch(url);
    if (!res.ok) return null;
    const png = await res.arrayBuffer();
    const bytes = new Uint8Array(png);
    let binary = '';
    bytes.forEach((b) => { binary += String.fromCharCode(b); });
    return { png, dataUrl: `data:image/png;base64,${btoa(binary)}` };
  } catch {
    return null;
  }
}
