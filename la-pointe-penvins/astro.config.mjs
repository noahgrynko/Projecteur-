// @ts-check
import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';

// SITE_URL / BASE_PATH permettent de déployer la même base de code sur le
// domaine de production (https://www.lapointe-penvins.com) ou sur une
// préversion GitHub Pages (https://<user>.github.io/la-pointe-penvins).
const site = process.env.SITE_URL || 'https://www.lapointe-penvins.com';
const base = process.env.BASE_PATH || '/';

export default defineConfig({
  site,
  base,
  trailingSlash: 'always',
  build: { format: 'directory', inlineStylesheets: 'auto' },
  integrations: [sitemap({ filter: (page) => !page.includes('/404') })],
});
