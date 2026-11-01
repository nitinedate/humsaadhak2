// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { checkFile, describeTypes, FilePicker, formatBytes } from './FilePicker';

afterEach(cleanup);

const ACCEPT = ['.pdf', '.jpg', '.jpeg', '.png', '.mp4'];
const MB = 1024 * 1024;
const pdf = (name = 'certificate.pdf', size?: number) => {
  const f = new File(['%PDF-1.7'], name, { type: 'application/pdf' });
  if (size !== undefined) Object.defineProperty(f, 'size', { value: size });
  return f;
};

function Harness({ onChange = () => {} }: { onChange?: (f: File | null) => void }) {
  const [file, setFile] = useState<File | null>(null);
  return <FilePicker id="f" label="File" accept={ACCEPT} maxBytes={20 * MB} file={file} onChange={(f) => { setFile(f); onChange(f); }} />;
}

describe('file rules', () => {
  it('formats sizes and type lists for people', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1536)).toBe('2 KB');
    expect(formatBytes(5.4 * MB)).toBe('5.4 MB');
    expect(describeTypes(ACCEPT)).toBe('PDF, JPG, PNG or MP4');
    expect(describeTypes(['.xlsx'])).toBe('XLSX');
  });
  it('accepts allowed files and explains rejections', () => {
    expect(checkFile(pdf(), ACCEPT, 20 * MB)).toBeNull();
    expect(checkFile(pdf('Scan.PDF'), ACCEPT, 20 * MB)).toBeNull();
    expect(checkFile(new File(['x'], 'notes.docx'), ACCEPT, 20 * MB)).toBe('“notes.docx” can’t be uploaded here. Use a PDF, JPG, PNG or MP4 file.');
    expect(checkFile(pdf('big.pdf', 25 * MB), ACCEPT, 20 * MB)).toBe('“big.pdf” is 25.0 MB; the limit is 20.0 MB.');
    expect(checkFile(pdf('empty.pdf', 0), ACCEPT, 20 * MB)).toBe('“empty.pdf” is empty.');
  });
});

describe('FilePicker', () => {
  it('shows a Browse files button and the allowed types before a file is chosen', () => {
    render(<Harness />);
    expect(screen.getByRole('button', { name: 'Browse files' })).toBeTruthy();
    expect(screen.getByText('PDF, JPG, PNG or MP4, up to 20.0 MB')).toBeTruthy();
  });

  it('Browse files opens the system file chooser', () => {
    render(<Harness />);
    const input = screen.getByLabelText('File') as HTMLInputElement;
    const click = vi.spyOn(input, 'click');
    fireEvent.click(screen.getByRole('button', { name: 'Browse files' }));
    expect(click).toHaveBeenCalledTimes(1);
  });

  it('shows the chosen file with its size, and Remove clears it', () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    fireEvent.change(screen.getByLabelText('File'), { target: { files: [pdf('80g-2026.pdf', 2 * MB)] } });
    expect(screen.getByText('80g-2026.pdf')).toBeTruthy();
    expect(screen.getByText('2.0 MB · ready to upload')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Replace' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Remove 80g-2026.pdf' }));
    expect(onChange).toHaveBeenLastCalledWith(null);
    expect(screen.getByRole('button', { name: 'Browse files' })).toBeTruthy();
  });

  it('accepts a dropped file and highlights the area while dragging', () => {
    const { container } = render(<Harness />);
    const zone = container.querySelector('.dropzone')!;
    fireEvent.dragEnter(zone, { dataTransfer: { files: [] } });
    expect(zone.className).toContain('over');
    expect(screen.getByText('Drop the file to add it')).toBeTruthy();
    fireEvent.drop(zone, { dataTransfer: { files: [pdf('dropped.pdf')] } });
    expect(screen.getByText('dropped.pdf')).toBeTruthy();
  });

  it('refuses a wrong or oversized file with a clear message and keeps nothing', () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    fireEvent.change(screen.getByLabelText('File'), { target: { files: [pdf('huge.pdf', 30 * MB)] } });
    expect(screen.getByRole('alert').textContent).toBe('“huge.pdf” is 30.0 MB; the limit is 20.0 MB.');
    expect(onChange).toHaveBeenLastCalledWith(null);
    expect(screen.getByRole('button', { name: 'Browse files' })).toBeTruthy();
  });
});
