import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('renders the search workspace shell', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: /nebullama search/i })).toBeInTheDocument();
    expect(screen.getByRole('search')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: /search results/i })).toBeInTheDocument();
  });
});
