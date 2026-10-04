/**
 * Informations de l'établissement — source unique utilisée par toutes les pages,
 * le pied de page et les données structurées (JSON-LD).
 * Pour mettre à jour un horaire, un téléphone ou un lien, c'est ici.
 */

export const business = {
  name: 'La Pointe',
  legalName: 'LA POINTE',
  tagline: 'Bar · Restaurant · Terrasse — Club-house',
  description:
    "Bar, restaurant et terrasse face à l'océan à la Pointe de Penvins, Sarzeau. Vue mer à 270°, cuisine de produits frais, locaux et bio, au cœur de la Presqu'île de Rhuys.",
  phone: '02 97 62 26 84',
  phoneIntl: '+33297622684',
  email: 'hospitalite@lapointe-sarzeau.com',
  address: {
    street: '12 Route de la Chapelle',
    postalCode: '56370',
    city: 'Sarzeau',
    area: "Presqu'île de Rhuys",
    region: 'Bretagne',
    country: 'FR',
  },
  geo: { lat: 47.4954162, lng: -2.6820838 },
  coordinatesLabel: '47°29′43″ N — 2°40′55″ O',
  founded: '2015',
  links: {
    booking: 'https://app.overfull.fr/booking-v2?key_id=ZaM5QdRRRh6&source=Web',
    google: 'https://share.google/KIQtQmyk1aHY7qsbu',
    maps: 'https://www.google.com/maps/search/?api=1&query=La+Pointe+12+Route+de+la+Chapelle+56370+Sarzeau',
    mapsEmbed:
      'https://www.google.com/maps/embed?pb=!1m14!1m8!1m3!1d10782.870056158403!2d-2.6820838!3d47.4954162!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x0%3A0x97f54a97252412aa!2sLa%20Pointe!5e0!3m2!1sfr!2sfr!4v1669886416108!5m2!1sfr!2sfr',
    facebook: 'https://www.facebook.com/restaurantlapointe/',
    instagram: 'https://www.instagram.com/lapointesarzeau/',
  },
  /** Cartes au format PDF, servies depuis /public (mêmes URL que l'ancien site). */
  menus: {
    restaurant: 'resto-hiver.pdf',
    bar: 'bar-menu_huitres-prix-hiver.pdf',
  },
} as const;

export type DayHours = { day: string; schema: string; open: string; close: string };

/** Horaires d'ouverture affichés et publiés dans les données structurées. */
export const openingHours: DayHours[] = [
  { day: 'Lundi', schema: 'Monday', open: '11:00', close: '23:00' },
  { day: 'Mardi', schema: 'Tuesday', open: '11:00', close: '23:00' },
  { day: 'Mercredi', schema: 'Wednesday', open: '11:00', close: '23:00' },
  { day: 'Jeudi', schema: 'Thursday', open: '11:00', close: '23:00' },
  { day: 'Vendredi', schema: 'Friday', open: '11:00', close: '23:00' },
  { day: 'Samedi', schema: 'Saturday', open: '11:00', close: '23:00' },
  { day: 'Dimanche', schema: 'Sunday', open: '11:00', close: '19:00' },
];

export const kitchenHours = [
  { label: 'Service du midi', value: "jusqu'à 14h30" },
  { label: 'Service du soir', value: "jusqu'à 22h00" },
];

export const seasonInfo = [
  'Ouvert 7j/7 de début avril à fin août.',
  'Fermeture annuelle à partir de fin novembre.',
];

export const navigation = [
  { label: 'Le restaurant', href: 'restaurant-sur-la-plage/' },
  { label: 'Le bar', href: 'bar-de-plage/' },
  { label: "L'histoire", href: 'a-propos/' },
  { label: 'Galerie', href: 'galerie-photos/en-photos/' },
  { label: 'Évènements', href: 'nos-actualites/' },
  { label: 'Contact', href: 'contact/' },
] as const;

export const testimonials = [
  {
    quote: "Lieu incroyable, le plus beau spot de la presqu'île. L'équipe est top et on y mange bien.",
    author: 'Rodolphe D.',
  },
  {
    quote:
      'Restaurant idéalement situé, avec une équipe jeune et professionnelle. Nous avons passé un agréable moment malgré une météo capricieuse.',
    author: 'Benjamin B.',
  },
  {
    quote: 'Les plats sont excellents et les serveuses sont très aimables.',
    author: 'Nadine M.',
  },
];
