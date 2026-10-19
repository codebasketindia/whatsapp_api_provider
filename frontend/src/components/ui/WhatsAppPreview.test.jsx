import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import WhatsAppPreview, { renderTemplateText } from './WhatsAppPreview';

describe('renderTemplateText', () => {
  it('fills positional variables and keeps missing ones', () => {
    const parts = renderTemplateText('Hi {{1}}, order {{2}} is ready', ['Ravi']);
    expect(parts.map((p) => p.value).join('')).toBe('Hi Ravi, order {{2}} is ready');
    expect(parts.find((p) => p.type === 'var').value).toBe('{{2}}');
  });
  it('fills named variables', () => {
    const parts = renderTemplateText('Hello {{customer_name}}', { customer_name: 'Asha' });
    expect(parts.map((p) => p.value).join('')).toBe('Hello Asha');
  });
});

describe('WhatsAppPreview', () => {
  it('renders body, footer and buttons', () => {
    render(
      <WhatsAppPreview
        businessName="Acme"
        header={{ format: 'TEXT', text: 'Order update' }}
        body="Hi {{1}}"
        values={['Ravi']}
        footer="Reply STOP to opt out"
        buttons={[{ type: 'URL', text: 'Track order' }, { type: 'QUICK_REPLY', text: 'Talk to us' }]}
      />,
    );
    expect(screen.getByText('Order update')).toBeInTheDocument();
    expect(screen.getByText('Ravi')).toBeInTheDocument();
    expect(screen.getByText('Reply STOP to opt out')).toBeInTheDocument();
    expect(screen.getByText('Track order')).toBeInTheDocument();
    expect(screen.getByText('Acme')).toBeInTheDocument();
  });
});
