import type { DashboardDto } from './api/types';

/**
 * CSV that opens cleanly in Excel: quoted where needed, CRLF line ends, a byte-order mark so ₹ and Indian names show
 * correctly, and cells starting with = + - @ prefixed with ' so a spreadsheet never runs them as formulas.
 * Same rules as Csv.java on the server.
 */
export function toCsv(rows: (string | number | null | undefined)[][]): string {
  return '\uFEFF' + rows.map((r) => r.map(cell).join(',')).join('\r\n') + '\r\n';
}

export function cell(value: string | number | null | undefined): string {
  if (value == null) return '';
  let v = String(value);
  if (v && '=+-@\t\r'.includes(v[0]!) && !/^-?\d+(\.\d+)?$/.test(v)) v = `'${v}`;
  return /[",\r\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v;
}

/** Saves text as a file in the browser (used for dashboards and the demo build). */
export function downloadText(fileName: string, text: string, type = 'text/csv;charset=utf-8') {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Everything on a dashboard as one table: figures first, then every chart, so it can be pasted into a report. */
export function dashboardRows(d: DashboardDto, heading: string): (string | number)[][] {
  const rows: (string | number)[][] = [[d.title], [heading], [], ['Figure', 'Value', 'Note']];
  d.kpis.forEach((k) => rows.push([k.label, k.value, k.note]));
  d.donuts.forEach((c) => {
    rows.push([], [c.title], ['Group', 'Value']);
    c.slices.forEach((s) => rows.push([s.label, s.value]));
  });
  d.bars.forEach((c) => {
    rows.push([], [`${c.title} (${c.unit})`], ['', ...c.series]);
    c.bars.forEach((b) => rows.push([b.label, ...b.values]));
  });
  return rows;
}
