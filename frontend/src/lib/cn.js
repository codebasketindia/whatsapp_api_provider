import clsx from 'clsx';

/** Joins class names; thin wrapper so components share one helper. */
export function cn(...args) {
  return clsx(...args);
}
