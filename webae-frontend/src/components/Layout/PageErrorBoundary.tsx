import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button, Result } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';

interface PageErrorBoundaryProps {
  children: ReactNode;
  title: string;
  retryLabel: string;
}

interface PageErrorBoundaryState {
  failed: boolean;
}

/** Keeps a single page failure from unmounting the application shell and navigation. */
export class PageErrorBoundary extends Component<
  PageErrorBoundaryProps,
  PageErrorBoundaryState
> {
  state: PageErrorBoundaryState = { failed: false };

  static getDerivedStateFromError(): PageErrorBoundaryState {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('[WebAE] page render failed', error, info.componentStack);
  }

  private retry = () => {
    this.setState({ failed: false });
  };

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <Result
        status="error"
        title={this.props.title}
        extra={
          <Button icon={<ReloadOutlined />} onClick={this.retry}>
            {this.props.retryLabel}
          </Button>
        }
      />
    );
  }
}
