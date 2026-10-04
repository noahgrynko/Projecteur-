import type { APIRoute } from 'astro';
import { url, isIndexable } from '../data/url';

// Les préversions (PUBLIC_INDEXABLE=false) ne doivent pas être indexées.
export const GET: APIRoute = ({ site }) => {
  const sitemap = new URL(url('sitemap-index.xml'), site).href;
  const body = isIndexable
    ? `User-agent: *\nAllow: /\n\nSitemap: ${sitemap}\n`
    : `User-agent: *\nDisallow: /\n`;
  return new Response(body, { headers: { 'Content-Type': 'text/plain; charset=utf-8' } });
};
