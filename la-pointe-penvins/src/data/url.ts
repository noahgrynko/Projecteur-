/** Construit une URL interne en tenant compte du `base` (préversion GitHub Pages). */
export function url(path = ''): string {
  const base = import.meta.env.BASE_URL.replace(/\/?$/, '/');
  return base + path.replace(/^\//, '');
}

export const isIndexable = import.meta.env.PUBLIC_INDEXABLE !== 'false';
