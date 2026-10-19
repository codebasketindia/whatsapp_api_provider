import { Component } from 'react';
import ErrorPage from './ErrorPage';

/** Catches render errors (including failed lazy chunk loads) and shows the 500 page. */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    // Logged for debugging in the browser console only; nothing sensitive is rendered.
    console.error('UI error', error, info?.componentStack);
  }

  render() {
    if (this.state.error) {
      const chunkFailed = /Loading chunk|dynamically imported module|Failed to fetch/i.test(String(this.state.error?.message));
      return (
        <ErrorPage
          code={500}
          onReset={() => {
            if (chunkFailed) window.location.reload();
            else this.setState({ error: null });
          }}
        />
      );
    }
    return this.props.children;
  }
}
