interface ImportMetaEnv {
  /** « false » sur les préversions : ajoute noindex et bloque robots.txt. */
  readonly PUBLIC_INDEXABLE?: string;
}
interface ImportMeta {
  readonly env: ImportMetaEnv;
}
