import type { IconName } from '@microcks/deep-ink';

export const STATUS_ICON = { pass: 'check-circle', fail: 'x-circle', off: 'minus-circle', pending: 'pending-circle' } as const satisfies Record<
  string,
  IconName
>;

export const RUN_WORD = { SUCCESS: 'Passed', FAILURE: 'Failed', RUNNING: 'Running' } as const;
