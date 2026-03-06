import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const errorRate = new Rate('errors');
const pagingDuration = new Trend('paging_duration', true);

export const options = {
  scenarios: {
    // Smoke test : vérifier que tout fonctionne
    smoke: {
      executor: 'constant-vus',
      vus: 1,
      duration: '10s',
      exec: 'smokeTest',
    },
    // Load test : montée en charge progressive
    load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 10 },
        { duration: '20s', target: 10 },
        { duration: '10s', target: 0 },
      ],
      exec: 'loadTest',
      startTime: '15s',
    },
    // Stress test : pics de charge
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s', target: 20 },
        { duration: '10s', target: 20 },
        { duration: '5s', target: 50 },
        { duration: '10s', target: 50 },
        { duration: '5s', target: 0 },
      ],
      exec: 'loadTest',
      startTime: '60s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    errors: ['rate<0.01'],
    paging_duration: ['p(95)<300'],
  },
};

// --- Helpers ---

function getProducts(params) {
  const query = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
    .join('&');
  const url = `${BASE_URL}/api/products${query ? '?' + query : ''}`;
  return http.get(url, { headers: { Accept: 'application/json' } });
}

function checkPageResponse(res, expectedStatus = 200) {
  const checks = {
    [`status is ${expectedStatus}`]: (r) => r.status === expectedStatus,
  };

  if (expectedStatus === 200) {
    checks['has content array'] = (r) => Array.isArray(JSON.parse(r.body).content);
    checks['has totalElements'] = (r) => JSON.parse(r.body).totalElements !== undefined;
    checks['has totalPages'] = (r) => JSON.parse(r.body).totalPages !== undefined;
    checks['content size <= requested size'] = (r) => {
      const body = JSON.parse(r.body);
      return body.content.length <= body.size;
    };
  }

  const ok = check(res, checks);
  errorRate.add(!ok);
  pagingDuration.add(res.timings.duration);
  return ok;
}

// --- Scénarios ---

export function smokeTest() {
  group('Paging basique', () => {
    const res = getProducts({ page: 0, size: 3, sort: 'name,asc' });
    checkPageResponse(res);
    const body = JSON.parse(res.body);
    check(body, {
      'page 0 is first': (b) => b.first === true,
      'totalElements is 10': (b) => b.totalElements === 10,
      'content has 3 items': (b) => b.content.length === 3,
      'sorted by name asc': (b) => b.content[0].name <= b.content[1].name,
    });
  });

  group('Navigation pages', () => {
    // Page 0
    const p0 = getProducts({ page: 0, size: 3, sort: 'id,asc' });
    checkPageResponse(p0);
    const page0 = JSON.parse(p0.body);
    check(page0, {
      'page0 first=true': (b) => b.first === true,
      'page0 last=false': (b) => b.last === false,
    });

    // Page 1
    const p1 = getProducts({ page: 1, size: 3, sort: 'id,asc' });
    checkPageResponse(p1);
    const page1 = JSON.parse(p1.body);
    check(page1, {
      'page1 first=false': (b) => b.first === false,
      'page1 no overlap with page0': (b) =>
        !b.content.some((item) => page0.content.some((p0item) => p0item.id === item.id)),
    });

    // Dernière page
    const pLast = getProducts({ page: 3, size: 3, sort: 'id,asc' });
    checkPageResponse(pLast);
    check(JSON.parse(pLast.body), {
      'last page last=true': (b) => b.last === true,
      'last page has 1 item': (b) => b.content.length === 1,
    });

    // Page au-delà
    const pBeyond = getProducts({ page: 99, size: 3, sort: 'id,asc' });
    checkPageResponse(pBeyond);
    check(JSON.parse(pBeyond.body), {
      'beyond page is empty': (b) => b.content.length === 0,
    });
  });

  group('Filtre RSQL', () => {
    // Filtre simple
    const res = getProducts({ filter: 'price>100', size: 50, sort: 'price,desc' });
    checkPageResponse(res);
    const body = JSON.parse(res.body);
    check(body, {
      'all prices > 100': (b) => b.content.every((p) => p.price > 100),
      'sorted desc': (b) =>
        b.content.length < 2 || b.content.every((p, i) => i === 0 || p.price <= b.content[i - 1].price),
    });

    // Filtre par nom
    const res2 = getProducts({ filter: 'name==Laptop', size: 10 });
    checkPageResponse(res2);
    check(JSON.parse(res2.body), {
      'found Laptop': (b) => b.totalElements === 1 && b.content[0].name === 'Laptop',
    });

    // Filtre sur relation
    const res3 = getProducts({ filter: 'category.name==Electronics', size: 50, sort: 'name,asc' });
    checkPageResponse(res3);
    check(JSON.parse(res3.body), {
      'all electronics': (b) => b.content.every((p) => p.category.name === 'Electronics'),
      'electronics count is 5': (b) => b.totalElements === 5,
    });

    // AND
    const res4 = getProducts({ filter: 'category.name==Electronics;price>500', size: 50 });
    checkPageResponse(res4);
    check(JSON.parse(res4.body), {
      'electronics > 500': (b) => b.content.every((p) => p.category.name === 'Electronics' && p.price > 500),
    });
  });

  group('Tri', () => {
    // Tri par prix ascendant
    const res = getProducts({ size: 50, sort: 'price,asc' });
    checkPageResponse(res);
    check(JSON.parse(res.body), {
      'sorted by price asc': (b) =>
        b.content.every((p, i) => i === 0 || p.price >= b.content[i - 1].price),
    });

    // Tri par prix descendant
    const res2 = getProducts({ size: 50, sort: 'price,desc' });
    checkPageResponse(res2);
    check(JSON.parse(res2.body), {
      'sorted by price desc': (b) =>
        b.content.every((p, i) => i === 0 || p.price <= b.content[i - 1].price),
    });

    // Sans tri — fallback sur id (déterministe)
    const res3 = getProducts({ size: 50 });
    checkPageResponse(res3);
    check(JSON.parse(res3.body), {
      'fallback sort by id asc': (b) =>
        b.content.every((p, i) => i === 0 || p.id > b.content[i - 1].id),
    });
  });

  group('Erreurs — 400', () => {
    // RSQL invalide
    const res1 = getProducts({ filter: 'invalid_rsql' });
    checkPageResponse(res1, 400);

    // Sort invalide
    const res2 = getProducts({ sort: 'nonExistent,asc' });
    checkPageResponse(res2, 400);

    // Page négative
    const res3 = getProducts({ page: -1, size: 10 });
    checkPageResponse(res3, 400);

    // Size = 0
    const res4 = getProducts({ size: 0 });
    checkPageResponse(res4, 400);
  });

  sleep(0.5);
}

export function loadTest() {
  const filters = [
    '',
    'price>50',
    'price<100',
    'category.name==Electronics',
    'category.name==Books',
    'name==Laptop',
    'price>100;price<1000',
    'category.name==Clothing;price<100',
  ];

  const sorts = ['name,asc', 'price,desc', 'id,asc', 'price,asc', 'name,desc'];
  const sizes = [3, 5, 10, 20];

  const filter = filters[Math.floor(Math.random() * filters.length)];
  const sort = sorts[Math.floor(Math.random() * sorts.length)];
  const size = sizes[Math.floor(Math.random() * sizes.length)];
  const page = Math.floor(Math.random() * 4);

  const res = getProducts({ filter, sort, size, page });
  checkPageResponse(res);

  sleep(0.1 + Math.random() * 0.3);
}
