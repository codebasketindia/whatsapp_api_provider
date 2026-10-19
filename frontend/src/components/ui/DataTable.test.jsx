import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import DataTable from './DataTable';

const page = { content: [{ id: 1, name: 'Acme', status: 'ACTIVE' }], page: 0, size: 20, totalElements: 1, totalPages: 1 };
const columns = [
  { key: 'name', header: 'Name', primary: true },
  { key: 'status', header: 'Status' },
];

describe('DataTable', () => {
  it('renders rows as cards on phones and handles row clicks', () => {
    const onRowClick = vi.fn();
    render(<DataTable columns={columns} page={page} onRowClick={onRowClick} onPageChange={() => {}} />);
    fireEvent.click(screen.getByText('Acme'));
    expect(onRowClick).toHaveBeenCalledWith(page.content[0]);
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
  });
  it('shows the empty state', () => {
    render(<DataTable columns={columns} rows={[]} empty={<p>No partners</p>} />);
    expect(screen.getByText('No partners')).toBeInTheDocument();
  });
});
