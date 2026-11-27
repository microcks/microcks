export type Theme = 'dark' | 'light';

export const THEME_STORAGE_KEY = 'microcks-theme';
export const THEME_EVENT = 'microcks-theme-change';

/** Inline script for `app.html`: applies the saved theme before first paint (dark by default). */
export const THEME_INIT_SCRIPT = `try{var t=localStorage.getItem('${THEME_STORAGE_KEY}');document.documentElement.dataset.theme=t==='light'?'light':'dark'}catch(e){document.documentElement.dataset.theme='dark'}`;

export function getTheme(): Theme {
  if (typeof document === 'undefined') return 'dark';
  return document.documentElement.dataset.theme === 'light' ? 'light' : 'dark';
}

export function setTheme(theme: Theme, persist = true): void {
  if (typeof document === 'undefined') return;
  document.documentElement.dataset.theme = theme;
  if (persist) {
    try {
      localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      // storage unavailable: the theme only lasts for the page
    }
  }
  document.dispatchEvent(new CustomEvent<Theme>(THEME_EVENT, { detail: theme }));
}
