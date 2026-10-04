# La Pointe — Penvins, Sarzeau

Site officiel de **La Pointe**, bar, restaurant & terrasse face à l'océan à la Pointe de Penvins (Sarzeau,
Presqu'île de Rhuys). Refonte complète de [lapointe-penvins.com](https://www.lapointe-penvins.com/).

- **Technologie** : [Astro 7](https://astro.build) — site 100 % statique, zéro framework JavaScript côté client
- **Images** : pipeline Astro/sharp — AVIF + WebP, tailles responsives, lazy loading
- **Hébergement** : GitHub Pages, déployé automatiquement par GitHub Actions à chaque push sur `main`

## Démarrer en local

Prérequis : Node.js 22 (voir `.nvmrc`).

```bash
npm install
npm run dev       # http://localhost:4321
npm run build     # vérification des types + build de production dans dist/
npm run preview   # sert le build de production
```

Le premier build encode toutes les photos en AVIF/WebP (~2 min) ; les builds suivants réutilisent le cache
(`node_modules/.astro`).

## Structure

```
src/
├── assets/
│   ├── fonts/          Instrument Serif + Hanken Grotesk (auto-hébergées, licence OFL)
│   └── photos/         Photos sources (optimisées automatiquement au build)
├── components/         Header, Footer, Photo, Hours, MapEmbed, CtaBand, PageHero…
├── data/
│   ├── site.ts         ⚑ Coordonnées, horaires, liens, avis — à modifier ici
│   └── url.ts          Gestion du sous-chemin (préversion GitHub Pages)
├── layouts/            BaseLayout (SEO, Open Graph, JSON-LD), LegalLayout
├── pages/              Une page = une URL (mêmes URL que l'ancien site)
└── styles/global.css   Système de design (couleurs, typographie, boutons)
public/                 Favicons, manifest, cartes PDF (mêmes URL que l'ancien site)
```

## Mettre à jour le contenu

| Je veux…                         | Fichier                                                          |
| -------------------------------- | ---------------------------------------------------------------- |
| changer les horaires / la saison | `src/data/site.ts` (`openingHours`, `kitchenHours`, `seasonInfo`) |
| changer téléphone, email, liens  | `src/data/site.ts` (`business`)                                  |
| remplacer la carte du restaurant | remplacer `public/resto-hiver.pdf` (ou changer `business.menus`) |
| ajouter une photo à la galerie   | déposer le `.jpg` dans `src/assets/photos/` puis l'ajouter à la liste `items` de `src/pages/galerie-photos/en-photos/index.astro` |
| modifier les avis clients        | `src/data/site.ts` (`testimonials`)                              |

Les horaires sont réutilisés partout : pages, pied de page et données structurées Google (`Restaurant`).

## Déploiement

Le workflow `.github/workflows/deploy.yml` construit et publie le site sur GitHub Pages à chaque push sur `main`
(et vérifie le build sur chaque pull request).

**Mise en place initiale (une seule fois)** : *Settings → Pages → Build and deployment → Source :
GitHub Actions*.

### Passage sur le domaine `www.lapointe-penvins.com`

1. *Settings → Pages → Custom domain* : `www.lapointe-penvins.com`, puis cocher *Enforce HTTPS*.
2. Chez le registraire DNS : enregistrement `CNAME www → <utilisateur>.github.io` (et redirection de l'apex).
3. *Settings → Secrets and variables → Actions → Variables* : créer `PUBLIC_INDEXABLE = true`, puis relancer le
   workflow.

Tant que `PUBLIC_INDEXABLE` n'est pas à `true`, la préversion est servie en `noindex` avec un `robots.txt`
bloquant, pour ne pas concurrencer le site actuel dans Google.

Toutes les URL de l'ancien site sont conservées (`/restaurant-sur-la-plage/`, `/bar-de-plage/`, `/a-propos/`,
`/galerie-photos/en-photos/`, `/nos-actualites/`, `/contact/`, `/mentions/`, `/rgpd/`, `/plan-site/`, ainsi que
les PDF des cartes) : le référencement existant est préservé. Les balises de vérification Google Search Console
sont reprises.

## SEO, performance & accessibilité

- `title`, meta description, canonical, Open Graph / Twitter Card (image 1200×630 générée) sur chaque page
- Données structurées JSON-LD `Restaurant` + `BarOrPub` (adresse, coordonnées GPS, horaires, réservation, menu)
  et `BreadcrumbList`
- `sitemap-index.xml` et `robots.txt` générés au build, méta géographiques pour le SEO local
- Lighthouse (mobile) : Accessibilité 100 · Bonnes pratiques 100 · SEO 100 · Performance 91–100
- Aucun cookie, aucun traceur : la carte Google Maps n'est chargée qu'au clic, le formulaire de contact ouvre la
  messagerie (aucune donnée stockée)
- Navigation clavier complète (lien d'évitement, menu mobile avec piège de focus, visionneuse `<dialog>`),
  contrastes AA, `prefers-reduced-motion` respecté

## Droits

Tous droits réservés. Les textes, la marque et les **photographies** appartiennent à La Pointe et à leurs auteurs :
toute reproduction est interdite sans autorisation. Les polices Instrument Serif et Hanken Grotesk sont distribuées
sous licence SIL Open Font License.
