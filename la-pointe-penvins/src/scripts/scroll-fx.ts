/**
 * Effets de défilement partagés, sans dépendance :
 *  - [data-reveal]          apparition à l'entrée dans l'écran ;
 *  - [data-parallax="0.15"] translation verticale proportionnelle à la position ;
 *  - [data-drift="-0.2"]    translation horizontale (bandeaux de photos).
 * Une seule boucle requestAnimationFrame, active uniquement pendant le défilement,
 * et désactivée si l'utilisateur préfère réduire les animations.
 */

const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;

function initReveal() {
  const items = document.querySelectorAll<HTMLElement>('[data-reveal]');
  if (reduceMotion || !('IntersectionObserver' in window)) {
    items.forEach((el) => el.classList.add('is-visible'));
    return;
  }
  const io = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          io.unobserve(entry.target);
        }
      }
    },
    { rootMargin: '0px 0px -8% 0px', threshold: 0.08 },
  );
  items.forEach((el) => io.observe(el));
}

type Fx = { el: HTMLElement; speed: number; axis: 'x' | 'y'; active: boolean };

function initParallax() {
  if (reduceMotion) return;
  const fx: Fx[] = [
    ...[...document.querySelectorAll<HTMLElement>('[data-parallax]')].map((el) => ({
      el,
      speed: Number(el.dataset.parallax) || 0.12,
      axis: 'y' as const,
      active: false,
    })),
    ...[...document.querySelectorAll<HTMLElement>('[data-drift]')].map((el) => ({
      el,
      speed: Number(el.dataset.drift) || 0.2,
      axis: 'x' as const,
      active: false,
    })),
  ];
  if (!fx.length) return;

  const byEl = new Map(fx.map((f) => [f.el, f]));
  const io = new IntersectionObserver(
    (entries) => entries.forEach((e) => (byEl.get(e.target as HTMLElement)!.active = e.isIntersecting)),
    { rootMargin: '20% 0px' },
  );
  fx.forEach((f) => io.observe(f.el));

  let ticking = false;
  const update = () => {
    const vh = window.innerHeight;
    for (const f of fx) {
      if (!f.active) continue;
      const host = f.el.parentElement ?? f.el;
      const r = host.getBoundingClientRect();
      // -1 quand l'élément entre par le bas, +1 quand il sort par le haut
      const p = (vh / 2 - (r.top + r.height / 2)) / ((vh + r.height) / 2);
      const v = p * f.speed * (f.axis === 'y' ? r.height : window.innerWidth);
      f.el.style.transform = f.axis === 'y' ? `translate3d(0, ${v.toFixed(1)}px, 0)` : `translate3d(${v.toFixed(1)}px, 0, 0)`;
    }
    ticking = false;
  };
  const onScroll = () => {
    if (!ticking) {
      ticking = true;
      requestAnimationFrame(update);
    }
  };
  window.addEventListener('scroll', onScroll, { passive: true });
  window.addEventListener('resize', onScroll, { passive: true });
  update();
}

initReveal();
initParallax();
